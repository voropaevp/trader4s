package domain.feed

import cats.syntax.all._
import cats.effect.std.{Semaphore, UUIDGen}
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
    msgTs: Ref[F, List[FiniteDuration]],
    clntMsgLock: Semaphore[F],
    mainLock: Semaphore[F],
    expireDur: FiniteDuration
  ) extends Limit[F] {
    override def refresh: F[Unit] =
      for {
        now      <- Clock[F].realTime
        content  <- msgTs.get
        nextSize <- mainLock.permit.use(_ => msgTs.updateAndGet(_.filter(_ + expireDur > now)).map(_.size))
        _ <- if (content.size > nextSize)
          mainLock.permit.use(_ => clntMsgLock.releaseN(content.size - nextSize))
        else
          Temporal[F].sleep(200.millis)
      } yield ()

    override def acquire(request: Request): F[Unit] =
      for {
        _   <- clntMsgLock.acquire
        now <- Clock[F].realTime
        _   <- mainLock.permit.use(_ => msgTs.update(_ :+ now))
      } yield ()

    override def release(request: Request): F[Unit] = ().pure[F]
  }

  class Hist10MinLimit[F[_]: Async](
    hist10Min: Ref[F, List[FiniteDuration]],
    hist10MinLock: Semaphore[F],
    mainLock: Semaphore[F],
    expireDur: FiniteDuration
  ) extends Limit[F] {
    override def refresh: F[Unit] =
      for {
        now      <- Clock[F].realTime
        initSize <- hist10Min.get.map(_.size)
        nextSize <- mainLock.permit.use(_ => hist10Min.updateAndGet(_.filter(_ + expireDur > now)).map(_.size))
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
        _   <- mainLock.permit.use(_ => hist10Min.update(_ :+ now))
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
    reqByContSizeLocks: Ref[F, Map[(Int, BarSize), Ref[F, (Semaphore[F], List[FiniteDuration])]]],
    expDuration: FiniteDuration
  ) extends Limit[F] {

    override def refresh: F[Unit] = ().pure[F]

    override def acquire(request: Request): F[Unit] =
      for {
        now <- Clock[F].realTime
        id <- UUIDGen.randomUUID
        _ <- Async[F].delay( println(s"1. $id $now"))
        _ <- request match {
          case r: RequestData if r.requestType == RequestType.Historic =>
            val idx = (r.contId, r.size)
            for {
              lck <- Semaphore[F](1)
              ref <- Ref.of((lck, Nil))
              ref <- reqByContSizeLocks.modify { tsById =>
                tsById.get(idx) match {
                  case Some(ref2) => (tsById, ref2)
                  case None => (tsById + (idx -> ref), ref)
                }
              }
              (lck, _) <- ref.get.map(_._1)
              _ <- lck
                    .permit
                    .use(_ =>
                      for {
                        now    <- Clock[F].realTime
                        ts     <- ref.get.map(_._2)
                        _      <- if (ts.size >= limit) Async[F].delay(println(s"$idx $now ${expDuration + ts.head - now} $ts")) else Async[F].unit
                        _      <- if (ts.size >= limit) Temporal[F].sleep(expDuration + ts.head - now) else Async[F].unit
                        now    <- Clock[F].realTime
                        _ <-  Async[F].delay(println(s"2. $idx $id $now"))
                        ts = ts.filter(_ + expDuration > now) :+ now
                        _ <- ref.set((lck, ts))
                      } yield ()
                    ) >> Async[F].delay( println(s"3. $idx $id $now"))
            }
          case _ => ().pure[F]
        }
      } yield ()

    override def release(request: Request): F[Unit] = ().pure[F]

  }

  def make[F[_]: Async: Temporal: Clock: Logger](lim: config.LimitsConfig): Resource[F, Limits[F]] =
    for {
      hist10Min          <- Resource.eval(Ref[F].of(List.empty[FiniteDuration]))
      hist10MinLock      <- Resource.eval(Semaphore[F](lim.hist10MinLimit))
      reqByContSizeLocks <- Resource.eval(Ref[F].of(Map.empty[(Int, BarSize), (Semaphore[F], List[FiniteDuration])]))
      hist10MinMainLock  <- Resource.eval(Semaphore[F](1))
      msgLimitMainLock   <- Resource.eval(Semaphore[F](1))
      msgLimit           <- Resource.eval(Ref[F].of(List.empty[FiniteDuration]))
      msgLimitLock       <- Resource.eval(Semaphore[F](lim.clientMsgLimit))
      concurrentSubsLock <- Resource.eval(Semaphore[F](lim.concurrentSubLimit))

      contractSizeLimit = new SameContractSizeLimit[F](
        lim.sameContractAndSizeLimit,
        reqByContSizeLocks,
        lim.sameContractAndSizeDuration
      )
      subLimit       = new SubLimit(concurrentSubsLock)
      limit10Min     = new Hist10MinLimit(hist10Min, hist10MinLock, hist10MinMainLock, lim.hist10MinDuration)
      clientMsgLimit = new ClientMessageLimit(msgLimit, msgLimitLock, msgLimitMainLock, lim.clientMsgDuration)
      _ <- limit10Min.refresh.foreverM.background.void
      _ <- clientMsgLimit.refresh.foreverM.background.void
    } yield new Limits(
      contractSizeLimit  = contractSizeLimit,
      subLimit           = subLimit,
      limit10Min         = limit10Min,
      clientMessageLimit = clientMsgLimit
    )
}
