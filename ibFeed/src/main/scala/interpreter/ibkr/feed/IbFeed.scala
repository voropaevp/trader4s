package interpreter.ibkr.feed

import cats.data.EitherT
import cats.syntax.all._
import cats.effect.{Async, Resource, Sync, Temporal}
import cats.effect.kernel.Clock
import cats.effect.std.Dispatcher
import cats.effect.implicits._
import cats.~>
import domain.feed.{FeedAlgebra, FeedException, FeedRequestService, QueuedFeedRequest}
import interpreter.ibkr.feed.components.IbkrFeedWrapper
import utils.config.Config.BrokerSettings
import com.ib.client.{EClientSocket, EJavaSignal, EReader}
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import domain.feed.FeedException.{IbError, RequestTypeException}
import model.datastax.ib.feed.ast.{RequestState, RequestType}
import fs2._
import model.datastax.ib.feed.request.{RequestContract, RequestData}
import model.datastax.ib.feed.response.contract.Contract
import model.datastax.ib.feed.response.data.Bar
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}

import scala.concurrent.ExecutionContext

class IbFeed[F[_]: Async: FeedRequestService: RequestDaoConnected: ContractDaoConnected] extends FeedAlgebra[F] {

//  private val ibFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")
//
//  private def ibDuration(startTime: Instant, endTime: Instant): String = {
//    val dur         = Duration.between(startTime, endTime)
//    val secs: Int   = dur.getSeconds.toInt
//    val days: Int   = secs % 86400
//    val weeks: Int  = days % 7
//    val months: Int = days % 30
//    val years: Int  = days % 365
//    if (days == 0) {
//      s"$secs S"
//    } else if (weeks == 0) {
//      s"$days D"
//    } else if (months == 0) {
//      s"$weeks W"
//    } else if (years == 0) {
//      s"$months M"
//    } else {
//      s"$years Y"
//    }
//  }

  val mapKStream: F ~> Stream[F, *] = Lambda[ F ~> Stream[F, *] ](Stream.eval)

  implicit def unsafeLogger[G[_]: Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  def retry

  override def requestContractDetails(request: RequestContract): EitherT[F, FeedException, Stream[F, Contract]] =
    FeedRequestService[F].register(request).map(_.stream.map(_.asInstanceOf[Contract]))

  override def requestHistBarData(request: RequestData): EitherT[F, FeedException, Stream[F, Bar]] =
    EitherT[F, FeedException, RequestData](
      Async[F].delay(
        Either.cond(
          request.requestType == RequestType.Historic,
          RequestTypeException(s"Wrong request type ${request.requestType} for historic data endpoint"),
          request
        )
      )
    ) *>
      FeedRequestService[F].register(request).map(_.stream.map(_.asInstanceOf[Bar]))

  override def subscribeBarData(request: RequestData): EitherT[F, FeedException, Stream[F, Bar]] =
    EitherT[F, FeedException, RequestData](
      Async[F].delay(
        Either.cond(
          request.requestType == RequestType.Subscription,
          RequestTypeException(s"Wrong request type ${request.requestType} for subscription data endpoint"),
          request
        )
      )
    ) *>
      FeedRequestService[F].register(request).map(_.stream.map(_.asInstanceOf[Bar]))

}

object IbkrFeed {
  def apply[F[_]: Async: Clock: Temporal](
    settings: BrokerSettings,
    readerEc: ExecutionContext,
    clientId: Int
  )(
    implicit reqDao: RequestDaoConnected[F],
    contDao: ContractDaoConnected[F],
    logger: Logger[F],
    sf: Sync[F]
  ): Resource[F, IbFeed[F]] =
    for {
      dsp                                              <- Dispatcher[F]
      implicit0(feedReqService: FeedRequestService[F]) <- FeedRequestService.make[F](settings.requestTimeout)
      eWrapper                                         <- IbkrFeedWrapper[F](feedReqService)
      ibkrPieces <- Resource.eval(
        logger.info("Making ibkr pieces") *>
          sf.delay {
            val readerSignal = new EJavaSignal()
            val client       = new EClientSocket(eWrapper, readerSignal)
            // It is important that the main EReader object is not created until after a connection has
            // been established. The initial connection results in a negotiated common version between TWS and
            // the API client which will be needed by the EReader thread in interpreting subsequent messages.
            client.eConnect(settings.ip, settings.port, settings.clientId)
            val reader = new EReader(client, readerSignal)
            (client, reader, readerSignal)
          }
      )
      (client, reader, readerSignal) = ibkrPieces
      _ <- Resource.eval(sf.delay {
        reader.start()
      } *> logger.info(s"${client.isConnected}"))
      _ <- (for {
        hasError  <- feedReqService.stoppedByError
        connected <- sf.delay(client.isConnected)
        optError <- sf.blocking {
          if (connected & !hasError) {
            readerSignal.waitForSignal()
            reader.processMsgs()
            None
          } else {
            if (hasError) {
              dsp.unsafeRunAndForget(logger.info(s"$connected $hasError"))
              Some(())
            } else {
              None
            }

          }
        }
        _ <- if (optError.isDefined) {
          feedReqService.failAll(IbError("Main feed loop stopped "))
          logger.error(s"Main feed loop stopped [$connected]")
        } else {
          None.pure[F]
        }
      } yield optError).untilDefinedM.background

      r <- Resource.make(
        logger.info("Made the IbkrFeed service") *> sf.delay(new IbFeed[F])
      )(_ =>
        logger.info("Shutting down the IbkrFeed service") *>
          sf.delay {
            client.eDisconnect()
            //          readerSignal.issueSignal()
          }
      )
    } yield r
}
