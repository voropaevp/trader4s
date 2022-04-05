package domain.feed

import cats.syntax.all._
import cats.effect.std.Semaphore
import cats.effect.{Async, Clock, Ref, Resource, Temporal}
import cats.effect.syntax.all._

import domain.feed.Limits._

import model.datastax.ib.feed.ast.{BarSize, RequestType}
import model.datastax.ib.feed.request.{Request, RequestData}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

class Limits[F[_]: Async: Temporal: Clock: Logger](
  limit10Min: Hist10MinLimit[F],
  subLimit: SubLimit[F],
  clientMessageLimit: ClientMessageLimit[F],
  contractSizeLimit: SameContractSizeLimit[F]
) {

  def acquire(request: Request): F[Unit] =
    limit10Min.acquire(request) *>
      subLimit.acquire(request) *>
      clientMessageLimit.acquire(request) *>
      contractSizeLimit.acquire(request)

  def release(request: Request): F[Unit] =
    limit10Min.release(request) *>
      clientMessageLimit.release(request) *>
      subLimit.release(request) *>
      contractSizeLimit.release(request)

}

object Limits {
  sealed trait Limit[F[_]] {

    def refresh: F[Unit]

    def acquire(request: Request): F[Unit]

    def release(request: Request): F[Unit]
  }

  class ClientMessageLimit[F[_]: Async](
    msgTs: Ref[F, Set[FiniteDuration]],
    clntMsgLock: Semaphore[F],
    mainLock: Semaphore[F],
    maxDelay: FiniteDuration
  ) extends Limit[F] {
    override def refresh: F[Unit] =
      mainLock
        .permit
        .use(_ =>
          for {
            now      <- Clock[F].realTime
            initSize <- msgTs.get.map(_.size)
            nextSize <- msgTs.updateAndGet(_.filter(_ < now - maxDelay)).map(_.size)
            _ <- if (initSize > nextSize)
              clntMsgLock.releaseN(initSize - nextSize)
            else
              ().pure[F]
          } yield ()
        )

    override def acquire(request: Request): F[Unit] =
      for {
        _ <- request match {
          case _: RequestData => clntMsgLock.acquire
          case _              => ().pure[F]
        }
        now <- Clock[F].realTime
        _   <- mainLock.permit.use(_ => msgTs.update(_ + now))
      } yield ()

    override def release(request: Request): F[Unit] = ().pure[F]
  }

  class Hist10MinLimit[F[_]: Async](
    hist10Min: Ref[F, Set[FiniteDuration]],
    hist10MinLock: Semaphore[F],
    mainLock: Semaphore[F],
    expireDur: FiniteDuration
  ) extends Limit[F] {
    override def refresh: F[Unit] =
      for {
        now      <- Clock[F].realTime
        initSize <- hist10Min.get.map(_.size)
        nextSize <- hist10Min.updateAndGet(_.filter(_ + expireDur > now)).map(_.size)
        _ <- if (initSize > nextSize)
          mainLock.permit.use(_ => hist10MinLock.releaseN(initSize - nextSize))
        else
          Temporal[F].sleep(200.millis)
      } yield ()

    override def acquire(request: Request): F[Unit] =
      for {
        _ <- request match {
          case _: RequestData => hist10MinLock.acquire
          case _              => ().pure[F]
        }
        now <- Clock[F].realTime
        _   <- mainLock.permit.use(_ => hist10Min.update(_ + now))
      } yield ()

    override def release(request: Request): F[Unit] = ().pure[F]
  }

  class SubLimit[F[_]: Async](
    concurrentSubsLock: Semaphore[F]
  ) extends Limit[F] {
    override def refresh: F[Unit] = Temporal[F].sleep(200.millis)

    override def acquire(request: Request): F[Unit] =
      for {
        _ <- request match {
          case r: RequestData if r.requestType == RequestType.Subscription => concurrentSubsLock.acquire
          case _                                                           => ().pure[F]
        }
      } yield ()

    override def release(request: Request): F[Unit] =
      for {
        _ <- request match {
          case r: RequestData if r.requestType == RequestType.Subscription => concurrentSubsLock.release
          case _                                                           => ().pure[F]
        }
      } yield ()
  }

