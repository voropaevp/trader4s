package domain.feed

import cats.{Applicative, Monad}
import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect.Async
import cats.syntax.all._
import cats.implicits._
import org.typelevel.log4cats.Logger
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import db.DbError
import model.datastax.ib.feed.request.RequestContract
import model.datastax.ib.feed.response.contract.Contract
import utils.config.Config.ContractEntry
import fs2._
import model.datastax.ib.feed.ast.RequestState

object ContractFetchStrategy {
  def run[F[_]: Async: ContractDaoConnected: RequestDaoConnected: FeedAlgebra](
    entries: List[ContractEntry]
  ): F[List[Either[FeedException, Stream[F, Contract]]]] = {
    val s = Stream
      .emits(entries)
      .parEvalMap[F, Option[Contract]](10)(tryGetContract[F])

    s
      .zip(Stream.emits(entries))
      .flatMap{
        case (maybeContract, entry) => maybeContract match {
          case Some(contract) => Stream.emit(contract.some)
          case None => Stream
            .eval(makeFeedRequest[F](entry).value.flatMap {
              case Left(ex) => ex match {
                case DbError(message, cause) =>
                case FeedException.IbError(message, cause) =>
                case _: FeedRequestService.LimitError =>
                case FeedException.RequestTypeException(message) =>
                case _ =>
              }
              case Right(value) =>

            })
            .flatMap {
              case Some(st) => st
              case None => Stream.empty[F, Contract]
            }
        }
        case _ => Stream.empty[F, Contract]
      }
  }


  def tryGetContract[F[_]: Monad: ContractDaoConnected](entry: ContractEntry): F[Option[Contract]] =
      ContractDaoConnected[F].getByProps(entry).map(_.map(_.contId)).flatMap {
        case Some(value) => ContractDaoConnected[F].get(value)
        case None        => Monad[F].pure(None)
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
          case Some(props) =>
            RequestDaoConnected[F].getReqContById(props.reqId)
              .flatMap {
                case Some(req) => OptionT.when(req.state == RequestState.,
                case None =>
              }

          case None        => Monad[F].pure(None)
        }

    }

  def makeFeedRequest[
    F[_]: Monad: Applicative: RequestDaoConnected: FeedAlgebra
  ](partCont: ContractEntry): EitherT[F, FeedException, Stream[F, Contract]] =
    FeedAlgebra[F].requestContractDetails(partCont.toContractRequest).value
      .flatMap {
        case Left(err) => err match {
          case DbError(message, cause) =>
          case FeedException.IbError(message, cause) =>
          case error: FeedRequestService.LimitError =>
          case FeedException.RequestTypeException(message) =>
          case _ =>
        }
        case Right(value) =>
      }

}
