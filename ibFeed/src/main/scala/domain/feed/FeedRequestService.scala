package domain.feed

import cats.data.EitherT
import cats.implicits._
import cats.effect.implicits._
import cats.effect.{Async, Clock, Deferred, Ref, Resource, Sync, Temporal}
import cats.effect.std.Dispatcher
import db.ConnectedDao._
import model.datastax.ib.feed.ast.{BarSize, DataType, RequestState, RequestType}
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._
import scala.collection.immutable.SortedMap
import model.datastax.ib.feed.request.{Request, RequestData}
import model.datastax.ib.feed.response.Response

import scala.concurrent.duration.FiniteDuration

class FeedRequestService[F[_]: Async: Temporal: Clock](
  queuedReq: Ref[F, SortedMap[Int, QueuedFeedRequest[F]]],
  recentReqTime: Ref[F, Set[FiniteDuration]],
  reqByContSize: Ref[F, Map[(Int, BarSize), Set[FiniteDuration]]],
  concurrentSubs: Ref[F, Int],
  startedReceive: Ref[F, Set[Int]],
  dispatcher: Dispatcher[F],
  criticalError: Deferred[F, GenericError]
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

  private def getLimitFor(request: Request): F[(Int, Int, Int)] =
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

  def checkLimits(request: Request): EitherT[F, FeedRequestService.LimitError, Unit] =
    for {
      (nRecent, nSubs, nSame) <- EitherT.right(getLimitFor(request))
      _ <- (if (nSame > 5)
              EitherT
                .left(Async[F].pure(FeedRequestService.LimitError.SameContractAndSizeLimit))
                .leftWiden[FeedRequestService.LimitError]
            else
              request match {
                case r: RequestData =>
                  r.reqType match {
                    case RequestType.Subscription =>
                      if (nSubs > 99)
                        EitherT
                          .left(Sync[F].pure(FeedRequestService.LimitError.ConcurrentSubLimit))
                          .leftWiden[FeedRequestService.LimitError]
                      else EitherT.right[FeedRequestService.LimitError](Async[F].unit)
                    case RequestType.Historic =>
                      if (nRecent > 59)
                        EitherT
                          .left(Sync[F].pure(FeedRequestService.LimitError.ConcurrentHistoricLimit))
                          .leftWiden[FeedRequestService.LimitError]
                      else EitherT.right[FeedRequestService.LimitError](Async[F].unit)
                    case _ => EitherT.right[FeedRequestService.LimitError](Async[F].unit)
                  }
                case _ => EitherT.right[FeedRequestService.LimitError](Async[F].unit)
              })
    } yield ()

  def sendError(ex: GenericError): Unit = dispatcher.unsafeRunAndForget(
    Logger[F].error(s"${ex.getMessage}  ${ex.printStackTrace()}") >> criticalError.complete(ex) >> failAll(ex)
  )

  def register(request: Request): EitherT[F, FeedError, QueuedFeedRequest[F]] =
    for {
      _ <- EitherT
        .fromOptionF(criticalError.tryGet, ())
        .swap
        .leftMap(ex => GenericError(s"Request [$request] rejected due to earlier critical feed error [$ex]", cause = ex)
        )
      _ <- checkLimits(request)
      req <- EitherT.right(for {

        id   <- queuedReq.get.map(c => if (c.isEmpty) 1 else c.lastKey + 1)
        _    <- Logger[F].debug(s"Request id [$id] assigned for [$request]")
        reqF <- QueuedFeedRequest(id, request)
        _    <- queuedReq.update(_ + (id -> reqF))
        now  <- Clock[F].realTime
        _    <- recentReqTime.update(_ + now)
        _ <- request match {
          case req: RequestData =>
            if (req.reqType == RequestType.Historic)
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

  def getAll: F[SortedMap[Int, QueuedFeedRequest[F]]] = queuedReq.get

  def enqueue[T](id: Int, fReq: Request => Either[GenericError, Response]): Unit = dispatcher.unsafeRunAndForget {
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
          case None => criticalError.complete(GenericError(s"Rogue request Id [$id] received data")).as(())
        }
      )
  }

  def fail(id: Int, ex: GenericError): Unit = dispatcher.unsafeRunAndForget(failF(id, ex))

  def failF(id: Int, ex: GenericError): F[Unit] =
    queuedReq
      .get
      .flatMap(_.get(id) match {
        case Some(req) =>
          (req.request match {
            case r: RequestData =>
              if (r.reqType == RequestType.Subscription)
                if (r.dataType == DataType.BidAsk)
                  concurrentSubs.update(_ - 2)
                else
                  concurrentSubs.update(_ - 1)
              else
                Sync[F].unit
            case _ => Sync[F].unit
          }) *> reqDao.changeState(req.request.reqId, RequestState.Failed) *>
            queuedReq.update(_.removed(id)) *>
            startedReceive.update(_ - id) *>
            req.fail(ex)
        case None => criticalError.complete(GenericError(s"Rogue request Id [$id] failed")).as(())
      })

  def failAll(ex: GenericError): F[Unit] =
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
                if (r.reqType == RequestType.Subscription)
                  concurrentSubs.update(_ - 1)
                else
                  Sync[F].unit
              case _ => Sync[F].unit
            }
          ) *> reqDao.changeState(req.request.reqId, RequestState.Complete) *>
            queuedReq.update(_.removed(id)) *>
            startedReceive.update(_ - id) *>
            req.close()
        case None => criticalError.complete(GenericError(s"Rogue request Id [$id] ended")).as(())
      })
  }
}

object FeedRequestService {

  sealed trait LimitError extends FeedError

  object LimitError {
    case object ConcurrentSubLimit extends LimitError

    case object ConcurrentHistoricLimit extends LimitError

    case object SameContractAndSizeLimit extends LimitError
  }

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
          errorChannel   <- Deferred[F, GenericError]
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
                frs.failF(req.id, GenericError(s"Timeout [$timeout] waiting for data"))
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
