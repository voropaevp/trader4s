package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey}
import model.datastax.ib.feed.ast.{Exchange, RequestState, SecurityType}

import java.util.UUID
import scala.annotation.meta.field

@Entity
case class RequestContractByProps(
  @(PartitionKey @field)(1) symbol: String,
  //The security's type: STK - stock (or ETF) OPT - option FUT - future IND - index FOP -
  // futures option CASH - forex pair BAG - combo WAR - warrant BOND- bond CMDTY- commodity
  // NEWS- news FUND- mutual fund.
  @(PartitionKey @field)(2) secType: SecurityType,
  //  The destination exchange.
  @(PartitionKey @field)(3) exchange: Exchange,
  //  The option's strike price.
  @(PartitionKey @field)(4) strike: Double = .0d,
  //  Either Put or Call (i.e. Options). Valid values are P, PUT, C, CALL.
  @(PartitionKey @field)(5) right: Option[String],
  //  The instrument's multiplier (i.e. options, futures).
  @(PartitionKey @field)(6) multiplier: Option[String],
  //  The underlying's currency.
  @(PartitionKey @field)(7) currency: Option[String],
  //  The contractReq's symbol within its primary exchange For options, this will be the OCC symbol.
  @(PartitionKey @field)(8) localSymbol: Option[String],
  //  The contractReq's primary exchange. For smart routed contracts, used to define contractReq in
  //  case of ambiguity. Should be defined as native exchange of contractReq, e.g. ISLAND for MSFT
  //  For exchanges which contain a period in name, will only be part of exchange name prior to
  //  period, i.e. ENEXT for ENEXT.BE.
  @(PartitionKey @field)(9) primaryExch: Option[Exchange],
  //  The trading class name for this contractReq. Available in TWS contractReq description window as
  //  well. For example, GBL Dec '13 future's trading class is "FGBL".
  @(PartitionKey @field)(10) tradingClass: Option[String],
  //  Security's identifier when querying contractReq's details or placing orders
  //  ISIN - example:
  //       apple: US0378331005
  //  CUSIP - example:
  //       apple: 037833100
  @(PartitionKey @field)(11) secIdType: Option[String],
  //  Identifier of the security type.
  @(PartitionKey @field)(12) secId: Option[String],
  //  Description of the combo legs.
  @(PartitionKey @field)(13) comboLegsDescription: Option[String],
  //The market name for this product.
  @(PartitionKey @field)(14) marketName: Option[String],
  @(PartitionKey @field)(15) state: RequestState,
  reqId: UUID
)
