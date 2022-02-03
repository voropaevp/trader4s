package domain.feed

import cats.syntax.all._
import cats.effect.{Async, Sync}
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import config.ContractEntry
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}

object ContractFetchStrategy {
  implicit def unsafeLogger[G[_]: Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  def run[F[_]: Async: ContractDaoConnected: RequestDaoConnected: FeedAlgebra](
    entry: ContractEntry
  ): F[Unit] = ContractDaoConnected[F].getByConfigEntry(entry).flatMap {
    case Right(contract) => Logger[F].info(contract.toString)
    case Left(entry)     => FeedAlgebra[F].requestContractDetails(entry.toContractRequest).compile.drain
  }
}
