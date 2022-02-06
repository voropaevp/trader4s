package db

import cats.effect._
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.unsafe.implicits.global
import db.DbSession._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._

class DbMigrationSpec
    extends AsyncFreeSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
//    with AsyncIOSpec
    with Matchers {
  import DbMigrationSpec._

  override def beforeEach(): Unit =
    makeMigrationSession[IO].use(session =>
        IO(session.execute(s"""DROP KEYSPACE IF EXISTS $keyspace""")).void).void.unsafeRunSync()


  override def afterAll(): Unit =
    makeMigrationSession[IO].use(session =>
      IO(session.execute(s"""DROP KEYSPACE IF EXISTS $keyspace""")).void).void.unsafeRunSync()

  "Migration should " - {

    "Create the keyspaces" in {
      makeSession[IO](keyspace).use(session =>
        IO(session.execute(s"""SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = '$keyspace'"""))
          .map(_.iterator().next().getFormattedContents)).unsafeRunSync() shouldBe s"""[keyspace_name:'$keyspace']"""
    }

//    "run the migrations" in {
//
//    }
  }
}
object DbMigrationSpec {
  val keyspace = "testkeyspace"
}
