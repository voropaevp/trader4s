package domain.feed

import cats.effect.std.Semaphore
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.{Async, Clock, Ref, Resource, Temporal}
import cats.implicits.catsSyntaxFlatMapOps
import domain.feed.FeedRequestService.LimitError
import domain.feed.Limits.LimitState

import scala.concurrent.duration._
import model.datastax.ib.feed.ast.{BarSize, RequestType}
import model.datastax.ib.feed.request.{Request, RequestData}
import org.typelevel.log4cats.Logger
import utils.config.Config

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

class Limits[F[_]: Async: Temporal: Clock: Logger](
  limits: Config.Limits,
  limitState: Ref[F, Limits.LimitState],

) {


  def tryAcquire(request: Request): F[Unit] =
    lock.acquire >> Clock[F].realTime.flatMap { now =>
      limitState.get.flatMap {
        _.tailRecM[F, LimitState](refState =>
          refState.updateForRequest(request, now, limits) match {
            case Left(ex)  => ex match {
              case _ :LimitError.ConcurrentSubLimit =>
              case _ :LimitError.ConcurrentHistoricLimit =>
              case _ :LimitError.SameContractAndSizeLimit =>
            }[F].sleep(1.seconds) >> refState.asLeft[LimitState].pure[F]
            case Right(state) => state.asRight[LimitState].pure[F]
          }
        )
      }
    }

  def acquire(request: Request): Resource[F, Request] =
    lock.permit >> Resource.make {
      Clock[F].realTime.flatMap { now =>
        limitState
          .updateAndGet(_.refresh(request, now))
          .flatMap(_.updateForRequest(request, now, limits) match {
            case Left(limitEx)   => Temporal[F].sleep(1.seconds) >>
            case Right(newState) => limitState.set(newState).as(request)
          })
      }
    } { r =>
      limitState.update(_.delete(r))
    }
}

object Limits {
  sealed trait Limit[F[_]]{

    def refresh:F[Unit]

    def acquire(request: Request): F[Unit]

    def release(request: Request): F[Unit]
  }

  class RecentLimit[F[_]: Async](
                                       recentReqTime: Ref[F, Set[FiniteDuration]],
                                       recentReqTimeLock: Semaphore[F],
                                       mainLock: Semaphore[F],
                                     ) extends Limit[F] {
    override def refresh: F[Unit] = mainLock.permit.use(_ =>
      for {
          now <- Clock[F].realTime
          initSize <- recentReqTime.get.map(_.size)
          nextSize <- recentReqTime.updateAndGet(_.filter(_ < now - 10.minutes)).map(_.size)
          _ <- if (initSize > nextSize)
                recentReqTimeLock.releaseN(initSize - nextSize)
              else
                ().pure[F]
        } yield ()
    )

    override def acquire(request: Request): F[Unit] = for {
      _ <- request match {
              case _: RequestData => recentReqTimeLock.acquire
              case _ => ().pure[F]
            }
      now <-Clock[F].realTime
      _ <-  mainLock.permit.use(_ => recentReqTime.update(_ + now))
    } yield ()


    override def release(request: Request): F[Unit] = ().pure[F]
  }


  class SubLimit[F[_]: Async] (
                                concurrentSubsLock: Semaphore[F],
    ) extends Limit[F] {
    override def refresh: F[Unit] = ().pure[F]

    override def acquire(request: Request): F[Unit] = for {
      _ <- request match {
        case r: RequestData if r.requestType == RequestType.Subscription => concurrentSubsLock.acquire
        case _ => ().pure[F]
      }
    } yield ()


    override def release(request: Request): F[Unit] = for {
      _ <- request match {
        case r: RequestData if r.requestType == RequestType.Subscription => concurrentSubsLock.release
        case _ => ().pure[F]
      }
    } yield ()
  }

  class SameContractSizeLimit[F[_]: Async](
    private val reqByContSizeLocks: Ref[F, Map[(Int, BarSize), (Set[FiniteDuration] , Semaphore[F])]]
  ) extends Limit[F] {

    override def refresh:F[Unit] = for {
      map <- reqByContSizeLocks.get
      now <-Clock[F].realTime
      newMap <- map.iterator.toList.traverse{ case (idx, (s, sem)) =>
         for {
             nextS  <-  s.filter(_ < now - 2.seconds).pure[F]
             _ <-  if  (s.size - nextS.size > 0)
                      sem.releaseN(s.size - nextS.size)
                  else
                    ().pure[F]
         } yield
                  if (s.size - nextS.size == 0)
                    List.empty[((Int, BarSize), (Set[FiniteDuration] , Semaphore[F]))]
                  else
                    List((idx, (nextS, sem)))
        }
      _ <- reqByContSizeLocks.set(Map.from(newMap.flatten))
    } yield ()

    override def acquire(request: Request): F[Unit] = for {
      now <-Clock[F].realTime
      _ <- request match {
        case r: RequestData if r.requestType == RequestType.Historic =>
         val idx = (r.contId, r.size)
         for {
            maybeVal <- reqByContSizeLocks.get.map(_.get(idx))
            newSem <- Semaphore[F](2)
            _   <- newSem.acquire
            newVal  <- maybeVal match {
              case Some((s, sem)) => sem.acquire.as((s + now, sem))
              case None => (Set(now), newSem).pure[F]
            }
            _ <- reqByContSizeLocks.update(_.updated(idx, newVal))
          } yield ()
        case _ => ().pure[F]
      }
    } yield ()

    override def release(request: Request): F[Unit] = ().pure[F]

  }
}
//
//  def apply[F[_]: Async: Temporal: Clock: Logger](lim: Config.Limits): F[Limits[F]] =
//    for {
//      limitState <- Ref[F].of(LimitState(Set.empty[FiniteDuration], Map.empty[(Int, BarSize), Set[FiniteDuration]], 0))
//    } yield new Limits(
//      limits     = lim,
//      limitState = limitState
//    )
//}
