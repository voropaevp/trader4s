package domain.feed

import cats.data.EitherT
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.{Async, Clock, Deferred, Ref, Resource, Sync, Temporal}
import cats.effect.std.Dispatcher
import db.ConnectedDao._
import domain.feed.FeedException.IbError
import model.datastax.ib.feed.ast.RequestState
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._
import scala.collection.immutable.SortedMap
import model.datastax.ib.feed.request.{Request, RequestContract, RequestData}
import model.datastax.ib.feed.response.Response
import utils.config.Config

import scala.concurrent.duration.FiniteDuration

class FeedRequestService[F[_]: Async: Temporal: Clock: Logger](
  queuedReq: Ref[F, SortedMap[Int, QueuedFeedRequest[F]]],
  limits: Limits[F],
  startedReceive: Ref[F, Set[Int]],
  dispatcher: Dispatcher[F],
  criticalError: Deferred[F, IbError]
)(implicit reqDao: RequestDaoConnected[F]) {

  implicit def unsafeLogger[G[_]: Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  def stoppedByError: F[Boolean] = criticalError.tryGet.map(_.nonEmpty)

  def sendError(ex: IbError): Unit = dispatcher.unsafeRunAndForget(
    Logger[F].error(s"${ex.getMessage}  ${ex.printStackTrace()}") >> criticalError.complete(ex) >> failAll(ex)
  )

  def register(request: Request): EitherT[F, FeedException, QueuedFeedRequest[F]] =
    for {
      _ <- EitherT
        .fromOptionF(criticalError.tryGet, ())
        .swap
        .leftMap(ex => IbError(s"Request [$request] rejected due to earlier critical feed error [$ex]", cause = ex))
      _ <- EitherT.liftF(
        request match {
          case req: RequestContract => reqDao.createContract(req)
          case req: RequestData     => reqDao.createData(req)
        }
      )
      req <- EitherT.right(for {
        _    <- limits.acquire(request)
        id   <- queuedReq.get.map(c => if (c.isEmpty) 1 else c.lastKey + 1)
        _    <- Logger[F].debug(s"Request id [$id] assigned for [$request]")
        reqF <- QueuedFeedRequest(id, request)
        _    <- queuedReq.update(_ + (id -> reqF))
      } yield reqF)
    } yield req

  def getAll: F[SortedMap[Int, QueuedFeedRequest[F]]] = queuedReq.get

  def enqueue[T](id: Int, fReq: Request => Either[IbError, Response]): Unit = dispatcher.unsafeRunAndForget {
    queuedReq
      .get
      .flatMap(c =>
        c.get(id) match {
          case Some(req) =>
            fReq(req.request)
              .fold(criticalError.complete, req.enqueue)
              .>>(startedReceive.getAndUpdate(_ + id).flatMap {
                case s if s.contains(id) =>
                  reqDao.changeState(req.request.reqId, RequestState.ReceivingData).as(())
                case _ => Sync[F].unit
              })
          case None => criticalError.complete(IbError(s"Rogue request Id [$id] received data")).as(())
        }
      )
  }

  def fail(id: Int, ex: IbError): Unit = dispatcher.unsafeRunAndForget(failF(id, ex))

  def failF(id: Int, ex: IbError): F[Unit] =
    queuedReq
      .get
      .flatMap(_.get(id) match {
        case Some(req) =>
          for {
            rows <- req.rowsReceived.get.map(_.some)
            _    <- reqDao.changeState(req.request.reqId, RequestState.Failed, rows, ex.some)
            _    <- limits.release(req.request)
            _    <- queuedReq.update(_.removed(id))
            _    <- startedReceive.update(_ - id)
            _    <- req.fail(ex)
          } yield ()
        case None => criticalError.complete(IbError(s"Rogue request Id [$id] failed")).as(())
      })

  def failAll(ex: IbError): F[Unit] =
    for {
      _       <- Logger[F].error(s"Shutting down all request due to [$ex]")
      content <- queuedReq.get
      _       <- content.values.toList.traverse(_.fail(ex))
      _       <- queuedReq.set(SortedMap.empty[Int, QueuedFeedRequest[F]])
    } yield ()

  def close(id: Int): Unit = dispatcher.unsafeRunAndForget {
    queuedReq
      .get
      .flatMap(_.get(id) match {
        case Some(req) =>
          for {
            rows <- req.rowsReceived.get.map(_.some)
            _    <- reqDao.changeState(req.request.reqId, RequestState.Failed, rows)
            _    <- limits.release(req.request)
            _    <- queuedReq.update(_.removed(id))
            _    <- startedReceive.update(_ - id)
            _    <- req.close()
          } yield ()
        case None => criticalError.complete(IbError(s"Rogue request Id [$id] ended")).as(())
      })
  }
}

object FeedRequestService {

  def apply[F[_]](implicit ev: FeedRequestService[F]): FeedRequestService[F] = ev

  def make[F[_]: Async: Temporal: Clock: Logger: RequestDaoConnected](
    timeout: FiniteDuration,
    config: Config.Limits
  ): Resource[F, FeedRequestService[F]] =
    for {
      dsp    <- Dispatcher[F]
      limits <- Limits.make[F](config)
      frs <- Resource.make(
        for {
          queuedReq     <- Ref[F].of(SortedMap.empty[Int, QueuedFeedRequest[F]])
          startReceive  <- Ref[F].of(Set.empty[Int])
          criticalError <- Deferred[F, IbError]
        } yield new FeedRequestService(
          queuedReq      = queuedReq,
          limits         = limits,
          startedReceive = startReceive,
          dispatcher     = dsp,
          criticalError  = criticalError
        )
      )(frs =>
        for {
          content <- frs.getAll
          _       <- content.values.toList.traverse(_.cancel())
        } yield ()
      )
      housekeeping = for {
        _              <- Temporal[F].sleep(1.seconds)
        queuedRequests <- frs.getAll
        _ <- queuedRequests
          .values
          .toList
          .traverse(req =>
            for {
              updTime <- req.updateTime.get
              now     <- Clock[F].realTime
              _ <- if (now - updTime > timeout) {
                frs.failF(req.id, IbError(s"Timeout [$timeout] waiting for data"))
              } else {
                ().pure[F]
              }
            } yield ()
          )
      } yield ()
      _ <- housekeeping.foreverM.background.void
    } yield frs
}
