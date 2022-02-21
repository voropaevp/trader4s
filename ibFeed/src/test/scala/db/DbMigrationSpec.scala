package db

import cats.effect._
import cats.effect.unsafe.implicits.global
import db.DbSession._

class DbMigrationSpec extends TestDbSpec {


  "Migration should " - {

    "Create the keyspace" in {
      (flushKeyspace("test_mig_1_", "test") >>
      getFormattedContents(
        "test_mig_1_",
        "test",
        s"""SELECT
          |keyspace_name
          |FROM system_schema.keyspaces
          |WHERE keyspace_name = 'test_mig_1_test'
          |""".stripMargin
      )).unsafeRunSync() shouldBe s"""[keyspace_name:'test_mig_1_test']"""
    }

    "run the migrations" in {
      (flushKeyspace("test_mig_1_", "test") >>
        getFormattedContents("test_mig_1_","test","""SELECT test_id FROM test WHERE test_id = 1""")
      ).unsafeRunSync()  shouldBe "[test_id:1]"
    }
  }
}
