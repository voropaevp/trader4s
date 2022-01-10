package domain.feed

import cats.{Applicative, Monad}
import cats.data.{EitherT, Kleisli}
import cats.effect.Async
import cats.syntax.all._
import cats.implicits._
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import model.datastax.ib.feed.ast.{DataType, RequestType}
import model.datastax.ib.feed.request.RequestContract
import model.datastax.ib.feed.response.contract.Contract
import utils.config.Config.ContractEntry
import fs2._

object ContractFetchStrategy {
  def run[F[_]: Async: ContractDaoConnected](
    entries: List[ContractEntry]
  ) = Stream.emits(entries).evalMap()

  def tryGetContract[F[_]: Monad: ContractDaoConnected]: Kleisli[F, ContractEntry, Option[Contract]] =
    Kleisli { entry =>
      ContractDaoConnected[F].getByProps(entry).map(_.map(_.contId)).flatMap {
        case Some(value) => ContractDaoConnected[F].get(value)
        case None        => Applicative[F].pure(None)
      }
    }

  def tryGetExistingRequest[F[_]: Monad: RequestDaoConnected]: Kleisli[F, ContractEntry, Option[RequestContract]] =
    Kleisli { entry =>
      RequestDaoConnected[F]
        .getByContProp(
          entry.symbol,
          entry.secType,
          entry.exchange,
          entry.strike,
          entry.right,
          entry.multiplier,
          entry.currency,
          entry.localSymbol,
          entry.primaryExch,
          None,
          entry.secIdType,
          entry.secId,
          None,
          entry.marketName
        )
        .flatMap {
          case Some(props) => RequestDaoConnected[F].getReqContById(props.reqId)
          case None        => Monad[F].pure(None)
        }

    }

  def makeFeedRequest[F[_]: Monad: Applicative: RequestDaoConnected: FeedAlgebra](
    partCont: ContractEntry
  ): EitherT[Stream[F, *], FeedError, Contract] = {
    val contractReq: RequestContract = partCont.toContractRequest

    EitherT.rightT[F, FeedError](RequestDaoConnected[F].createContract(contractReq).pure[F]) *>
        FeedAlgebra[F].requestContractDetails(contractReq).map(_.stream)
  }

}
