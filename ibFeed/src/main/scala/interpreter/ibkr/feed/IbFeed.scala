package interpreter.ibkr.feed

import cats.data.{EitherT, Kleisli}
import cats.syntax.all._
import cats.effect.{Async, Resource, Sync, Temporal}
import cats.effect.kernel.Clock
import cats.effect.std.Dispatcher
import cats.effect.implicits._
import cats.~>
import domain.feed.{FeedAlgebra, FeedError, FeedRequestService, GenericError, QueuedFeedRequest}
import interpreter.ibkr.feed.components.IbkrFeedWrapper
import utils.config.Config.BrokerSettings
import com.ib.client.{EClientSocket, EJavaSignal, EReader}
import db.ConnectedDao.{ContractDaoConnected, RequestDaoConnected}
import model.datastax.ib.feed.ast.{RequestState, RequestType}
import fs2._
import model.datastax.ib.feed.request.{RequestContract, RequestData}
import model.datastax.ib.feed.response.contract.Contract
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}

import scala.concurrent.ExecutionContext
import java.time.{Duration, Instant}
import java.time.format.DateTimeFormatter

class IbFeed[F[_]: Async: FeedRequestService: RequestDaoConnected: ContractDaoConnected](
  client: EClientSocket
) extends FeedAlgebra[F] {

  //Making identical historical data request within 15 seconds.
  //Making six or more historical data request for the same Contract, Exchange and Tick Type within two seconds.
  //Making more than 60 request within any ten minute period.
  //Note that when BID_ASK historical data is requested, each request is counted twice.

  private val ibFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")

  private def ibDuration(startTime: Instant, endTime: Instant): String = {
    val dur         = Duration.between(startTime, endTime)
    val secs: Int   = dur.getSeconds.toInt
    val days: Int   = secs % 86400
    val weeks: Int  = days % 7
    val months: Int = days % 30
    val years: Int  = days % 365
    if (days == 0) {
      s"$secs S"
    } else if (weeks == 0) {
      s"$days D"
    } else if (months == 0) {
      s"$weeks W"
    } else if (years == 0) {
      s"$months M"
    } else {
      s"$years Y"
    }
  }

  implicit def unsafeLogger[G[_]: Sync]: SelfAwareStructuredLogger[G] = Slf4jLogger.getLogger[G]

  private def ddd(initFr: QueuedFeedRequest[F]): Kleisli[F, QueuedFeedRequest[F], Unit] = for {
    _ <- Kleisli.local(initFr)

    Kleisli{ fr =>

  } yield ()

  override def requestContractDetails(request: RequestContract): EitherT[F, FeedError, Stream[F, Contract]] =
  FeedRequestService[F]
    .register(request)
    .map(Kleisli.liftF( ))


//      .flatTap(s => RequestDaoConnected[F].changeStateContract(request, RequestState.InQueue, None))
//      .flatMap(s => s)
//      .flatMap { fr =>
//      EitherT.right(Sync[F].delay(client.reqContractDetails(fr.id, request.toIb)) >> Sync[F].pure(fr))
//    }


  override def requestHistBarData(request: RequestData): EitherT[F, FeedError, QueuedFeedRequest[F]] = {
    request.ensuring(_.reqType == RequestType.Historic)
    feedRequests.register(request).flatMap { fr =>
      EitherT.right(for {
        fr   <- Sync[F].pure(fr)
        _    <- requestDao.create(request)
        cont <- contractDao.get(request.contId)
        _ <- Sync[F].delay(
          client.reqHistoricalData(
            fr.id,
            cont.ibContract,
            ibFormatter.format(request.endTime),
            ibDuration(request.startTime, request.endTime),
            request.size.toString,
            request.dataType.toString,
            1,
            1,
            false,
            null
          )
        )
        _ <- requestDao.changeState(request.reqId, RequestState.InQueue)
      } yield fr)
    }
  }

  override def subscribeBarData(request: RequestData): EitherT[F, FeedError, QueuedFeedRequest[F]] = {
    request.ensuring(_.reqType == RequestType.Subscription)
    feedRequests.register(request).flatMap { fr =>
      EitherT.right(for {
        fr   <- Sync[F].pure(fr)
        _    <- requestDao.create(request)
        cont <- contractDao.get(request.contId)
        _ <- Sync[F].delay(
          client.reqHistoricalData(
            fr.id,
            cont.ibContract,
            null,
            ibDuration(request.startTime, request.endTime),
            request.size.toString,
            request.dataType.toString,
            1,
            1,
            true,
            null
          )
        )
      } yield fr)
    }
  }
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
      dsp            <- Dispatcher[F]
      feedReqService <- FeedRequestService[F](settings.requestTimeout)
      eWrapper       <- IbkrFeedWrapper[F](feedReqService)
      ibkrPieces <- Resource.eval(
        logger.info("Making ibkr pieces") >>
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
      } >> logger.info(s"${client.isConnected}"))
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
          feedReqService.failAll(GenericError("Main feed loop stopped "))
          logger.error(s"Main feed loop stopped [$connected]")
        } else {
          None.pure[F]
        }
      } yield optError).untilDefinedM.background

      r <- Resource.make(
        logger.info("Made the IbkrFeed service") >> sf.delay(
          new IbFeed(feedReqService, client)
        )
      )(_ =>
        logger.info("Shutting down the IbkrFeed service") >>
          sf.delay {
            client.eDisconnect()
            //          readerSignal.issueSignal()
          }
      )
    } yield r
}
