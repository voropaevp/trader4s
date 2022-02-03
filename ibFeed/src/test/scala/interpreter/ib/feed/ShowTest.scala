package interpreter.ib.feed

import org.scalatest._
import org.scalatest.flatspec.AsyncFlatSpec
import matchers.should.Matchers._
import cats.derived.cached.show._
import dummiesx._
import utils.implicits._

trait IbkrFeedSpec extends AsyncFlatSpec
  with BeforeAndAfterAll


class ShowTest extends IbkrFeedSpec {

  "MdInstrument " should " be able to show " in assertCompiles("instrument.show")
}