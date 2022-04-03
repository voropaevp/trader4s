package mocks

import cats.effect.IO
import org.typelevel.log4cats.Logger

object noopLogger {
  implicit val noopLogger: Logger[IO] = new Logger[IO]{
    override def error(message: => String): IO[Unit] = IO.unit

    override def warn(message: => String): IO[Unit] = IO.unit

    override def info(message: => String): IO[Unit] = IO.unit

    override def debug(message: => String): IO[Unit] = IO.unit

    override def trace(message: => String): IO[Unit] = IO.unit

    override def error(t: Throwable)(message: => String): IO[Unit] = IO.unit

    override def warn(t: Throwable)(message: => String): IO[Unit] = IO.unit

    override def info(t: Throwable)(message: => String): IO[Unit] = IO.unit

    override def debug(t: Throwable)(message: => String): IO[Unit] = IO.unit

    override def trace(t: Throwable)(message: => String): IO[Unit] = IO.unit
  }
}
