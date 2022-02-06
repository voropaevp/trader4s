package db

import cats.effect._
import cats.effect.unsafe.implicits.global
import db.DbSession._

class DbMigrationSpec extends TestDbSpec {

  val keyspace                    = "test"
  override def beforeAll(): Unit  = flushKeyspace(keyspace)
  override def afterAll(): Unit   = flushKeyspace(keyspace)
  override def beforeEach(): Unit = flushKeyspace(keyspace)

  "Migration should " - {

    "Create the keyspace" in {
      getFormattedContents(
        keyspace,
        s"""SELECT
          |keyspace_name
          |FROM system_schema.keyspaces
          |WHERE keyspace_name = '$testSchemaPrefix$keyspace'
          |""".stripMargin
      ) === s"""[keyspace_name:'$testSchemaPrefix$keyspace']"""
    }

    "run the migrations" in {
      getFormattedContents(keyspace, """SELECT test_id FROM test WHERE test_id = 1""") === "[test_id:1]"
    }
  }
}
