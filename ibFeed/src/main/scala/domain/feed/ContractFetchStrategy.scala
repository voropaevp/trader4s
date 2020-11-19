package domain.feed

import cats.Monad
import cats.data.{EitherT, Kleisli}
import cats.effect.Async
import cats.syntax.all._
import cats.implicits._
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import model.datastax.ib.feed.request.RequestContract
import model.datastax.ib.feed.response.contract.Contract
import utils.config.Config.ContractEntry

object ContractFetchStrategy {
  def run[F[_]: Async](
    entry: ContractEntry
  )(implicit contractDao: ContractDaoConnected[F]): F[Unit] =


  def tryGetContract[F[_]: Async](
    implicit contractDao: ContractDaoConnected[F]
  ): Kleisli[F, ContractEntry, Option[Contract]] = Kleisli { entry =>
    contractDao.getByProps(entry).map(_.map(_.contId)).flatMap{
      case Some(value) => contractDao.get(value)
      case None => Async[F].pure(None)
    }
  }

  def tryGetExistingRequest[F[_]: Async]
  (
    implicit requestDao: RequestDaoConnected[F]
  ): Kleisli[F, ContractEntry, Option[RequestContract]] = Kleisli { entry =>
   requestDao.getByProp()
  }

  def makeFeedRequest[F[_]: Async: ContractDaoConnected]: Kleisli[F, RequestContract, Either[FeedError, Contract]] =
    Kleisli { partCont =>
    }

}
