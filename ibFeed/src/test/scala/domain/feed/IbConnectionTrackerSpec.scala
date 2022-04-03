package domain.feed
import cats.effect.IO
import fs2._
import cats.syntax.all._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import domain.feed.FeedException.IbReqError
import model.datastax.ib.feed.request.RequestData
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AsyncFreeSpec

import scala.concurrent.duration._
import mocks.RequestMocks

class IbConnectionTrackerSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with RequestMocks {
  import IbConnectionTrackerSpec._

  "IbConnectionTracker should throw exception after timeout" in {
    (
      ibConnectionTracker.flatMap(tracker =>
          tracker.resolveError(IbReqError(1102)) >>
          tracker.resolveError(IbReqError(2104)) >>
          tracker.resolveError(IbReqError(2107))
            parProduct
          Stream.emit(())
          .merge(Stream.sleep[IO](reqTimeout + 2000.millis))
            .zipWithIndex
            .map(_.asRight[FeedException])
            .take(5)
            .through(tracker.setTimeout(mockDataReqHist))
            .compile
            .toList
      ))
      .map(_._2)
      .map(arr => (arr.length,  arr.head, arr.last))
      .asserting(_ shouldBe (2, Right(((),0)), Left(FeedException.RequestTimeout(reqTimeout))))
  }

  "IbConnectionTracker should not timeout, when connection flicks back and forth" in {
    (
      ibConnectionTracker.flatMap(tracker =>
        IO.sleep(reqTimeout*2) >>
          tracker.resolveError(IbReqError(1102)) >>
          tracker.resolveError(IbReqError(2104)) >>
          tracker.resolveError(IbReqError(2107)) parProduct
        IO.sleep(reqTimeout*4) >>
          tracker.addError(IbReqError(1100)) parProduct
        IO.sleep(reqTimeout*6) >>
            tracker.addError(IbReqError(1100)) parProduct
          fs2
            .Stream
            .awakeEvery[IO](reqTimeout - 100.millis)
            .zipWithIndex
            .map(_.asRight[FeedException])
            .take(5)
            .through(tracker.setTimeout(mockDataReqHist))
            .compile
            .toList
      ))
      // feed connection is checked every 500ms
      .timeout(reqTimeout * 5  + 500.millis)
      .map(_._2)
      .map(arr => (arr.length, arr.forall(_.isRight)))
      .asserting(_ shouldBe (5, true))
  }

  "IbConnectionTracker should ignore timeout while feed is starting" in {
    (
        ibConnectionTracker.flatMap(tracker =>
          IO.sleep(reqTimeout*2) >>
            tracker.resolveError(IbReqError(1102)) >>
            tracker.resolveError(IbReqError(2104)) >>
            tracker.resolveError(IbReqError(2107)) parProduct
          fs2
            .Stream
            .awakeEvery[IO](reqTimeout - 100.millis)
            .zipWithIndex
            .map(_.asRight[FeedException])
            .take(5)
            .through(tracker.setTimeout(mockDataReqHist))
            .compile
            .toList
        ))
      // feed connection is checked every 500ms
      .timeout(reqTimeout * 5  + 500.millis)
      .map(_._2)
      .map(arr => (arr.length, arr.forall(_.isRight)))
      .asserting(_ shouldBe (5, true))
  }
}

object IbConnectionTrackerSpec {
  import mocks.noopLogger._

  val reqTimeout: FiniteDuration = 150.millis
  val ibConnectionTracker: IO[IbConnectionTracker[IO]] = IbConnectionTracker.build[IO](reqTimeout)
}
