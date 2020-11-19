package interpreter.ibkr.feed.components

import cats.effect.{Async, Resource, Sync}
import com.ib.client._
import com.typesafe.scalalogging.LazyLogging
import model.datastax.ib.feed.response.data.{Bar          => MBar}
import model.datastax.ib.feed.response.contract.{Contract => MContact}

import java.{lang, util}
import scala.jdk.CollectionConverters._
import domain.feed.{GenericError, FeedRequestService}
import model.datastax.ib.feed.request.{RequestData, RequestContract}

class IbkrFeedWrapper[F[_]: Async](feedRequests: FeedRequestService[F]) extends EWrapper with LazyLogging {

  override def replaceFAEnd(x$1: Int, x$2: String): Unit =
    logger.warn(s"Unexpected data [${x$1} ${x$2}] in replaceFAEnd received from broker ")

  override def accountUpdateMultiEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [$i] in accountUpdateMultiEnd received from broker ")

  override def tickPrice(i: Int, i1: Int, v: Double, tickAttrib: TickAttrib): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $v ,  $tickAttrib ] in tickPrice received from broker ")

  override def tickSize(i: Int, i1: Int, l: Long): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $l ] in tickSize received from broker ")

  override def tickOptionComputation(
    x$1: Int,
    x$2: Int,
    x$3: Int,
    x$4: Double,
    x$5: Double,
    x$6: Double,
    x$7: Double,
    x$8: Double,
    x$9: Double,
    x$10: Double,
    x$11: Double
  ): Unit = logger.warn(s"Unexpected data in tickOptionComputation received from broker ")

  override def tickGeneric(i: Int, i1: Int, v: Double): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $v ] in tickGeneric received from broker ")

  override def tickString(i: Int, i1: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $s ] in tickString received from broker ")

  override def tickEFP(i: Int, i1: Int, v: Double, s: String, v1: Double, i2: Int, s1: String, v2: Double, v3: Double)
    : Unit =
    logger.warn(
      s"Unexpected data [ $i ,  $i1 ,  $v ,  $s ,  $v1 ,  $i2 ,  $s1 ,  $v2 ,  $v3 ] in tickEFP received from broker "
    )

  override def orderStatus(
    i: Int,
    s: String,
    v: Double,
    v1: Double,
    v2: Double,
    i1: Int,
    i2: Int,
    v3: Double,
    i3: Int,
    s1: String,
    v4: Double
  ): Unit =
    logger.warn(
      s"Unexpected data [ $i ,  $s ,  $v ,  $v1 ,  $v2 ,  $i1 ,  $i2 ,  $v3 ,  $i3 ,  $s1 ,  $v4 ] in orderStatus received from broker "
    )

  override def openOrder(i: Int, contract: Contract, order: Order, orderState: OrderState): Unit =
    logger.warn(s"Unexpected data [ $i ,  $contract ,  $order ,  $orderState ] in openOrder received from broker ")

  override def openOrderEnd(): Unit = logger.warn(s"Unexpected data [] in openOrderEnd received from broker ")

  override def updateAccountValue(s: String, s1: String, s2: String, s3: String): Unit =
    logger.warn(s"Unexpected data [ $s ,  $s1 ,  $s2 ,  $s3 ] in updateAccountValue received from broker ")

  override def updatePortfolio(
    contract: Contract,
    v: Double,
    v1: Double,
    v2: Double,
    v3: Double,
    v4: Double,
    v5: Double,
    s: String
  ): Unit =
    logger.warn(
      s"Unexpected data [ $contract ,  $v ,  $v1 ,  $v2 ,  $v3 ,  $v4 ,  $v5 ,  $s ] in updatePortfolio received from broker "
    )

  override def updateAccountTime(s: String): Unit =
    logger.warn(s"Unexpected data [ $s ] in updateAccountTime received from broker ")

  override def accountDownloadEnd(s: String): Unit =
    logger.warn(s"Unexpected data [ $s ] in accountDownloadEnd received from broker ")

  override def nextValidId(i: Int): Unit = logger.warn(s"[ $i ] nextValidId received from broker")

  override def contractDetails(i: Int, contractDetails: ContractDetails): Unit = {
    logger.warn(s"Unexpected data [ $i ,  $contractDetails ] in contractDetails received from broker ")
    feedRequests.enqueue(
      i, {
        case _: RequestContract => Right(MContact(contractDetails))
        case x @ _                 => Left(GenericError(s"Bars returned as a ${x.getClass}, must be RequestContract"))
      }
    )
  }

  override def bondContractDetails(i: Int, contractDetails: ContractDetails): Unit =
    logger.warn(s"Unexpected data [ $i ,  $contractDetails ] in bondContractDetails received from broker ")

  override def contractDetailsEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in contractDetailsEnd received from broker ")

  override def execDetails(i: Int, contract: Contract, execution: Execution): Unit =
    logger.warn(s"Unexpected data [ $i ,  $contract ,  $execution ] in execDetails received from broker ")

  override def execDetailsEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in execDetailsEnd received from broker ")

  override def updateMktDepth(i: Int, i1: Int, i2: Int, i3: Int, v: Double, i4: Long): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $i2 ,  $i3 ,  $v ,  $i4 ] in updateMktDepth received from broker ")

  override def updateMktDepthL2(i: Int, i1: Int, s: String, i2: Int, i3: Int, v: Double, i4: Long, b: Boolean): Unit =
    logger.warn(
      s"Unexpected data [ $i ,  $i1 ,  $s ,  $i2 ,  $i3 ,  $v ,  $i4 ,  $b ] in updateMktDepthL2 received from broker "
    )

  override def updateNewsBulletin(i: Int, i1: Int, s: String, s1: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $s ,  $s1 ] in updateNewsBulletin received from broker ")

  override def managedAccounts(s: String): Unit =
    logger.warn(s"Unexpected data [ $s ] in managedAccounts received from broker ")

  override def receiveFA(i: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ] in receiveFA received from broker ")

  override def historicalData(i: Int, bar: Bar): Unit = {
    logger.info(s"[ $i ,  $bar ] in historicalData received from broker ")
    feedRequests.enqueue(
      i, {
        case r: RequestData => Right(MBar(bar, r.contId, r.dataType, r.size))
        case x @ _          => Left(GenericError(s"Bars returned as a ${x.getClass}, must be RequestData"))
      }
    )
  }

  override def wshEventData(x$1: Int, x$2: String): Unit = ???

  override def wshMetaData(x$1: Int, x$2: String): Unit = ???

  override def scannerParameters(s: String): Unit =
    logger.warn(s"Unexpected data [ $s ] in scannerParameters received from broker ")

  override def scannerData(
    i: Int,
    i1: Int,
    contractDetails: ContractDetails,
    s: String,
    s1: String,
    s2: String,
    s3: String
  ): Unit =
    logger.warn(
      s"Unexpected data [ $i ,  $i1 ,  $contractDetails ,  $s ,  $s1 ,  $s2 ,  $s3 ] in scannerData received from broker "
    )

  override def scannerDataEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in scannerDataEnd received from broker ")

  override def realtimeBar(
    i: Int,
    l: Long,
    v: Double,
    v1: Double,
    v2: Double,
    v3: Double,
    l1: Long,
    v4: Double,
    i1: Int
  ): Unit =
    logger.warn(
      s"Unexpected data [ $i ,  $l ,  $v ,  $v1 ,  $v2 ,  $v3 ,  $l1 ,  $v4 ,  $i1 ] in realtimeBar received from broker "
    )

  override def currentTime(l: Long): Unit = logger.warn(s"Unexpected data [ $l ] in currentTime received from broker ")

  override def fundamentalData(i: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ] in fundamentalData received from broker ")

  override def deltaNeutralValidation(i: Int, deltaNeutralContract: DeltaNeutralContract): Unit =
    logger.warn(s"Unexpected data [ $i ,  $deltaNeutralContract ] in deltaNeutralValidation received from broker ")

  override def tickSnapshotEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in tickSnapshotEnd received from broker ")

  override def marketDataType(i: Int, i1: Int): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ] in marketDataType received from broker ")

  override def commissionReport(commissionReport: CommissionReport): Unit =
    logger.warn(s"Unexpected data [ $commissionReport ] in commissionReport received from broker ")

  override def position(s: String, contract: Contract, v: Double, v1: Double): Unit =
    logger.warn(s"Unexpected data [ $s ,  $contract ,  $v ,  $v1 ] in position received from broker ")

  override def positionEnd(): Unit = logger.warn(s"Unexpected data [] in positionEnd received from broker ")

  override def accountSummary(i: Int, s: String, s1: String, s2: String, s3: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ,  $s1 ,  $s2 ,  $s3 ] in accountSummary received from broker ")

  override def accountSummaryEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in accountSummaryEnd received from broker ")

  override def verifyMessageAPI(s: String): Unit =
    logger.warn(s"Unexpected data [ $s ] in verifyMessageAPI received from broker ")

  override def verifyCompleted(b: Boolean, s: String): Unit =
    logger.warn(s"Unexpected data [ $b ,  $s ] in verifyCompleted received from broker ")

  override def verifyAndAuthMessageAPI(s: String, s1: String): Unit =
    logger.warn(s"Unexpected data [ $s ,  $s1 ] in verifyAndAuthMessageAPI received from broker ")

  override def verifyAndAuthCompleted(b: Boolean, s: String): Unit =
    logger.warn(s"Unexpected data [ $b ,  $s ] in verifyAndAuthCompleted received from broker ")

  override def displayGroupList(i: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ] in displayGroupList received from broker ")

  override def displayGroupUpdated(i: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ] in displayGroupUpdated received from broker ")

  override def error(e: Exception): Unit =
    e match {
      // probably a bug
      case e: NullPointerException =>
        logger.warn(s"Got NullPointerException [ $e ] from client api. Does not cause any issue during startup")
      case _ => feedRequests.sendError(GenericError("self API error", cause = e))
    }

  override def error(s: String): Unit = feedRequests.sendError(GenericError(s))

  override def error(i: Int, code: Int, s: String): Unit = i match {
    case -1 =>
      code match {
        case x if List(502, 504).contains(x) => feedRequests.sendError(GenericError(s"[$code] [$s]"))
        case _                               => logger.warn(s"Notification with error code [$code] [$s]")
      }
    case _ => feedRequests.fail(i, GenericError(s"[$code] [$s]"))
  }

  override def connectionClosed(): Unit = feedRequests.sendError(GenericError("Connection to IBKR gateway shutdown"))

  override def connectAck(): Unit = logger.info(s"Connection to IBKR gateway acknowledged")

  override def positionMulti(i: Int, s: String, s1: String, contract: Contract, v: Double, v1: Double): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ,  $s1 ,  $contract ,  $v ,  $v1 ] in positionMulti received from broker ")

  override def positionMultiEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in positionMultiEnd received from broker ")

  override def accountUpdateMulti(i: Int, s: String, s1: String, s2: String, s3: String, s4: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ,  $s1 ,  $s2 ,  $s3 ,  $s4 ] in accountUpdateMulti received from broker ")

  override def securityDefinitionOptionalParameter(
    i: Int,
    s: String,
    i1: Int,
    s1: String,
    s2: String,
    set: util.Set[String],
    set1: util.Set[lang.Double]
  ): Unit =
    logger.warn(
      s"Unexpected data [ $i ,  $s ,  $i1 ,  $s1 ,  $s2 ,  $set .Set[String],  $set1 .Set[lang.Double]] in securityDefinitionOptionalParameter received from broker "
    )

  override def securityDefinitionOptionalParameterEnd(i: Int): Unit =
    logger.warn(s"Unexpected data [ $i ] in securityDefinitionOptionalParameterEnd received from broker ")

  override def softDollarTiers(i: Int, softDollarTiers: Array[SoftDollarTier]): Unit =
    softDollarTiers.foreach(x => logger.warn(s"Unexpected data [ $i ,  $x] in softDollarTiers received from broker "))

  override def familyCodes(familyCodes: Array[FamilyCode]): Unit =
    familyCodes.foreach(x =>
      logger.warn(s"Unexpected data [ ${x.accountID}:${x.familyCodeStr}] in familyCodes received from broker ")
    )

  override def symbolSamples(i: Int, contractDescriptions: Array[ContractDescription]): Unit =
    contractDescriptions.foreach(x =>
      logger.warn(s"Unexpected data [ $i ,  ${x.contract}] in symbolSamples received from broker ")
    )

  override def historicalDataEnd(i: Int, start: String, end: String): Unit = {
    logger.info(s"id [$i] [$start  $end] fulfilled request")
    feedRequests.close(i)
  }

  override def mktDepthExchanges(depthMktDataDescriptions: Array[DepthMktDataDescription]): Unit =
    logger.warn(s"Unexpected data [more debug required] in mktDepthExchanges received from broker ")

  override def tickNews(i: Int, l: Long, s: String, s1: String, s2: String, s3: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $l ,  $s ,  $s1 ,  $s2 ,  $s3 ] in tickNews received from broker ")

  override def smartComponents(i: Int, map: util.Map[Integer, util.Map.Entry[String, Character]]): Unit =
    logger.warn(s"Unexpected data [ $i , more debug required ] in smartComponents received from broker ")

  override def tickReqParams(i: Int, v: Double, s: String, i1: Int): Unit =
    logger.warn(s"Unexpected data [ $i ,  $v ,  $s ,  $i1 ] in tickReqParams received from broker ")

  override def newsProviders(newsProviders: Array[NewsProvider]): Unit =
    logger.warn(s"Unexpected data [more debug required] in newsProviders received from broker ")

  override def newsArticle(i: Int, i1: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $s ] in newsArticle received from broker ")

  override def historicalNews(i: Int, s: String, s1: String, s2: String, s3: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $s ,  $s1 ,  $s2 ,  $s3 ] in historicalNews received from broker ")

  override def historicalNewsEnd(i: Int, b: Boolean): Unit =
    logger.warn(s"Unexpected data [ $i ,  $b ] in historicalNewsEnd received from broker ")

  override def headTimestamp(i: Int, s: String): Unit = ???

  override def histogramData(i: Int, list: util.List[HistogramEntry]): Unit =
    list.asScala.foreach(x => logger.warn(s"Unexpected data [ $i , $x ] in histogramData received from broker "))

  override def historicalDataUpdate(i: Int, bar: Bar): Unit =
    logger.warn(s"Unexpected data [ $i ,  $bar ] in historicalDataUpdate received from broker ")

  override def rerouteMktDataReq(i: Int, i1: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $s ] in rerouteMktDataReq received from broker ")

  override def rerouteMktDepthReq(i: Int, i1: Int, s: String): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $s ] in rerouteMktDepthReq received from broker ")

  override def marketRule(i: Int, priceIncrements: Array[PriceIncrement]): Unit =
    priceIncrements.foreach(x =>
      logger.warn(s"Unexpected data [ $i ,  ${x.increment},  ${x.lowEdge()}] in marketRule received from broker ")
    )

  override def pnl(i: Int, v: Double, v1: Double, v2: Double): Unit =
    logger.warn(s"Unexpected data [ $i ,  $v ,  $v1 ,  $v2 ] in pnl received from broker ")

  override def pnlSingle(i: Int, i1: Int, v: Double, v1: Double, v2: Double, v3: Double): Unit =
    logger.warn(s"Unexpected data [ $i ,  $i1 ,  $v ,  $v1 ,  $v2 ,  $v3 ] in pnlSingle received from broker ")

  override def historicalTicks(i: Int, list: util.List[HistoricalTick], done: Boolean): Unit = ???

  override def historicalTicksBidAsk(i: Int, list: util.List[HistoricalTickBidAsk], done: Boolean): Unit = ???

  override def historicalTicksLast(i: Int, list: util.List[HistoricalTickLast], b: Boolean): Unit = ???

  override def tickByTickAllLast(
    i: Int,
    i1: Int,
    l: Long,
    v: Double,
    i2: Long,
    tickAttribLast: TickAttribLast,
    s: String,
    s1: String
  ): Unit =
    ???

  override def tickByTickBidAsk(
    i: Int,
    l: Long,
    v: Double,
    v1: Double,
    i1: Long,
    i2: Long,
    tickAttribBidAsk: TickAttribBidAsk
  ): Unit =
    ???

  override def tickByTickMidPoint(i: Int, l: Long, v: Double): Unit =
    logger.warn(s"Unexpected data [ $i ,  $l ,  $v ] in tickByTickMidPoint received from broker ")

  override def orderBound(l: Long, i: Int, i1: Int): Unit =
    logger.warn(s"Unexpected data [ $l ,  $i ,  $i1 ] in orderBound received from broker ")

  override def completedOrder(contract: Contract, order: Order, orderState: OrderState): Unit =
    logger.warn(s"Unexpected data [ $contract ,  $order ,  $orderState ] in completedOrder received from broker ")

  override def completedOrdersEnd(): Unit =
    logger.warn(s"Unexpected data [] in completedOrdersEnd received from broker ")

}

object IbkrFeedWrapper {
  def apply[F[_]: Async](feedRequests: FeedRequestService[F]): Resource[F, IbkrFeedWrapper[F]] =
    Resource.eval(Sync[F].delay(new IbkrFeedWrapper(feedRequests)))
}
