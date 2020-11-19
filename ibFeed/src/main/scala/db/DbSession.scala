package db

import cats.syntax.all._
import cats.effect.Resource
import cats.effect.kernel.Sync
import com.datastax.oss.driver.api.core.{CqlSession, DefaultConsistencyLevel}
import org.cognitor.cassandra.migration.{Database, MigrationRepository, MigrationTask}

object DbSession {
  private def runMigration[F[_]: Sync](keyspace: String): F[Unit] = makeMigrationSession.use { session =>
    for {
      _ <- Sync[F].delay(
        session.execute(
          """
            CREATE KEYSPACE IF NOT EXISTS :keyspace
            WITH REPLICATION =
            {
             'class' : 'SimpleStrategy',
             'replication_factor' : 1
             }""".stripMargin,
          keyspace
        )
      )
      database  <- Sync[F].delay(new Database(session, keyspace).setConsistencyLevel(DefaultConsistencyLevel.ONE))
      migration <- Sync[F].delay(new MigrationTask(database, new MigrationRepository()))
      _         <- Sync[F].delay(migration.migrate())
    } yield ()
  }

  private def makeMigrationSession[F[_]: Sync]: Resource[F, CqlSession] =
    Resource.make { Sync[F].delay(CqlSession.builder.build) }(session => Sync[F].delay(session.close()))

  def makeSession[F[_]: Sync](keyspace: String): Resource[F, CqlSession] =
    Resource.make { runMigration(keyspace) >> Sync[F].delay(CqlSession.builder.withKeyspace(keyspace).build) }(
      session => Sync[F].delay(session.close())
    )

}
