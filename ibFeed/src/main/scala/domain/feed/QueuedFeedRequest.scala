package domain.feed

import cats.effect.kernel.Clock
import cats.effect.{Async, Deferred, Ref, Sync}
import cats.effect.std.Queue
import fs2.Stream
import cats.syntax.all._
import cats.derived.cached.show._
import model.datastax.ib.feed.request.Request
import model.datastax.ib.feed.response.Response
import org.typelevel.log4cats.Logger
import utils.log.{printWrite, stringWriter}

import scala.concurrent.duration.FiniteDuration

sealed case class QueuedFeedRequest[F[_]: Logger: Clock: Sync](
  id: Int,
  request: Request,
  stream: Stream[F, Response],
  private val queue: Queue[F, Option[Response]],
  outcome: Deferred[F, RequestOutcome],
  updateTime: Ref[F, FiniteDuration],
  createTime: FiniteDuration
) {
  def close(): F[Unit] =
    for {
      _ <- outcome.complete(RequestSuccess)
      _ <- queue.offer(None)
      _ <- Logger[F].info(s"Closed request $id $request")
    } yield ()

  def fail(ex: GenericError): F[Unit] =
    for {
      _ <- outcome.complete(RequestError(ex))
      _ <- queue.offer(None)
      _ <- ex.printStackTrace(printWrite).pure[F]
      _ <- Logger[F].error(s"Failed request with ${stringWriter} $id $request ")
    } yield ()

  def cancel(): F[Unit] =
    for {
      _ <- outcome.complete(RequestCancel)
      _ <- queue.offer(None)
      _ <- Logger[F].warn(s"Closed request $id $request")
    } yield ()

  def enqueue(el: Response): F[Unit] = queue.offer(Some(el))

}

private object QueuedFeedRequest {
  def apply[F[_]: Async: Clock: Logger](id: Int, request: Request): F[QueuedFeedRequest[F]] =
    for {
      q          <- Queue.unbounded[F, Option[Response]]
      id         <- id.pure[F]
      req        <- request.pure[F]
      createTime <- Clock[F].realTime
      updTime    <- Ref[F].of(createTime)
      o          <- Deferred[F, RequestOutcome]
      st = Stream.fromQueueNoneTerminated(q)
    } yield new QueuedFeedRequest(id, req, st, q, o, updTime, createTime)

}

sealed trait RequestOutcome

case object RequestSuccess extends RequestOutcome

case class RequestError(ex: GenericError) extends RequestOutcome

case object RequestCancel extends RequestOutcome
