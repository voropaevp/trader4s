package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._
import model.datastax.ib.feed.ast.RequestState
import java.util.concurrent.CompletionStage

class RequestContractProvider(
  val context: MapperContext,
  val reqContractHelperByProps: EntityHelper[RequestContractByProps],
  val reqContractHelper: EntityHelper[RequestContract],
  val reqStateAuditByIdHelper: EntityHelper[RequestStateAudit]
) {
  private val session = context.getSession

  private val preparedContractByProp       = prepareInsert(session, reqContractHelperByProps)
  private val preparedDeleteContractByProp = session.prepare(reqContractHelperByProps.deleteByPrimaryKey().asCql)
  private val preparedContractById         = prepareInsert(session, reqContractHelper)
  private val preparedInsertState          = prepareInsert(session, reqStateAuditByIdHelper)

  def changeStateContract(
    contractReq: RequestContract,
    newState: RequestState,
    error: Option[String]
  ): CompletionStage[Unit] =
    batch(
      Seq(
        bind(preparedDeleteContractByProp, contractReq.toProps, reqContractHelperByProps),
        bind(preparedContractById, contractReq.copy(state   = newState), reqContractHelper),
        bind(preparedContractByProp, contractReq.copy(state = newState).toProps, reqContractHelperByProps),
        bind(
          preparedInsertState,
          RequestStateAudit(
            reqId = contractReq.reqId,
            state = newState,
            error = error
          ),
          reqStateAuditByIdHelper
        )
      ),
      session
    )

  def createContract(contract: RequestContract): CompletionStage[Unit] = batch(
    Seq(
      bind(
        preparedContractByProp,
        RequestContractByProps(
          symbol               = contract.symbol,
          secType              = contract.secType,
          exchange             = contract.exchange,
          strike               = contract.strike.getOrElse(.0D),
          right                = contract.right,
          multiplier           = contract.multiplier,
          currency             = contract.currency,
          localSymbol          = contract.localSymbol,
          primaryExch          = contract.primaryExch,
          tradingClass         = contract.tradingClass,
          secIdType            = contract.secIdType,
          secId                = contract.secId,
          comboLegsDescription = contract.comboLegsDescription,
          marketName           = contract.marketName,
          state                = contract.state,
          reqId                = contract.reqId
        ),
        reqContractHelperByProps
      ),
      bind(
        preparedInsertState,
        RequestStateAudit(
          reqId        = contract.reqId,
          state        = contract.state,
          rowsReceived = None,
          error        = None
        ),
        reqStateAuditByIdHelper
      ),
      bind(preparedContractById, contract, reqContractHelper)
    ),
    session
  )
}
