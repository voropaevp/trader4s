package db

import cats.{MonadError, MonadThrow}
import cats.data.OptionT
import model.datastax.ib.feed.response.contract.ContractEntry

import java.util.Optional
import cats.syntax.applicative._
import cats.syntax.option._
import cats.syntax.flatMap._
import cats.syntax.semigroupal._
import cats.syntax.functor._
import cats.effect.{Async, Resource}
import fs2.{Chunk, Stream}
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import domain.feed.FeedException
import model.datastax.ib.feed.ast._
import model.datastax.ib.feed.request._
import model.datastax.ib.feed.response.contract.{Contract, ContractDao}
import model.datastax.ib.feed.response.data.{Bar, BarDao}
import model.datastax.ib.feed.{FeedMapper, FeedMapperBuilder}
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object ConnectedDao {

  trait BarDaoConnected[F[_]] {
    def write(bar: Bar): F[Unit]

    def get(
      contId: Int,
      size: BarSize,
      dataType: DataType,
      head: Instant,
      tail: Instant,
      limit: Int
    ): Stream[F, Bar]

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
      size: BarSize,
      contId: Int,
      startTimeMin: Instant,
      startTimeMax: Instant
    ): Stream[F, RequestData]

    def getContractEntryState(
      entry: ContractEntry,
      state: RequestState = RequestState.PendingId
    ): F[Option[RequestContract]]

    def create(req: Request): F[Unit]
  }

  trait ContractDaoConnected[F[_]] {
    def get(contId: Int): F[Option[Contract]]

    def getByContractEntry(entry: ContractEntry): F[Option[Contract]]

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

  private def liftFOption[F[_]: Async, T](under: CompletionStage[Optional[T]]): F[Option[T]] =
    liftF(under).map(_.toScala)

  private def liftFUnit[F[_]: Async](under: CompletionStage[Void]): F[Unit] =
    Async[F].fromCompletableFuture(Async[F].delay(under.toCompletableFuture)).void

  private def page[F[_]: Async, T](it: MappedAsyncPagingIterable[T]): Stream[F, T] =
    Stream.unfoldChunkEval(it)(thisIt =>
      if (thisIt.remaining() > 0)
        (Chunk.iterable(thisIt.currentPage().asScala), thisIt).some.pure[F]
      else if (thisIt.hasMorePages)
        Async[F]
          .fromCompletableFuture(thisIt.fetchNextPage().toCompletableFuture.pure[F])
          .map(nextIt => (Chunk.iterable(nextIt.currentPage().asScala), nextIt).some)
      else
        Async[F].pure[Option[(Chunk[T], MappedAsyncPagingIterable[T])]](None)
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
        lazy val mapperData: FeedMapper =
          new FeedMapperBuilder(dataSession).withDefaultKeyspace(s"${schemaPrefix}data").build()
        lazy val mapperMeta: FeedMapper =
          new FeedMapperBuilder(metaSession).withDefaultKeyspace(s"${schemaPrefix}meta").build()

        lazy val barDao: BarDao                                 = mapperData.BarDao()
        lazy val requestDao: RequestDao                         = mapperMeta.RequestDao()
        lazy val contractDao: ContractDao                       = mapperMeta.ContractDao()
        implicit def unsafeLogger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

        val barDaoConnected = new BarDaoConnected[F] {

          def write(bar: Bar): F[Unit] = liftFUnit(barDao.create(bar))

          def get(contId: Int, size: BarSize, dataType: DataType, head: Instant, tail: Instant, limit: Int)
            : Stream[F, Bar] =
            liftStream(barDao.selectRangeLimit(contId, size, dataType, head, tail, limit))

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
                liftF(requestDao.changeStateData(req, newState, rowsReceived, error.map(_.toString))).map(_.some)
              )
              .orElse(
                OptionT(getReqContById(id)).flatMapF(req =>
                  liftF(requestDao.changeStateContract(req, newState, error.map(_.getMessage))).map(_.some)
                )
              )
              .getOrElseF(Async[F].raiseError(DbError("Changing state for missing data request")).void)

          def getReqDataById(id: UUID): F[Option[RequestData]] = liftFOption(requestDao.getDataById(id))

          def getReqContById(id: UUID): F[Option[RequestContract]] = liftFOption(requestDao.getContractReqById(id))

          def getDataByProp(
            reqType: RequestType,
            dataType: DataType,
            state: RequestState,
            size: BarSize,
            contId: Int,
            startTimeMin: Instant,
            startTimeMax: Instant
          ): Stream[F, RequestData] =
            liftStream(
              requestDao.getIdDataByStartRange(reqType, contId, size, dataType, state, startTimeMin, startTimeMax)
            ).evalMap(requestDataByProps => getReqDataById(requestDataByProps.reqId)).evalMap {
              case None =>
                Async[F].raiseError[RequestData](DbError(s"""Corrupted cassandra state. Record in request_data_by_props in range of (
                                               |$reqType, $contId, $size, $dataType, 
                                               |$state, $startTimeMin, $startTimeMax
                                               |) referred to none existent request id""".stripMargin))
              case Some(value) => value.pure[F]
            }

          def getContractEntryState(
            e: ContractEntry,
            state: RequestState
          ): F[Option[RequestContract]] =
            OptionT(
              liftFOption(
                requestDao.getContractReqByEntryHashState(e.hashCode(), state)
              )
            ).flatMapF(req => getReqContById(req.reqId)).value

          def create(req: Request): F[Unit] = req match {
            case r: RequestContract => liftF(requestDao.createContractRequest(r))
            case r: RequestData     => liftF(requestDao.createDataRequest(r))
            case _                  => ().pure[F]
          }
        }

        val contractDaoConnected = new ContractDaoConnected[F] {
          override def get(contId: Int): F[Option[Contract]] = liftFOption(contractDao.get(contId))

          override def create(contract: Contract): F[Unit] = liftF(contractDao.create(contract))

          override def getByContractEntry(entry: ContractEntry): F[Option[Contract]] =
            OptionT(
              liftFOption(
                contractDao.getByEntryHash(entry.hashCode())
              )
            ).flatMap(s => OptionT(get(s.contId))).value

        }

        (contractDaoConnected, requestDaoConnected, barDaoConnected)

    }
}
