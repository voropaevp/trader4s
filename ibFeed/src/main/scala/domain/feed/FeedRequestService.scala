package domain.feed

import fs2.Stream
import cats.effect.{Async, Clock, Ref, Resource, Sync, Temporal}
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all._
import db.ConnectedDao._
import domain.feed.FeedException._
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import model.datastax.ib.feed.ast.RequestState
import model.datastax.ib.feed.request.{Request, RequestContract, RequestData}
import model.datastax.ib.feed.response.Response
import model.datastax.ib.feed.response.contract.Contract
import model.datastax.ib.feed.response.data.Bar
import com.ib.client.{Bar => IbBar, ContractDetails => IbContractDetails}
import utils.config.Config

import scala.collection.immutable.SortedMap

class FeedRequestService[F[_]: Async: Temporal: Clock: Logger: BarDaoConnected: RequestDaoConnected: ContractDaoConnected](
  queuedReq: Ref[F, SortedMap[Int, Queue[F, Option[Either[FeedException, AnyRef]]]]],
  limits: Limits[F],
  startedReceive: Ref[F, Set[Int]],
  dispatcher: Dispatcher[F],
  ibConnectionTracker: IbConnectionTracker[F]
) {

  private def nextId(request: Request): Resource[F, (Int, Queue[F, Option[Either[FeedException, AnyRef]]])] =
    Resource.make {
      for {
        id <- queuedReq.get.map(c => if (c.isEmpty) 1 else c.lastKey + 1)
        _  <- limits.acquire(request)
        q  <- Queue.unbounded[F, Option[Either[FeedException, AnyRef]]]
        _  <- queuedReq.update(_ + (id -> q))
      } yield (id, q)
    } {
      case (id, _) =>
        for {
          _ <- limits.release(request)
          _ <- queuedReq.update(_.removed(id))
        } yield ()
    }

  // Raises [[FeedException]]
  def register(request: Request): Stream[F, Response] =
    Stream
      .resource(nextId(request))
      .map(_._2)
      .flatMap(Stream.fromQueueNoneTerminated(_).through(ibConnectionTracker.setTimeout(request)))
      .zipWithIndex
      .evalMap {
        case (either, count) =>
          either match {
            case Left(ex) =>
              (ex match {
                // handle all errors feeds here
                case _: FeedShutdown.type =>
                  RequestDaoConnected[F].changeState(request.reqId, RequestState.Cancelled, count.some, ex.some)
                case _ => RequestDaoConnected[F].changeState(request.reqId, RequestState.Cancelled, count.some, ex.some)
              }).as(throw ex)
            case Right(el) =>
              if (count == 1)
                RequestDaoConnected[F].changeState(request.reqId, RequestState.ReceivingData).as(el)
              else
                el.pure[F]
          }
      }
      .map(el =>
        request match {
          case r: RequestData     => Bar(el.asInstanceOf[IbBar], r.contId, r.dataType, r.size)
          case _: RequestContract => Contract(el.asInstanceOf[IbContractDetails])
        }
      )
      .evalTap(el =>
        request match {
          case _: RequestData     => BarDaoConnected[F].write(el.asInstanceOf[Bar])
          case _: RequestContract => ContractDaoConnected[F].create(el.asInstanceOf[Contract])
        }
      )

  def enqueue(id: Int, data: AnyRef): Unit = dispatcher.unsafeRunAndForget {
    queuedReq
      .get
      .flatMap(c =>
        c.get(id) match {
          case Some(req) => req.offer(data.asRight[FeedException].some)
          case None      => errorAll(CriticalFeedError(s"Rogue request Id [$id] received data"))
        }
      )
  }

  def errorOne(id: Int, ex: IbReqError): Unit = dispatcher.unsafeRunAndForget(errorOneF(id, ex))

  def errorOneF(id: Int, ex: IbReqError): F[Unit] =
    queuedReq
      .get
      .flatMap(_.get(id) match {
        case Some(req) => req.offer(ex.asLeft[AnyRef].some)
        case None      => errorAll(CriticalFeedError(s"Rogue request Id [$id] failed"))
      })

  def errorAll(ex: FeedException): F[Unit] =
    for {
      _       <- Logger[F].error(s"Shutting down all request due to [$ex]")
      content <- queuedReq.get
      _       <- content.values.toList.traverse(_.offer(ex.asLeft[AnyRef].some))
    } yield ()

  def close(id: Int): Unit = dispatcher.unsafeRunAndForget {
    queuedReq
      .get
      .flatMap(_.get(id) match {
        case Some(req) => req.offer(None)
        case None      => errorAll(CriticalFeedError(s"Rogue request Id [$id] ended"))
      })
  }
}

object FeedRequestService {
  def apply[F[_]](implicit ev: FeedRequestService[F]): FeedRequestService[F] = ev

  implicit def unsafeLogger[F[_]: Sync]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

  def make[F[_]: Async: Temporal: Clock: BarDaoConnected: RequestDaoConnected: ContractDaoConnected](
    config: Config.Limits
  ): Resource[F, FeedRequestService[F]] =
    for {
      dsp    <- Dispatcher[F]
      limits <- Limits.make[F](config)
      frs <- Resource.make(
        for {
          queuedReq                <- Ref[F].of(SortedMap.empty[Int, Queue[F, Option[Either[FeedException, AnyRef]]]])
          startReceive             <- Ref[F].of(Set.empty[Int])
          ibConnectionErrorHistory <- IbConnectionTracker.build[F]
        } yield new FeedRequestService(
          queuedReq           = queuedReq,
          limits              = limits,
          startedReceive      = startReceive,
          dispatcher          = dsp,
          ibConnectionTracker = ibConnectionErrorHistory
        )
      )(_.errorAll(FeedShutdown))
    } yield frs
}
