package domain.feed

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import cats.effect.{IO, Resource}
import mocks.RequestMocks
import cats._
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import cats.effect.syntax.all._
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
      hist10MinDuration           = 500.millis,
      sameContractAndSizeDuration = 300.millis
    )

    val limits: Resource[IO, Limits[IO]] = Limits.make[IO](limConfig)

    "Allow 3x limit number of requests after initial limit is reached and expired with 2x duration" in {
      limits
        .use { lim =>
          (1 until limConfig.hist10MinLimit * 3)
            .scanLeft(mockDataReqHist)((cont, id) => cont.copy(contId = id))
            .toList
            .traverse(lim.acquire)
            .map(_.size)
            .timeout(2 * (limConfig.hist10MinDuration+ 200.millis))// double the refresh timeout
        }
        .asserting(_ shouldBe limConfig.hist10MinLimit * 3)
    }

    "Timeout 2x +1 limit number of requests after 1x duration" in {
      limits
        .use { lim =>
          (1 until limConfig.hist10MinLimit * 2 + 1)
            .scanLeft(mockDataReqHist)((cont, id) => cont.copy(contId = id))
            .toList
            .traverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.hist10MinDuration + 200.millis)
            .handleError {
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

    "Let 2x concurrent limit though if they release" in {
      limits
        .use { lim =>
          (1 until 2 * limConfig.concurrentSubLimit)
            .scanLeft(mockDataReqSub)((cont, id) => cont.copy(contId = id))
            .toList
            .traverseTap(_ => IO.sleep(100.millis))
            .flatMap(_.parTraverse(r => lim.acquire(r) >> IO.sleep(200.millis) >> lim.release(r)))
            .map(_.size)
            .timeout(limConfig.concurrentSubLimit * (100.millis + 200.millis + 200.millis))
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe 2 * limConfig.concurrentSubLimit)
    }

    "Timout 2x concurrent limit though if they don't release" in {
      limits
        .use { lim =>
          (1 until 2 * limConfig.concurrentSubLimit)
            .scanLeft(mockDataReqSub)((cont, id) => cont.copy(contId = id))
            .toList
            .traverseTap(_ => IO.sleep(100.millis))
            .flatMap(_.parTraverse(r => lim.acquire(r) >> IO.sleep(200.millis)))
            .map(_.size)
            .timeout(2 * limConfig.concurrentSubLimit * 300.millis + 200.millis)
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe "Timeout")
    }
  }

  "Client Msg Limit should" - {

    val limConfig: config.LimitsConfig = config.LimitsConfig(
      concurrentSubLimit          = 10000,
      clientMsgLimit              = 10,
      hist10MinLimit              = 10000,
      sameContractAndSizeLimit    = 10000,
      clientMsgDuration           = 500.millis,
      hist10MinDuration           = 100.millis,
      sameContractAndSizeDuration = 100.millis
    )

    val limits: Resource[IO, Limits[IO]] = Limits.make[IO](limConfig)

    "Let 40x concurrent limit though after the 39x clientMsgDuration" in {
      limits
        .use { lim =>
          (1 until 40 * limConfig.clientMsgLimit)
            .scanLeft(mockDataReqSub)((cont, id) => cont.copy(contId = id))
            .toList
            .parTraverse(lim.acquire)
            .map(_.size)
            .timeout(39 * (limConfig.clientMsgDuration + 200.millis))
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe 40 * limConfig.clientMsgLimit)
    }

    "Timeout 3x concurrent limit though after the 1x clientMsgDuration" in {
      limits
        .use { lim =>
          (1 until 3 * limConfig.clientMsgLimit)
            .scanLeft(mockDataReqSub)((cont, id) => cont.copy(contId = id))
            .toList
            .parTraverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.clientMsgDuration)
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe "Timeout")
    }
  }

  "Same contract and size limit" - {

    val limConfig: config.LimitsConfig = config.LimitsConfig(
      concurrentSubLimit          = 100,
      clientMsgLimit              = 100,
      hist10MinLimit              = 100,
      sameContractAndSizeLimit    = 10,
      clientMsgDuration           = 10000.millis,
      hist10MinDuration           = 10000.millis,
      sameContractAndSizeDuration = 200.millis
    )

    val limits: Resource[IO, Limits[IO]] = Limits.make[IO](limConfig)

    "Let 2x concurrent limit though after the 1x sameContractAndSizeDuration" in {
      limits
        .use { lim =>
            List.fill(2*limConfig.sameContractAndSizeLimit)(mockDataReqHist)
            .parTraverse(lim.acquire)
            .map(_.size)
            .timeout(limConfig.sameContractAndSizeDuration + 100.millis)
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe 2 * limConfig.sameContractAndSizeLimit)
    }

    "Let 10x concurrent limit though after the 9x sameContractAndSizeDuration" in {
      limits
        .use { lim =>
          List.fill(10*limConfig.sameContractAndSizeLimit)(mockDataReqHist)
            .parTraverse(lim.acquire)
            .map(_.size)
            .timeout(9*limConfig.sameContractAndSizeDuration + 100.millis)
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe 10 * limConfig.sameContractAndSizeLimit)
    }

    "Timeout 10x concurrent limit should timeout the 8x sameContractAndSizeDuration" in {
      limits
        .use { lim =>
          List.fill(10*limConfig.sameContractAndSizeLimit)(mockDataReqHist)
            .parTraverse(lim.acquire)
            .map(_.size)
            .timeout(7*limConfig.sameContractAndSizeDuration + 100.millis)
            .handleError {
              case _: TimeoutException => "Timeout"
            }
        }
        .asserting(_ shouldBe "Timeout")
    }
  }
}
