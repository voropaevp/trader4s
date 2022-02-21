package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._
import model.datastax.ib.feed.ast.RequestState

import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.jdk.OptionConverters.RichOption

class RequestContractProvider(
  val context: MapperContext,
  val reqContractHelperByEntryHash: EntityHelper[RequestContractByEntryHash],
  val reqContractHelper: EntityHelper[RequestContract],
  val reqStateAuditByIdHelper: EntityHelper[RequestStateAudit]
) {
  private val session = context.getSession

  private val preparedContractByEntryHash = prepareInsert(session, reqContractHelperByEntryHash)
  private val preparedDeleteContractByEntryHash =
    session.prepare(reqContractHelperByEntryHash.deleteByPrimaryKey().asCql)
  private val preparedContractRequestById = prepareInsert(session, reqContractHelper)
  private val preparedInsertState         = prepareInsert(session, reqStateAuditByIdHelper)

  def changeStateContract(
    contractReq: RequestContract,
    newState: RequestState,
    error: Option[String]
  ): CompletionStage[Unit] =
    batch(
      Seq(
        bind(
          preparedDeleteContractByEntryHash,
          RequestContractByEntryHash(contractReq.asContractEntry.hashCode(), contractReq.state, contractReq.reqId),
          reqContractHelperByEntryHash
        ),
        bind(preparedContractRequestById, contractReq.copy(state = newState), reqContractHelper),
        bind(
          preparedContractByEntryHash,
          RequestContractByEntryHash(contractReq.asContractEntry.hashCode(), newState, contractReq.reqId),
          reqContractHelperByEntryHash
        ),
        bind(
          preparedInsertState,
          RequestStateAudit(
            reqId        = contractReq.reqId,
            state        = newState,
            error        = error.toJava,
            rowsReceived = 0
          ),
          reqStateAuditByIdHelper
        )
      ),
      session
    )

  def createContractRequest(contractReq: RequestContract): CompletionStage[Unit] = batch(
    Seq(
      bind(
        preparedContractByEntryHash,
        RequestContractByEntryHash(contractReq.asContractEntry.hashCode(), contractReq.state, contractReq.reqId),
        reqContractHelperByEntryHash
      ),
      bind(
        preparedInsertState,
        RequestStateAudit(
          reqId        = contractReq.reqId,
          state        = contractReq.state,
          rowsReceived = 0,
          error        = Optional.empty[String]()
        ),
        reqStateAuditByIdHelper
      ),
      bind(preparedContractRequestById, contractReq, reqContractHelper)
    ),
    session
  )
}
