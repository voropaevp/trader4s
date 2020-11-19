package model.datastax.ib.feed.request;

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.mapper.annotations._
import model.datastax.ib.feed.ast._

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage

@Dao
trait RequestDao {

  // ---------------------------------------------------------------
  // Data
  // ---------------------------------------------------------------

  @Query("""SELECT
          req_id
           ${keyspaceId}.request_data_by_props
        reqType = :reqType
        AND contId = :contId
        AND dataType = :dataType
        AND contId = :contId
        AND startTime >= startTimeMin
        AND startTime < startTimeMax
        """)
  def getIdDataByStartRange(
    @CqlName("reqType") reqType: RequestType,
    @CqlName("contId") contId: Int,
    @CqlName("dataType") dataType: DataType,
    @CqlName("state") state: RequestState,
    @CqlName("startTimeMin") startTimeMin: Instant,
    @CqlName("startTimeMax") startTimeMax: Instant
  ): CompletionStage[MappedAsyncPagingIterable[UUID]]

  @Select
  def getDataById(id: UUID): CompletionStage[Option[RequestData]]

  @QueryProvider(
    providerClass = classOf[RequestDataProvider],
    entityHelpers = Array(classOf[RequestDataByProps], classOf[RequestData], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def create(histData: RequestData): CompletionStage[Unit]

  @QueryProvider(
    providerClass = classOf[RequestDataProvider],
    entityHelpers = Array(classOf[RequestDataByProps], classOf[RequestData], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def changeState(
    histData: RequestData,
    newState: RequestState,
    error: Option[String]
  ): CompletionStage[Unit]

  // ---------------------------------------------------------------
  // Contract
  // ---------------------------------------------------------------

  @QueryProvider(
    providerClass = classOf[RequestContractProvider],
    entityHelpers = Array(classOf[RequestContractByProps], classOf[RequestContract], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def create(contract: RequestContract): CompletionStage[Unit]

  @QueryProvider(
    providerClass = classOf[RequestContractProvider],
    entityHelpers = Array(classOf[RequestContractByProps], classOf[RequestContract], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def changeState(
    contractReq: RequestContract,
    newState: RequestState,
    rowsReceived: Option[Int],
    error: Option[String]
  ): CompletionStage[Unit]

  @Select
  def getContractReqById(id: UUID): CompletionStage[Option[RequestContract]]
}
