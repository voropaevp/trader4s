package db.model

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import db.ConnectedDao.BarDaoConnected
import db.DbSession.makeSession
import db.TestDbSpec
import model.datastax.ib.feed.ast.BarSize
import model.datastax.ib.feed.response.data.Bar

import java.util.UUID

class BarSpec extends TestDbSpec {
  val keyspace = "data"

  "Bar cache" - {
    "Create the data keyspace" in {
      getFormattedContents(
        keyspace,
        s"""SELECT
           | keyspace_name
           |  FROM system_schema.keyspaces 
           |  WHERE keyspace_name = '$testSchemaPrefix$keyspace'
           |  """.stripMargin
      ) === s"""[keyspace_name:'$testSchemaPrefix$keyspace']"""
    }

    "Be able to insert row" in {
      runWithTestDao(
        BarDaoConnected[IO].write(Bar(
          contId   = 1,
          size     = BarSize.Day1,
          dataType = dataType,
          ts       = Instant.from(dateParser.parse(ibBar.time())),
          open     = ibBar.open(),
          high     = ibBar.high(),
          low      = ibBar.low(),
          close    = ibBar.close(),
          volume   = ibBar.volume(),
          count    = Some(ibBar.count()),
          wap      = Some(ibBar.wap()),
          extra    = None
        ))
      )

      getFormattedContents(
        keyspace,
        s"""SELECT
           | keyspace_name
           |  FROM system_schema.keyspaces
           |  WHERE keyspace_name = '$testSchemaPrefix$keyspace'
           |  """.stripMargin
      ) === s"""[keyspace_name:'$testSchemaPrefix$keyspace']"""
    }
  }
}
