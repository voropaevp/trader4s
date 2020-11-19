package interpreter.ibkr.feed

import cats.effect.{IO, Resource, Sync}
import utils.config.BrokerSettings

import java.net.InetAddress
import domain.model.MarketData
import dummiesx._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import utils.ExecutionContexts
import java.util.concurrent.{Executors, ExecutorService}
import scala.concurrent.ExecutionContext
import utils.log._
import java.util.concurrent.Executors
import scala.concurrent.duration._

class FeedTest[F] extends AsyncFlatSpec with AsyncIOSpec {
  val settings: BrokerSettings = BrokerSettings("127.0.0.1",4002, 10.seconds, 1)


//  "Feed " should " be able to process the range request" in {
//    val feed = IbkrFeed[IO](settings, ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2)),1)
//    feed.use {
//      f =>
//        for {
//          _ <- IO.sleep(10.seconds)
//          qreq <- f.runRequest(rangeRequest)
//        } yield qreq.stream
//    }
//  }.asserting(z => z.compile.toVector shouldBe Vector.empty[MarketData])
}
