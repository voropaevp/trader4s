import cats.effect.{IO, IOApp}
import interpreter.ibkr.feed.IbkrFeed
import utils.config.BrokerSettings

import dummiesx._

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

import cats.effect.Sync
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._

object  main extends IOApp.Simple {
  implicit def unsafeLogger[G[_] : Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  val settings: BrokerSettings = BrokerSettings("127.0.0.1", 4002, 60.seconds, 1)

  val run: IO[Unit] = {
    val feed = IbkrFeed[IO](settings, ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2)), 1)
    feed.use {f =>
      f.runRequest(rangeRequest).flatMap{
        case Left(_) => IO(())
        case Right(fr) => fr.stream.evalMap(el => IO.print(el)).compile.drain
      }
    }
  }
}