  class SameContractSizeLimit[F[_]: Async](
    limit: Int,
    reqByContSizeLocks: Ref[F, Map[(Int, BarSize), (Set[FiniteDuration], Semaphore[F])]],
    exireDuration: FiniteDuration
  ) extends Limit[F] {

    override def refresh: F[Unit] =
      for {
        map <- reqByContSizeLocks.get
        now <- Clock[F].realTime
        newMap <- map.iterator.toList.traverse {
          case (idx, (s, sem)) =>
            for {
              nextS <- s.filter(_ < now - exireDuration).pure[F]
              _ <- if (s.size - nextS.size > 0)
                sem.releaseN(s.size - nextS.size)
              else
                Temporal[F].sleep(200.millis)
            } yield
              if (s.size - nextS.size == 0)
                List.empty[((Int, BarSize), (Set[FiniteDuration], Semaphore[F]))]
              else
                List((idx, (nextS, sem)))
        }
        _ <- reqByContSizeLocks.set(Map.from(newMap.flatten))
      } yield ()

    override def acquire(request: Request): F[Unit] =
      for {
        now <- Clock[F].realTime
        _ <- request match {
          case r: RequestData if r.requestType == RequestType.Historic =>
            val idx = (r.contId, r.size)
            for {
              maybeVal <- reqByContSizeLocks.get.map(_.get(idx))
              newSem   <- Semaphore[F](limit)
              _        <- newSem.acquire
              newVal <- maybeVal match {
                case Some((s, sem)) => sem.acquire.as((s + now, sem))
                case None           => (Set(now), newSem).pure[F]
              }
              _ <- reqByContSizeLocks.update(_.updated(idx, newVal))
            } yield ()
          case _ => ().pure[F]
        }
      } yield ()

    override def release(request: Request): F[Unit] = ().pure[F]

  }

  def make[F[_]: Async: Temporal: Clock: Logger](lim: config.LimitsConfig): Resource[F, Limits[F]] =
    for {
      hist10Min          <- Resource.eval(Ref[F].of(Set.empty[FiniteDuration]))
      hist10MinLock      <- Resource.eval(Semaphore[F](lim.hist10MinLimit))
      reqByContSizeLocks <- Resource.eval(Ref[F].of(Map.empty[(Int, BarSize), (Set[FiniteDuration], Semaphore[F])]))
      hist10MinMainLock  <- Resource.eval(Semaphore[F](1))
      msgLimitMainLock   <- Resource.eval(Semaphore[F](1))
      msgLimit           <- Resource.eval(Ref[F].of(Set.empty[FiniteDuration]))
      msgLimitLock       <- Resource.eval(Semaphore[F](lim.clientMsgLimit))
      concurrentSubsLock <- Resource.eval(Semaphore[F](lim.concurrentSubLimit))

      contractSizeLimit = new SameContractSizeLimit(
        lim.sameContractAndSizeLimit,
        reqByContSizeLocks,
        lim.sameContractAndSizeDuration
      )
      subLimit       = new SubLimit(concurrentSubsLock)
      limit10Min     = new Hist10MinLimit(hist10Min, hist10MinLock, hist10MinMainLock, lim.hist10MinDuration)
      clientMsgLimit = new ClientMessageLimit(msgLimit, msgLimitLock, msgLimitMainLock, lim.clientMsgDuration)
      _ <- contractSizeLimit.refresh.foreverM.background.void
      _ <- subLimit.refresh.foreverM.background.void
      _ <- limit10Min.refresh.foreverM.background.void
      _ <- clientMsgLimit.refresh.foreverM.background.void
    } yield new Limits(
      contractSizeLimit  = contractSizeLimit,
      subLimit           = subLimit,
      limit10Min         = limit10Min,
      clientMessageLimit = clientMsgLimit
    )
}
