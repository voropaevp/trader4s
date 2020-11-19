package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._
import model.datastax.ib.feed.ast.RequestState

import java.util.concurrent.CompletionStage

class RequestDataProvider(
  val context: MapperContext,
  val reqHistDataHelperByProp: EntityHelper[RequestDataByProps],
  val reqHistDataByIdHelper: EntityHelper[RequestData],
  val reqStateAuditByIdHelper: EntityHelper[RequestStateAudit]
) {
  private val session = context.getSession

  private val preparedReqHistDataByProp       = prepareInsert(session, reqHistDataHelperByProp)
  private val preparedDeleteReqHistDataByProp = session.prepare(reqHistDataHelperByProp.deleteByPrimaryKey().asCql)
  private val preparedReqHistDataById         = prepareInsert(session, reqHistDataByIdHelper)
  private val preparedInsertState             = prepareInsert(session, reqStateAuditByIdHelper)

  def changeState(
    histData: RequestData,
    newState: RequestState,
    rowsReceived: Option[Int] = None,
    error: Option[String]     = None
  ): CompletionStage[Unit] =
    batch(
      Seq(
        bind(preparedDeleteReqHistDataByProp, histData, reqHistDataHelperByProp),
        bind(preparedReqHistDataById, histData.copy(state   = newState), reqHistDataByIdHelper),
        bind(preparedReqHistDataByProp, histData.copy(state = newState), reqHistDataHelperByProp),
        bind(
          preparedInsertState,
          RequestStateAudit(
            reqId        = histData.reqId,
            state        = newState,
            rowsReceived = rowsReceived,
            error        = error
          ),
          reqStateAuditByIdHelper
        )
      ),
      session
    )

  def create(histData: RequestData): CompletionStage[Unit] = batch(
    Seq(
      bind(
        preparedReqHistDataByProp,
        RequestDataByProps(
          reqType   = histData.reqType,
          contId    = histData.contId,
          dataType  = histData.dataType,
          state     = histData.state,
          startTime = histData.startTime,
          endTime   = histData.endTime,
          reqId     = histData.reqId
        ),
        reqHistDataHelperByProp
      ),
      bind(
        preparedInsertState,
        RequestStateAudit(
          reqId        = histData.reqId,
          state        = histData.state,
          rowsReceived = None,
          error        = None
        ),
        reqStateAuditByIdHelper
      ),
      bind(preparedReqHistDataById, histData, reqHistDataByIdHelper)
    ),
    session
  )
}
