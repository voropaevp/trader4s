package db

import cats.effect.IO
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

  def runWithTestDao[T](ev: IO[T]): T =
    ConnectedDao
      .initWithPrefix[IO](testSchemaPrefix)
      .use {
        case (a, b, c) =>
          implicit val _a: ConnectedDao.ContractDaoConnected[IO] = a
          implicit val _b: ConnectedDao.RequestDaoConnected[IO]  = b
          implicit val _c: ConnectedDao.BarDaoConnected[IO]      = c
          ev
      }
      .unsafeRunSync()
}
