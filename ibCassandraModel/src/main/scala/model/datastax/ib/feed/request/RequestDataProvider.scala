package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._
import model.datastax.ib.feed.ast.RequestState

import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.jdk.OptionConverters.RichOption

class RequestDataProvider(
  val context: MapperContext,
  val reqHistDataHelperByPropHelper: EntityHelper[RequestDataByProps],
  val reqHistDataByIdHelper: EntityHelper[RequestData],
  val reqStateAuditByIdHelper: EntityHelper[RequestStateAudit]
) {
  private val session = context.getSession

  private val preparedUpdateReqHistDataByProp = prepareInsert(session, reqHistDataHelperByPropHelper)
  private val preparedDeleteReqHistDataByProp = deleteByPrimaryKey(session, reqHistDataHelperByPropHelper)
  private val preparedReqHistDataById         = prepareInsert(session, reqHistDataByIdHelper)
  private val preparedInsertState             = prepareInsert(session, reqStateAuditByIdHelper)

  def changeStateData(
    histData: RequestData,
    newState: RequestState,
    rowsReceived: Option[Long] = None,
    error: Option[String]      = None
  ): CompletionStage[Unit] = batch(
    Seq(
      bind(preparedDeleteReqHistDataByProp, histData.toProps, reqHistDataHelperByPropHelper),
      bind(preparedUpdateReqHistDataByProp, histData.toProps.copy(state = newState), reqHistDataHelperByPropHelper),
      bind(
        preparedInsertState,
        RequestStateAudit(
          reqId        = histData.reqId,
          state        = newState,
          rowsReceived = rowsReceived.getOrElse(0),
          error        = error.toJava
        ),
        reqStateAuditByIdHelper
      )
    ),
    session
  )

  def createDataRequest(histData: RequestData): CompletionStage[Unit] = batch(
    Seq(
      bind(preparedUpdateReqHistDataByProp, histData.toProps, reqHistDataHelperByPropHelper),
      bind(
        preparedInsertState,
        RequestStateAudit(
          reqId        = histData.reqId,
          state        = histData.state,
          rowsReceived = 0,
          error        = Optional.empty[String]()
        ),
        reqStateAuditByIdHelper
      ),
      bind(preparedReqHistDataById, histData, reqHistDataByIdHelper)
    ),
    session
  )
}
