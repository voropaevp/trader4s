package db

import cats.syntax.all._
import cats.effect.Resource
import cats.effect.kernel.Sync
import com.datastax.oss.driver.api.core.{CqlSession, DefaultConsistencyLevel}
import org.cognitor.cassandra.migration.{Database, MigrationRepository, MigrationTask}
import model.datastax.ib.feed.codec.CqlStringToAstCodec

object DbSession {
  private[db] def runMigration[F[_]: Sync](keyspace: String, keyspacePrefix: String): F[CqlSession] =
    makeMigrationSession.use { session =>
      for {
        _ <- Sync[F].delay(
          // Bind variables cannot be used for keyspace names
          session.execute(
            s"""CREATE KEYSPACE IF NOT EXISTS $keyspacePrefix$keyspace
               | WITH REPLICATION =
               | {
               |  'class' : 'SimpleStrategy',
               |  'replication_factor' : 1
               |  }""".stripMargin
          )
        )
        database <- Sync[F].delay(
          new Database(session, s"$keyspacePrefix$keyspace").setConsistencyLevel(DefaultConsistencyLevel.QUORUM)
        )
        migration <- Sync[F].delay(
          new MigrationTask(database, new MigrationRepository(s"/cassandra/migration/$keyspace"))
        )
        _ <- Sync[F].delay(migration.migrate())
        migratedSession <- Sync[F].delay(
          CqlSession
            .builder
            .addTypeCodecs(CqlStringToAstCodec.astEncoders: _*)
            .withKeyspace(s"$keyspacePrefix$keyspace")
            .build
        )
      } yield migratedSession
    }

  private[db] def makeMigrationSession[F[_]: Sync]: Resource[F, CqlSession] =
    Resource.make(Sync[F].delay(CqlSession.builder.build))(session => Sync[F].delay(session.close()))

  def makeSession[F[_]: Sync](keyspace: String, keyspacePrefix: String = ""): Resource[F, CqlSession] =
    Resource.make(runMigration(keyspace, keyspacePrefix))(session => Sync[F].delay(session.close()))

}
