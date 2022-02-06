package db

import cats.data.OptionT
import cats.syntax.all._
import cats.effect.{Async, Resource}
import fs2.{Chunk, Stream}
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import domain.feed.FeedException
import config.{ContractEntry, WatchEntry}
import model.datastax.ib.feed.ast._
import model.datastax.ib.feed.request._
import model.datastax.ib.feed.response.contract.{Contract, ContractByProps, ContractDao}
import model.datastax.ib.feed.response.data.{Bar, BarDao}
import model.datastax.ib.feed.{FeedMapper, FeedMapperBuilder}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import scala.jdk.CollectionConverters._

object ConnectedDao {

  trait BarDaoConnected[F[_]] {
    def write(bar: Bar): F[Unit]

    def headTs(contId: Int, size: BarSize, dataType: DataType): F[Instant]

    def tailTs(contId: Int, size: BarSize, dataType: DataType): F[Instant]
  }

  trait RequestDaoConnected[F[_]] {
    def changeState(
      id: UUID,
      newState: RequestState,
      rowsReceived: Option[Long]   = None,
      error: Option[FeedException] = None
    ): F[Unit]

    def getReqDataById(id: UUID): F[Option[RequestData]]

    def getReqContById(id: UUID): F[Option[RequestContract]]

    def getDataByProp(
      reqType: RequestType,
      dataType: DataType,
      state: RequestState,
      contId: Int,
      startTimeMin: Instant,
      startTimeMax: Instant
    ): Stream[F, UUID]

    def getByConfigEntry(entry: ContractEntry): F[Option[RequestContractByProps]] = getByContProp(
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

    def getByContProp(
      symbol: String,
      secType: SecurityType,
      exchange: Exchange,
      strike: Double = .0d,
      right: Option[String],
      multiplier: Option[String],
      currency: Option[String],
      localSymbol: Option[String],
      primaryExch: Option[Exchange],
      tradingClass: Option[String],
      secIdType: Option[String],
      secId: Option[String],
      comboLegsDescription: Option[String],
      marketName: Option[String],
      state: RequestState = RequestState.PendingId
    ): F[Option[RequestContractByProps]]

    def create(req: Request): F[Unit]

//    def createContract(contReq: RequestContract): F[Unit]
//
//    def createData(dataReq: RequestData): F[Unit]
  }

  trait ContractDaoConnected[F[_]] {
    def get(contId: Int): F[Option[Contract]]

    def getByConfigEntry(entry: ContractEntry): F[Either[ContractEntry, Contract]]

    def create(contract: Contract): F[Unit]

  }
  object ContractDaoConnected {
    def apply[F[_]](implicit ev: ContractDaoConnected[F]): ContractDaoConnected[F] = ev
  }

  object RequestDaoConnected {
    def apply[F[_]](implicit ev: RequestDaoConnected[F]): RequestDaoConnected[F] = ev
  }

  object BarDaoConnected {
    def apply[F[_]](implicit ev: BarDaoConnected[F]): BarDaoConnected[F] = ev
  }

  private def liftF[F[_]: Async, T](under: CompletionStage[T]): F[T] =
    Async[F].fromCompletableFuture(Async[F].delay(under.toCompletableFuture))

  private def page[F[_]: Async, T](it: MappedAsyncPagingIterable[T]): Stream[F, T] =
    Stream.unfoldChunkEval(it.hasMorePages)(rem =>
      if (rem)
        Async[F].delay[Option[(Chunk[T], Boolean)]] {
          Some((Chunk.iterable(it.currentPage().asScala), it.hasMorePages))
        } else
        Async[F].pure[Option[(Chunk[T], Boolean)]](None)
    )

  private def liftStream[F[_]: Async, T](under: CompletionStage[MappedAsyncPagingIterable[T]]): Stream[F, T] =
    Stream.eval(liftF(under)).flatMap(page[F, T])

  def init[F[_]: Async]: Resource[F, (ContractDaoConnected[F], RequestDaoConnected[F], BarDaoConnected[F])] =
    initWithPrefix("")

