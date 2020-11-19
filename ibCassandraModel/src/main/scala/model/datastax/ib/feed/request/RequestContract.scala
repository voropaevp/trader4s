package model.datastax.ib.feed.request
import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey, Transient}
import com.ib.client.{Contract => IbContract}
import model.datastax.ib.feed.ast.{Exchange, RequestState, RequestType, SecurityType}

import java.util.UUID
import scala.annotation.meta.field

@Entity
case class RequestContract(
  symbol: String,
  //The security's type: STK - stock (or ETF) OPT - option FUT - future IND - index FOP -
  // futures option CASH - forex pair BAG - combo WAR - warrant BOND- bond CMDTY- commodity
  // NEWS- news FUND- mutual fund.
  secType: SecurityType,
  //  The destination exchange.
  exchange: Exchange,
  //  The option's strike price.
  strike: Option[Double],
  //  Either Put or Call (i.e. Options). Valid values are P, PUT, C, CALL.
  right: Option[String],
  //  The instrument's multiplier (i.e. options, futures).
  multiplier: Option[String],
  //  The underlying's currency.
  currency: Option[String],
  //  The contractReq's symbol within its primary exchange For options, this will be the OCC symbol.
  localSymbol: Option[String],
  //  The contractReq's primary exchange. For smart routed contracts, used to define contractReq in
  //  case of ambiguity. Should be defined as native exchange of contractReq, e.g. ISLAND for MSFT
  //  For exchanges which contain a period in name, will only be part of exchange name prior to
  //  period, i.e. ENEXT for ENEXT.BE.
  primaryExch: Option[Exchange],
  //  The trading class name for this contractReq. Available in TWS contractReq description window as
  //  well. For example, GBL Dec '13 future's trading class is "FGBL".
  tradingClass: Option[String],
  //  Security's identifier when querying contractReq's details or placing orders
  //  ISIN - example:
  //       apple: US0378331005
  //  CUSIP - example:
  //       apple: 037833100
  secIdType: Option[String],
  //  Identifier of the security type.
  secId: Option[String],
  //  Description of the combo legs.
  comboLegsDescription: Option[String],
  //The market name for this product.
  marketName: Option[String],
  @(PartitionKey @field)(1) reqId: UUID,
  state: RequestState                 = RequestState.PendingId,
  @Transient requestType: RequestType = RequestType.ContractDetails
) extends Request {
  @Transient def toIb: IbContract = {
    val c = new IbContract()
    c.symbol(symbol)
    strike.foreach(c.strike)
    c.exchange(exchange.toString)
    c.secType(secType.asIb)
    c.exchange(exchange.toString)
    right.foreach(c.right)
    multiplier.foreach(c.multiplier)
    currency.foreach(c.currency)
    localSymbol.foreach(c.localSymbol)
    primaryExch.map(_.toString).foreach(c.primaryExch)
    tradingClass.foreach(c.tradingClass)
    secIdType.foreach(c.secIdType)
    secId.foreach(c.secId)
    c
  }
}
