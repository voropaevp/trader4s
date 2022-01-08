package db

import cats.Monad
import cats.effect.{Async, Resource}
import cats.effect.implicits._
import cats.syntax.all._
import fs2.{Chunk, Stream}
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.ib.client.{Bar => IbBar}
import model.datastax.ib.feed.ast._
import model.datastax.ib.feed.request._
import model.datastax.ib.feed.response.contract.{Contract, ContractByProps, ContractDao}
import model.datastax.ib.feed.response.data.{Bar, BarDao}
import model.datastax.ib.feed.{FeedMapper, FeedMapperBuilder}
import utils.config.Config.ContractEntry

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import scala.jdk.CollectionConverters._

object ConnectedDao {

  trait BarDaoConnected[F[_]] {
    def write(bar: IbBar, contId: Int, size: BarSize, dataType: DataType): F[Unit]

    def headTs(contId: Int, size: BarSize, dataType: DataType): F[Instant]

    def tailTs(contId: Int, size: BarSize, dataType: DataType): F[Instant]
  }

  trait RequestDaoConnected[F[_]] {

    // ---------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------

    def getDataById(id: UUID): CompletionStage[Option[RequestData]]
    def create(histData: RequestData): CompletionStage[Unit]
    def changeState(
      histData: RequestData,
      newState: RequestState,
      error: Option[String]
    ): CompletionStage[Unit]

    // ---------------------------------------------------------------
    // Contract
    // ---------------------------------------------------------------

    def create(contract: RequestContract): CompletionStage[Unit]
    def getContById(id: UUID): CompletionStage[Option[RequestContract]]

    def getByProps(
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
    ): F[Option[RequestContractByProps]]

    def changeState(
      contractReq: RequestContract,
      newState: RequestState,
      rowsReceived: Option[Int],
      error: Option[String]
    ): CompletionStage[Unit]

    def getContractReqById(id: UUID): CompletionStage[Option[RequestContract]]

    def getByProp(
      reqType: RequestType,
      dataType: DataType,
      state: RequestState,
      contId: Int,
      startTimeMin: Instant,
      startTimeMax: Instant
    ): Stream[F, UUID]

    def getById(id: UUID): F[Option[RequestData]]
  }

  trait ContractDaoConnected[F[_]] {
    def get(contId: Int): F[Option[Contract]]

    def getByProps(e: ContractEntry): F[Option[ContractByProps]] = getByProps(
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
    )

    def getByProps(
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
    ): F[Option[ContractByProps]]

    def create(contract: Contract): F[Unit]

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
    DbSession.makeSession("meta").product(DbSession.makeSession("data")).map {
      case (metaSession, dataSession) =>
        lazy val mapperData: FeedMapper = new FeedMapperBuilder(dataSession).build()
        lazy val mapperMeta: FeedMapper = new FeedMapperBuilder(metaSession).build()

        lazy val barDao: BarDao           = mapperData.BarDao()
        lazy val requestDao: RequestDao   = mapperMeta.RequestDao()
        lazy val contractDao: ContractDao = mapperMeta.ContractDao()

        val barDaoConnected = new BarDaoConnected[F] {

          def write(bar: IbBar, contId: Int, size: BarSize, dataType: DataType): F[Unit] =
            liftF(barDao.create(Bar(bar, contId, dataType, size)))

          def headTs(contId: Int, size: BarSize, dataType: DataType): F[Instant] =
            liftF(barDao.headTs(contId, size, dataType))

          def tailTs(contId: Int, size: BarSize, dataType: DataType): F[Instant] =
            liftF(barDao.tailTs(contId, size, dataType))
        }

        val requestDaoConnected = new RequestDaoConnected[F] {
          def changeStateData(
            histData: RequestData,
            newState: RequestState,
            rowsReceived: Option[Int],
            error: Option[String]
          ): F[Unit] = liftF(requestDao.changeStateData(histData, newState, rowsReceived, error))

          def changeStateContract(
            contractReq: RequestContract,
            newState: RequestState,
            error: Option[String]
          ): F[Unit] = liftF(requestDao.changeStateContract(contractReq, newState, error))

          def reqDataById(id: UUID): F[Option[RequestData]] = liftF(requestDao.getDataById(id))

          def reqContById(id: UUID): F[Option[RequestContract]] = liftF(requestDao.getContractReqById(id))

          def getByProp(
            reqType: RequestType,
            dataType: DataType,
            state: RequestState,
            contId: Int,
            startTimeMin: Instant,
            startTimeMax: Instant
          ): Stream[F, UUID] =
            liftStream(requestDao.getIdDataByStartRange(reqType, contId, dataType, state, startTimeMin, startTimeMax))

          def getById(id: UUID): F[RequestData] = liftF(requestDao.getById(id))

          def create(req: RequestData): F[Unit] = liftF(requestDao.create(req))
        }

        val contractDaoConnected = new ContractDaoConnected[F] {
          def get(contId: Int): F[Contract] = liftF(contractDao.get(contId))

          def getByProps(
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
          ): F[ContractByProps] =
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

          def create(contract: Contract): F[Unit] = liftF(contractDao.create(contract))
        }

        (contractDaoConnected, requestDaoConnected, barDaoConnected)

    }
}
