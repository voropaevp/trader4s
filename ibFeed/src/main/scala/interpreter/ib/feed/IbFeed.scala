package interpreter.ib.feed

import fs2._
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.{Async, Resource, Sync, Temporal}
import cats.effect.kernel.Clock
import interpreter.ib.feed.components.IbFeedWrapper
import domain.feed.{FeedAlgebra, FeedRequestService}
import com.ib.client.{EClientSocket, EJavaSignal, EReader}
import config.BrokerSettings
import db.ConnectedDao.{BarDaoConnected, ContractDaoConnected, RequestDaoConnected}
import domain.feed.FeedException._
import model.datastax.ib.feed.ast.RequestType
import model.datastax.ib.feed.request.{RequestContract, RequestData}
import model.datastax.ib.feed.response.contract.Contract
import model.datastax.ib.feed.response.data.Bar
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import utils.ExecutionContexts
import scala.concurrent.duration.DurationInt

class IbFeed[F[_]: Async: FeedRequestService: RequestDaoConnected: ContractDaoConnected] extends FeedAlgebra[F] {

  override def requestContractDetails(request: RequestContract): Stream[F, Contract] =
    FeedRequestService[F].register(request).map(_.asInstanceOf[Contract])

  override def requestHistBarData(request: RequestData): Stream[F, Bar] =
    Stream.fromEither(
      Either.cond(
        request.requestType == RequestType.Historic,
        request,
        RequestTypeException(s"Wrong request type ${request.requestType} for historic data endpoint")
      )
    ) *>
      FeedRequestService[F].register(request).map(_.asInstanceOf[Bar])

  override def subscribeBarData(request: RequestData): Stream[F, Bar] =
    Stream.fromEither(
      Either.cond(
        request.requestType == RequestType.Subscription,
        request,
        RequestTypeException(s"Wrong request type ${request.requestType} for subscription data endpoint")
      )
    ) *>
      FeedRequestService[F].register(request).map(_.asInstanceOf[Bar])

}

object IbFeed {
  implicit def unsafeLogger[F[_]: Sync]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

  def apply[F[_]: Async: Clock: Temporal: RequestDaoConnected: ContractDaoConnected: BarDaoConnected](
    brokerSetting: BrokerSettings
  ): Resource[F, IbFeed[F]] =
    for {
      implicit0(feedReqService: FeedRequestService[F]) <- FeedRequestService.make[F](brokerSetting.limits)
      eWrapper                                         <- IbFeedWrapper[F](feedReqService)
      (client, reader, readerSignal) <- Resource.eval(
        Logger[F].info("Making ib pieces") *>
          Sync[F].delay {
            val readerSignal = new EJavaSignal()
            val client       = new EClientSocket(eWrapper, readerSignal)
            // It is important that the main EReader object is not created until after a connection has
            // been established. The initial connection results in a negotiated common version between TWS and
            // the API client which will be needed by the EReader thread in interpreting subsequent messages.
            client.eConnect(brokerSetting.ip, brokerSetting.port, brokerSetting.clientId)
            val reader = new EReader(client, readerSignal)
            // TODO move reader.start() to here
            (client, reader, readerSignal)
          }
      )
      fixedThreadPool <- ExecutionContexts.fixedThreadPool(brokerSetting.nThreads)
      _ <- Resource
        .eval(Sync[F].delay(reader.start()) *> Logger[F].info(s"${client.isConnected}"))
        .evalOn(fixedThreadPool)
      _ <- Sync[F]
        .blocking {
          if (client.isConnected) {
            readerSignal.waitForSignal()
            Either.catchNonFatal[Unit](reader.processMsgs())
          } else
            FeedShutdown.asLeft[Unit]
        }
        .flatMap {
          case Left(ex) if ex == FeedShutdown => Temporal[F].sleep(50.millis).as(Option.empty[Unit])
          case Left(ex)                       => Logger[F].error(s"Ibkr reader exception ${ex.getMessage}").as(Option[Unit](()))
          case Right(_)                       => Sync[F].pure(Option.empty[Unit])
        }
        .untilDefinedM
        .background
      r <- Resource.make(
        Logger[F].info("Made the IbkrFeed service") *> Sync[F].delay(new IbFeed[F])
      )(_ =>
        Logger[F].info("Shutting down the IbkrFeed service") *>
          Sync[F].delay {
            client.eDisconnect()
            //          readerSignal.issueSignal()
          }
      )
    } yield r
}
