package model.datastax.ib.feed.request;

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.mapper.annotations._
import model.datastax.ib.feed.ast._

import java.time.Instant
import java.util.{Optional, UUID}
import java.util.concurrent.CompletionStage

@Dao
trait RequestDao {

  // ---------------------------------------------------------------
  // Data
  // ---------------------------------------------------------------

  @Query("""SELECT
          req_id
           ${keyspaceId}.request_data_by_props
        requestType = :requestType
        AND contId = :contId
        AND dataType = :dataType
        AND contId = :contId
        AND startTime >= startTimeMin
        AND startTime < startTimeMax
        """)
  def getIdDataByStartRange(
    @CqlName("requestType") reqType: RequestType,
    @CqlName("contId") contId: Int,
    @CqlName("dataType") dataType: DataType,
    @CqlName("state") state: RequestState,
    @CqlName("startTimeMin") startTimeMin: Instant,
    @CqlName("startTimeMax") startTimeMax: Instant
  ): CompletionStage[MappedAsyncPagingIterable[UUID]]

  @Select
  def getDataById(id: UUID): CompletionStage[Optional[RequestData]]

  @QueryProvider(
    providerClass = classOf[RequestDataProvider],
    entityHelpers = Array(classOf[RequestDataByProps], classOf[RequestData], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def createDataRequest(histData: RequestData): CompletionStage[Unit]

  @QueryProvider(
    providerClass = classOf[RequestDataProvider],
    entityHelpers = Array(classOf[RequestDataByProps], classOf[RequestData], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def changeStateData(
    histData: RequestData,
    newState: RequestState,
    rowsReceived: Option[Long] = None,
    error: Option[String]      = None
  ): CompletionStage[Unit]

  // ---------------------------------------------------------------
  // Contract
  // ---------------------------------------------------------------

  @QueryProvider(
    providerClass = classOf[RequestContractProvider],
    entityHelpers = Array(classOf[RequestContractByEntryHash], classOf[RequestContract], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def createContractRequest(contract: RequestContract): CompletionStage[Unit]

  @QueryProvider(
    providerClass = classOf[RequestContractProvider],
    entityHelpers = Array(classOf[RequestContractByEntryHash], classOf[RequestContract], classOf[RequestStateAudit])
  )
  @StatementAttributes(consistencyLevel = "LOCAL_QUORUM")
  def changeStateContract(
    id: RequestContract,
    newState: RequestState,
    error: Option[String] = None
  ): CompletionStage[Unit]

  @Select
  def getContractReqById(id: UUID): CompletionStage[Optional[RequestContract]]

  @Select
  def getContractReqByEntryHashState(
    entryHash: Int,
    state: RequestState = RequestState.PendingId
  ): CompletionStage[Optional[RequestContractByEntryHash]]
}
