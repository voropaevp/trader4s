package db.dao

import cats.effect.IO
import cats.syntax.traverse._
import cats.effect.unsafe.implicits.global
import db.ConnectedDao.BarDaoConnected
import db.{ConnectedDao, TestDbSpec}
import model.datastax.ib.feed.ast._
import model.datastax.ib.feed.response.data.Bar

import java.time.Instant

class BarDaoSpec extends TestDbSpec {

  import BarDaoSpec._

  "Create the data keyspace" in {
    (flushKeyspace("test_bar_dao_1_", "data") >>
      getFormattedContents(
        "test_bar_dao_1_",
        "data",
        s"""SELECT
           | keyspace_name
           |  FROM system_schema.keyspaces
           |  WHERE keyspace_name = 'test_bar_dao_1_data'
           |  """.stripMargin
      )).unsafeRunSync() shouldBe s"""[keyspace_name:'test_bar_dao_1_data']"""
  }

  "Be able to insert row" in {
    (flushKeyspace("test_bar_dao_2_", "data") >>
      ConnectedDao.initWithPrefix[IO]("test_bar_dao_2_").use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          BarDaoConnected[IO].write(mockBar) >>
            BarDaoConnected[IO]
              .get(1, BarSize.Day1, DataType.Bid, mockTs.minusSeconds(9999), mockTs.plusSeconds(1), 1)
              .compile
              .lastOrError
      }).unsafeRunSync() should ===(mockBar)
  }

  "Be able to insert more than one driver page rows" in {
    (flushKeyspace("test_bar_dao_3_", "data") >>
      ConnectedDao.initWithPrefix[IO]("test_bar_dao_3_").use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          List
            .fill(10000)(mockBar)
            .zipWithIndex
            .traverse {
              case (bar, i) =>
                BarDaoConnected[IO].write(bar.copy(ts = bar.ts.plusSeconds(i.toLong)))
            }
            .void >> BarDaoConnected[IO]
            .get(1, BarSize.Day1, DataType.Bid, mockTs, mockTs.plusSeconds(10001), 11000)
            .zipWithIndex
            .debug(o => s"a: $o")
            .compile
            .lastOrError
      }).unsafeRunSync() should ===(mockBar, 9999) // -1 because of element 0

  }
}

object BarDaoSpec {
  val mockTs: Instant = Instant.ofEpochMilli(1000000000)
  val mockBar: Bar = Bar(
    contId   = 1,
    size     = BarSize.Day1,
    dataType = DataType.Bid,
    ts       = mockTs,
    open     = 1L,
    high     = 2L,
    low      = 3L,
    close    = 4L,
    volume   = 5L,
    count    = 1,
    wap      = 3d,
    extra    = ""
  )
}
