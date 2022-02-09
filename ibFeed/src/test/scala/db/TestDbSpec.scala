package db

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import db.DbSession.{makeMigrationSession, makeSession}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers

class TestDbSpec extends AnyFreeSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers {

  val testSchemaPrefix = "test_"

  def flushKeyspace(keyspace: String): Unit =
    makeMigrationSession[IO]
      .use(session => IO(session.execute(s"""DROP KEYSPACE IF EXISTS $testSchemaPrefix$keyspace""")).void)
      .void
      .unsafeRunSync()

  def getFormattedContents(keyspace: String, query: String): String =
    makeSession[IO](keyspace, testSchemaPrefix)
      .use(session => IO(session.execute(query)).map(_.iterator().next().getFormattedContents))
      .unsafeRunSync()

}
