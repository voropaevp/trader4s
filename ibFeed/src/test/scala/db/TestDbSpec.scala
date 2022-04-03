package db

import cats.effect.IO
import db.DbSession.{makeMigrationSession, makeSession}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import model.datastax.ib.feed.ast._
import model.datastax.ib.feed.response.contract._
import model.datastax.ib.feed.request._

import java.time.Instant
import java.util.{Optional, List => JList}

class TestDbSpec extends AnyFreeSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers {

  def flushKeyspace(prefix: String, keyspace: String): IO[Unit] =
    makeMigrationSession[IO].use(session => IO(session.execute(s"""DROP KEYSPACE IF EXISTS $prefix$keyspace""")).void)

  def getFormattedContents(prefix: String, keyspace: String, query: String): IO[String] =
    makeSession[IO](keyspace, prefix).use(session =>
      IO(session.execute(query)).map(_.iterator().next().getFormattedContents)
    )

}
