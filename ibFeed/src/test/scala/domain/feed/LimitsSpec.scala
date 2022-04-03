package domain.feed

import cats.effect.{IO, Sync}
import mocks.RequestMocks
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.{SelfAwareLogger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

class LimitsSpec extends AnyFreeSpec with Matchers with RequestMocks {

  "Migration should " - {}
}

object LimitsSpec {
  val limConfig = config.LimitsConfig(
    clientMsgLimit           = 10,
    concurrentSubLimit       = 11,
    hist10MinLimit           = 12,
    sameContractAndSizeLimit = 13
  )

//  val limits = Limits.make[IO](limConfig)
}
