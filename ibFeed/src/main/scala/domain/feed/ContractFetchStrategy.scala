package domain.feed

import cats.syntax.all._
import cats.effect.{Async, Sync}
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import model.datastax.ib.feed.request.RequestContract
import model.datastax.ib.feed.response.contract.ContractEntry
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}

object ContractFetchStrategy {
  implicit def unsafeLogger[G[_]: Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  def run[F[_]: Async: ContractDaoConnected: RequestDaoConnected: FeedAlgebra](
    entry: ContractEntry
  ): F[Unit] = ContractDaoConnected[F].getByContractEntry(entry).flatMap {
    case Some(contract) => Logger[F].info(contract.toString)
    case None           => FeedAlgebra[F].requestContractDetails(RequestContract(entry)).compile.drain
  }
}
