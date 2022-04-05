package domain.feed

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import cats.effect.{IO, Resource}
import mocks.RequestMocks
import cats.syntax.all._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

class LimitsSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with RequestMocks {
  import mocks.noopLogger._

  "Historic limits should " - {

    val limConfig: config.LimitsConfig = config.LimitsConfig(
      concurrentSubLimit          = 100,
      clientMsgLimit              = 100,
      hist10MinLimit              = 12,
      sameContractAndSizeLimit    = 100,
      clientMsgDuration           = 100.millis,
      hist10MinDuration           = 200.millis,
      sameContractAndSizeDuration = 300.millis
    )

    val limits: Resource[IO, Limits[IO]] = Limits.make[IO](limConfig)

    "Allow 2x limit number of requests after initial limit is reached and expired with 2x duration" in {
      limits
        .use { lim =>
          (1 until limConfig.hist10MinLimit * 2)
            .scanLeft(mockDataReqHist)((cont, id) => cont.copy(contId = id))
            .toList
            .traverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.hist10MinDuration * 2 + 300.millis)
        }
        .asserting(_ shouldBe limConfig.hist10MinLimit * 2)
    }

    "Timeout 2x limit number of requests after 1x duration" in {
      limits
        .use { lim =>
          (1 until limConfig.hist10MinLimit * 2)
            .scanLeft(mockDataReqHist)((cont, id) => cont.copy(contId = id))
            .toList
            .traverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.hist10MinDuration + 300.millis)
            .handleError{
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe "Timeout")
    }
  }

  "Concurrent Sub limits should " - {

    val limConfig: config.LimitsConfig = config.LimitsConfig(
      concurrentSubLimit          = 10,
      clientMsgLimit              = 100,
      hist10MinLimit              = 100,
      sameContractAndSizeLimit    = 100,
      clientMsgDuration           = 100.millis,
      hist10MinDuration           = 200.millis,
      sameContractAndSizeDuration = 300.millis
    )

    val limits: Resource[IO, Limits[IO]] = Limits.make[IO](limConfig)

    "Allow 2x limit number of requests after initial limit is reached and expired with 2x duration" in {
      limits
        .use { lim =>
          (1 until limConfig.hist10MinLimit * 2)
            .scanLeft(mockDataReqSub)((cont, id) => cont.copy(contId = id))
            .toList
            .traverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.hist10MinDuration * 2 + 300.millis)
        }
        .asserting(_ shouldBe limConfig.hist10MinLimit * 2)
    }

    "Timeout 2x limit number of requests after 1x duration" in {
      limits
        .use { lim =>
          (1 until limConfig.hist10MinLimit * 2)
            .scanLeft(mockDataReqHist)((cont, id) => cont.copy(contId = id))
            .toList
            .traverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.hist10MinDuration + 300.millis)
            .handleError{
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe "Timeout")
    }
  }
}
