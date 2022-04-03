package domain.feed

import cats.{Monad, NonEmptyParallel, Parallel}
import fs2._
import cats.data.EitherT
import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import domain.feed.FeedException.{IbReqError, RequestTimeout}
import fs2.{Pipe, Pull}
import model.datastax.ib.feed.ast.RequestType
import model.datastax.ib.feed.request.{Request,  RequestData}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class IbConnectionTracker[F[_]: Async: Clock: NonEmptyParallel](
  streamTimeout: FiniteDuration,
  connectionError: Ref[F, (FiniteDuration, Boolean)],
  histDataFarmError: Ref[F, (FiniteDuration, Boolean)],
  marketDataFarmError: Ref[F, (FiniteDuration, Boolean)]
) {

  // Sets the timeouts for the requests. Resetting the timeout, if connection to IB gateway was broken
  // and restored. If connection is broken stream will be waiting indefinitely for connection to come back.
  // `connectionState` will throw the Error if it is down for too long, which will reset the whole service.
  def setTimeout(request: Request): Pipe[F, Either[FeedException, AnyRef], Either[FeedException, AnyRef]] =
    (st: Stream[F, Either[FeedException, AnyRef]]) =>
      st.pull
        .timed { timedPull =>
          def go(
            timedPull: Pull.Timed[F, Either[FeedException, AnyRef]]
          ): Pull[F, Either[FeedException, AnyRef], Unit] =
            timedPull.timeout(streamTimeout) >> // starts new timeout and stops the previous one
              timedPull.uncons.flatMap {
                case Some((Right(elems), next)) =>
                  Pull.output(elems) >>
                    go(next)
                case Some((Left(_), next)) =>
                  Pull.eval(Clock[F].realTime).flatMap(now => Pull.eval(isCausedByBrokerError(request, now))).flatMap {
                    case true  => go(next)
                    case false => Pull.output(Chunk(RequestTimeout(streamTimeout).asLeft[AnyRef])) >> Pull.done
                  }
                case None => Pull.done
              }

          go(timedPull)
        }
        .stream

  def addError(ex: IbReqError): F[Unit] = Clock[F].realTime.flatMap { now =>
    ex.code match {
      case 1100 => connectionError.set((now, true))
      case 2110 => connectionError.set((now, true))
      case 2103 => marketDataFarmError.set((now, true))
      case 2105 => histDataFarmError.set((now, true))
    }
  }

  def resolveError(ex: IbReqError): F[Unit] = Clock[F].realTime.flatMap { now =>
    ex.code match {
      case 1102 => connectionError.set((now, false))
      case 2104 => marketDataFarmError.set((now, false))
      case 2107 => histDataFarmError.set((now, false))
      case 2108 => histDataFarmError.set((now, false))
    }
  }


  def isCausedByHistoricFarmError(tsOfTimeout: FiniteDuration): F[Boolean] =
    histDataFarmError.get.flatMap {
      case (modifiedTs, isError) =>
        if (isError)
          Temporal[F].sleep(500.millis) *> isCausedByHistoricFarmError(tsOfTimeout)
        else
          (tsOfTimeout - modifiedTs < streamTimeout).pure[F]
    }


    def isCausedBySubscriptionFarmError(tsOfTimeout: FiniteDuration): F[Boolean] =
      marketDataFarmError.get.flatMap {
      case (modifiedTs, isError) =>
        if (isError)
          Temporal[F].sleep(500.millis) *> isCausedBySubscriptionFarmError(tsOfTimeout)
        else
          (tsOfTimeout - modifiedTs < streamTimeout).pure[F]
    }


    def isCausedByConnectionError(tsOfTimeout: FiniteDuration): F[Boolean] =
    connectionError.get.flatMap {
      case (modifiedTs, isError) =>
        if (isError)
          Temporal[F].sleep(500.millis) *> isCausedByConnectionError(tsOfTimeout)
        else
          (tsOfTimeout - modifiedTs < streamTimeout).pure[F]
    }

  // Checks if connection had broken when timeout was reached. Blocks indefinitely until connection is restored.
  def isCausedByBrokerError(request: Request, tsOfTimeout: FiniteDuration): F[Boolean] = request match {
      case r : RequestData if r.requestType == RequestType.Historic  =>
        (isCausedByConnectionError(tsOfTimeout), isCausedByHistoricFarmError(tsOfTimeout)).parMapN(_ && _)
      case r : RequestData if r.requestType == RequestType.Subscription  =>
        (isCausedByConnectionError(tsOfTimeout), isCausedBySubscriptionFarmError(tsOfTimeout)).parMapN(_ && _)
      case _ => isCausedByConnectionError(tsOfTimeout)
    }
}

object IbConnectionTracker {
  def build[F[_]: Async: Clock: NonEmptyParallel](streamTimeout: FiniteDuration): F[IbConnectionTracker[F]] =
    for {
      now                 <- Clock[F].realTime
      connectionError     <- Ref.of((now, true))
      histDataFarmError   <- Ref.of((now, true))
      marketDataFarmError <- Ref.of((now, true))
    } yield new IbConnectionTracker(
      streamTimeout       = streamTimeout,
      connectionError     = connectionError,
      histDataFarmError   = histDataFarmError,
      marketDataFarmError = marketDataFarmError
    )
}