  private[db] def initWithPrefix[F[_]: Async](
    schemaPrefix: String
  ): Resource[F, (ContractDaoConnected[F], RequestDaoConnected[F], BarDaoConnected[F])] =
    DbSession.makeSession("meta", schemaPrefix).product(DbSession.makeSession("data", schemaPrefix)).map {
      case (metaSession, dataSession) =>
        lazy val mapperData: FeedMapper = new FeedMapperBuilder(dataSession).build()
        lazy val mapperMeta: FeedMapper = new FeedMapperBuilder(metaSession).build()

        lazy val barDao: BarDao           = mapperData.BarDao()
        lazy val requestDao: RequestDao   = mapperMeta.RequestDao()
        lazy val contractDao: ContractDao = mapperMeta.ContractDao()

        val barDaoConnected = new BarDaoConnected[F] {

          def write(bar: Bar): F[Unit] = liftF(barDao.create(bar))

          def headTs(contId: Int, size: BarSize, dataType: DataType): F[Instant] =
            liftF(barDao.headTs(contId, size, dataType))

          def tailTs(contId: Int, size: BarSize, dataType: DataType): F[Instant] =
            liftF(barDao.tailTs(contId, size, dataType))
        }

        val requestDaoConnected = new RequestDaoConnected[F] {
          def changeState(
            id: UUID,
            newState: RequestState,
            rowsReceived: Option[Long]   = None,
            error: Option[FeedException] = None
          ): F[Unit] =
            OptionT(getReqDataById(id))
              .flatMapF(req =>
                liftF(requestDao.changeStateData(req, newState, rowsReceived, error.map(_.toString))).as(().some)
              )
              .orElse(
                OptionT(getReqContById(id)).flatMapF(req =>
                  liftF(requestDao.changeStateContract(req, newState, error.map(_.getMessage))).as(().some)
                )
              )
              .getOrElseF(Async[F].raiseError(DbError("Changing state for missing data request")).void)

          def getReqDataById(id: UUID): F[Option[RequestData]] = liftF(requestDao.getDataById(id))

          def getReqContById(id: UUID): F[Option[RequestContract]] = liftF(requestDao.getContractReqById(id))

          def getDataByProp(
            reqType: RequestType,
            dataType: DataType,
            state: RequestState,
            contId: Int,
            startTimeMin: Instant,
            startTimeMax: Instant
          ): Stream[F, UUID] =
            liftStream(requestDao.getIdDataByStartRange(reqType, contId, dataType, state, startTimeMin, startTimeMax))

          def getByContProp(
            symbol: String,
            secType: SecurityType,
            exchange: Exchange,
            strike: Double = .0d,
            right: Option[String],
            multiplier: Option[String],
            currency: Option[String],
            localSymbol: Option[String],
            primaryExch: Option[Exchange],
            tradingClass: Option[String],
            secIdType: Option[String],
            secId: Option[String],
            comboLegsDescription: Option[String],
            marketName: Option[String],
            state: RequestState = RequestState.PendingId
          ): F[Option[RequestContractByProps]] = liftF(
            requestDao.getContractReqByProps(
              symbol,
              secType,
              exchange,
              strike,
              right,
              multiplier,
              currency,
              localSymbol,
              primaryExch,
              tradingClass,
              secIdType,
              secId,
              comboLegsDescription,
              marketName,
              state
            )
          )

          def create(req: Request): F[Unit] = req match {
            case r: RequestContract => liftF(requestDao.createContract(r))
            case r: RequestData     => liftF(requestDao.createData(r))
            case _                  => ().pure[F]
          }
        }

        val contractDaoConnected = new ContractDaoConnected[F] {
          override def get(contId: Int): F[Option[Contract]] = liftF(contractDao.get(contId))

          override def getByConfigEntry(e: ContractEntry): F[Either[ContractEntry, Contract]] =
            getByProps(
              e.exchange,
              e.symbol,
              e.secType,
              e.strike,
              e.right,
              e.multiplier,
              e.currency,
              e.localSymbol,
              e.primaryExch,
              e.secIdType,
              e.secId,
              e.marketName
            ).map(_.map(_.contId)).flatMap {
              case Some(value) => get(value).map(_.toRight(e))
              case None        => Async[F].pure(e.asLeft)
            }

          private def getByProps(
            exchange: Exchange,
            symbol: String,
            secType: SecurityType,
            strike: Double,
            right: Option[String],
            multiplier: Option[String],
            currency: Option[String],
            localSymbol: Option[String],
            primaryExch: Option[Exchange],
            secIdType: Option[String],
            secId: Option[String],
            marketName: Option[String]
          ): F[Option[ContractByProps]] =
            liftF(
              contractDao.getByProps(
                exchange,
                symbol,
                secType,
                strike,
                right,
                multiplier,
                currency,
                localSymbol,
                primaryExch,
                secIdType,
                secId,
                marketName
              )
            )

          override def create(contract: Contract): F[Unit] = liftF(contractDao.create(contract))
        }

        (contractDaoConnected, requestDaoConnected, barDaoConnected)

    }
}
