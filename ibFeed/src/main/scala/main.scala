import cats.effect.{IO, IOApp}
import db.ConnectedDao
import domain.feed.{ContractFetchStrategy, FeedAlgebra}
import interpreter.ib.feed.IbFeed

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object main extends IOApp.Simple {

  val settings: config.AppSettings = config.parse

  def run: IO[Unit] =
    ConnectedDao.init[IO].use { db =>
      implicit val (contractDaoConnected, requestDaoConnected, barDaoConnected) = db
      IbFeed[IO](settings, ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2)), 1).use { f =>
        implicit val feed: FeedAlgebra[IO] = f
        ContractFetchStrategy.run[IO](settings.watchList.map(_.contractEntry).head)
      }
    }

}
