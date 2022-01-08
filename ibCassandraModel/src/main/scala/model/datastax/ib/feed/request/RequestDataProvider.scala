package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._
import model.datastax.ib.feed.ast.RequestState
import model.datastax.ib.feed.codec.CqlStringToAstCodec.{DataTypeCodec, ReqStateCodec, ReqTypeCodec}

import java.util
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.CompletionStage

class RequestDataProvider(
  val context: MapperContext,
  val reqHistDataHelperByProp: EntityHelper[RequestDataByProps],
  val reqHistDataByIdHelper: EntityHelper[RequestData],
  val reqStateAuditByIdHelper: EntityHelper[RequestStateAudit]
) {
  private val session = context.getSession

  private val preparedReqHistDataByProp = session.prepare(s"""UPDATE ${reqHistDataHelperByProp.getTableId}
                                                             | SET req_id = req_id + :reqId,
                                                             | req_type = ?,
                                                             | cont_id = ?,
                                                             | data_type = ?,
                                                             | state = ?,
                                                             | start_time = ?""".stripMargin)

  private val preparedDeleteReqHistDataByProp = session.prepare(reqHistDataHelperByProp.deleteByPrimaryKey().asCql)
  private val preparedReqHistDataById         = prepareInsert(session, reqHistDataByIdHelper)
  private val preparedInsertState             = prepareInsert(session, reqStateAuditByIdHelper)

  def changeState(
    histData: RequestData,
    newState: RequestState,
    rowsReceived: Option[Int] = None,
    error: Option[String]     = None
  ): CompletionStage[Unit] = batch(
    Seq(
      bind(preparedDeleteReqHistDataByProp, histData, reqHistDataHelperByProp),
      bind(preparedReqHistDataById, histData.copy(state = newState), reqHistDataByIdHelper),
      preparedReqHistDataByProp
        .bind()
        .setSet("reqId", {
          val s: util.Set[UUID] = new util.HashSet(1)
          s.add(histData.reqId)
          s
        }, classOf[UUID])
        .set(1, histData.reqType, ReqTypeCodec)
        .setInt(2, histData.contId)
        .set(3, histData.dataType, DataTypeCodec)
        .set(4, histData.state, ReqStateCodec)
        .setInstant(5, histData.startTime),
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
      preparedReqHistDataByProp
        .bind()
        .setUuid(0, histData.reqId)
        .set(1, histData.reqType, ReqTypeCodec)
        .setInt(2, histData.contId)
        .set(3, histData.dataType, DataTypeCodec)
        .set(4, histData.state, ReqStateCodec)
        .setInstant(5, histData.startTime),
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
