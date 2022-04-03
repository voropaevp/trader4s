import cats.effect.{IO, IOApp}
import db.ConnectedDao
import domain.feed.{ContractFetchStrategy, FeedAlgebra}
import interpreter.ib.feed.IbFeed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {

  implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def run: IO[Unit] =
    config.parse[IO].flatMap { appConfig =>
      ConnectedDao.init[IO].use { db =>
        implicit val (contractDaoConnected, requestDaoConnected, barDaoConnected) = db
        IbFeed[IO](appConfig.broker).use { ibFeed =>
          implicit val feed: FeedAlgebra[IO] = ibFeed
          ContractFetchStrategy.run[IO](appConfig.watchList.map(_.contractEntry).head) >>
          IO.println("sss")
        }
      }
    }
}
