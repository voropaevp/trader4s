package domain.feed

import cats.data.EitherT
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.{Async, Clock, Deferred, Ref, Resource, Sync, Temporal}
import cats.effect.std.{Dispatcher, Queue}
import db.ConnectedDao._
import domain.feed.FeedException.IbError
import domain.feed.FeedRequestService.LimitError
import model.datastax.ib.feed.ast.{BarSize, DataType, RequestState, RequestType}
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._
import scala.collection.immutable.SortedMap
import model.datastax.ib.feed.request.{Request, RequestContract, RequestData}
import model.datastax.ib.feed.response.Response

import scala.concurrent.duration.FiniteDuration

class FeedRequestService[F[_]: Async: Temporal: Clock: Logger](
  queuedReq: Ref[F, SortedMap[Int, QueuedFeedRequest[F]]],
  limits: Limits[F],
  startedReceive: Ref[F, Set[Int]],
  dispatcher: Dispatcher[F],
  criticalError: Deferred[F, IbError]
)(implicit reqDao: RequestDaoConnected[F]) {

  //Max of 100 active subscriptions.
  //Making identical historical data request within 15 seconds.
  //Making six or more historical data request for the same Contract, Exchange and Tick Type within two seconds.
  //Making more than 60 request within any ten minute period.
  //Note that when BID_ASK historical data is requested, each request is counted twice.

  implicit def unsafeLogger[G[_]: Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  def stoppedByError: F[Boolean] = criticalError.tryGet.map(_.nonEmpty)

  def expireReqByTime: F[Unit] =
    for {
      now <- Clock[F].realTime
      _   <- recentReqTime.update(_.view.filter(_ < now - 10.minutes).toSet)
    } yield ()

  private def acquire(request: Request): F[(Int, Int, Int)] =
    for {
      now     <- Clock[F].realTime
      nRecent <- recentReqTime.get.map(_.size)
      nSubs   <- concurrentSubs.get
      nSame <- request match {
        case r: RequestData =>
          reqByContSize
            .updateAndGet(_.updatedWith((r.contId, r.size)) {
              case Some(value) =>
                val newSet = value.filter(_ < now - 2.seconds)
                if (newSet.nonEmpty)
                  Some(newSet)
                else
                  None
              case None => None
            })
            .map(_.getOrElse((r.contId, r.size), Set()).size)
        case _ => Sync[F].pure(0)
      }
    } yield (nRecent, nSubs, nSame)


  def sendError(ex: IbError): Unit = dispatcher.unsafeRunAndForget(
    Logger[F].error(s"${ex.getMessage}  ${ex.printStackTrace()}") >> criticalError.complete(ex) >> failAll(ex)
  )

  def retryRegister(request: Request): F[Either[FeedException, QueuedFeedRequest[F]]] =
    request match {
      case req: RequestContract => reqDao.createContract(req)
      case req: RequestData     => reqDao.createData(req)
    }

    register(request).value.flatMap {
      case Left(err) =>
        err match {
          case _: LimitError => Temporal[F].sleep(10.seconds) >> retryRegister(request)
        }
      case Right(value) => Either.right[FeedException, QueuedFeedRequest[F]](value).pure[F]
    }


  def register(request: Request): EitherT[F, FeedException, QueuedFeedRequest[F]] = {
    checkLimits(request).flatMap {
      case Left(_) =>  Temporal[F].sleep(10.seconds) >> checkLimits(request)
      case Right(r) => r.pure
    }
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
      _ <- EitherT(checkLimits(request))
      req <- EitherT.right(for {
        id   <- queuedReq.get.map(c => if (c.isEmpty) 1 else c.lastKey + 1)
        _    <- Logger[F].debug(s"Request id [$id] assigned for [$request]")
        reqF <- QueuedFeedRequest(id, request)
        _    <- queuedReq.update(_ + (id -> reqF))
        now  <- Clock[F].realTime
        _    <- recentReqTime.update(_ + now)
        _ <- request match {
          case req: RequestData =>
            if (req.requestType == RequestType.Historic)
              reqByContSize.update(_.updatedWith(req.contId, req.size) {
                case Some(value) =>
                  if (req.dataType == DataType.BidAsk)
                    Some((value + now) + (now + 1.seconds))
                  else
                    Some(value + now)
                case None => Some(Set(now))
              })
            else if (req.dataType == DataType.BidAsk)
              concurrentSubs.update(_ + 2)
            else
              concurrentSubs.update(_ + 1)
        }
      } yield reqF)
    } yield req
  }

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
          (req.request match {
            case r: RequestData =>
              if (r.requestType == RequestType.Subscription)
                if (r.dataType == DataType.BidAsk)
                  concurrentSubs.update(_ - 2)
                else
                  concurrentSubs.update(_ - 1)
              else
                Sync[F].unit
            case _ => Sync[F].unit
          }) *>
            req
              .rowsReceived
              .get
              .map(_.some)
              .flatMap(reqDao.changeState(req.request.reqId, RequestState.Failed, _, ex.some)) *>
            queuedReq.update(_.removed(id)) *>
            startedReceive.update(_ - id) *>
            req.fail(ex)
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
          (
            req.request match {
              case r: RequestData =>
                if (r.requestType == RequestType.Subscription)
                  concurrentSubs.update(_ - 1)
                else
                  Sync[F].unit
              case _ => Sync[F].unit
            }
          ) *> req
            .rowsReceived
            .get
            .map(_.some)
            .flatMap(reqDao.changeState(req.request.reqId, RequestState.Complete, _)) *>
            queuedReq.update(_.removed(id)) *>
            startedReceive.update(_ - id) *>
            req.close()
        case None => criticalError.complete(IbError(s"Rogue request Id [$id] ended")).as(())
      })
  }
}

object FeedRequestService {

  def apply[F[_]](implicit ev: FeedRequestService[F]): FeedRequestService[F] = ev

  def make[F[_]: Async: Temporal: Clock: Logger: RequestDaoConnected](
    timeout: FiniteDuration
  ): Resource[F, FeedRequestService[F]] =
    for {
      dsp <- Dispatcher[F]
      frs <- Resource.make(
        for {
          queuedReq      <- Ref[F].of(SortedMap.empty[Int, QueuedFeedRequest[F]])
          recentReqTime  <- Ref[F].of(Set.empty[FiniteDuration])
          reqByContSize  <- Ref[F].of(Map.empty[(Int, BarSize), Set[FiniteDuration]])
          concurrentSubs <- Ref[F].of(0)
          startReceive   <- Ref[F].of(Set.empty[Int])
          errorChannel   <- Deferred[F, IbError]
        } yield new FeedRequestService(
          queuedReq,
          recentReqTime,
          reqByContSize,
          concurrentSubs,
          startReceive,
          dsp,
          errorChannel
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
        _ <- frs.expireReqByTime
      } yield ()
      _ <- housekeeping.foreverM.background.void
    } yield frs
}
