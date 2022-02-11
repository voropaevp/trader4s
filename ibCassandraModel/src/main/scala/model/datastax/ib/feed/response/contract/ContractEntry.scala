package model.datastax.ib.feed.response.contract

import model.datastax.ib.feed.ast.{Exchange, SecurityType}

case class ContractEntry(
  symbol: String,
  secType: SecurityType,
  lastTradeDateOrContractMonth: Option[String],
  strike: Option[Double],
  right: Option[String],
  multiplier: Option[String],
  exchange: Exchange,
  currency: Option[String],
  localSymbol: Option[String],
  tradingClass: Option[String],
  includeExpired: Option[Boolean],
  comboLegs: Option[List[ComboLeg]],
  primaryExch: Option[Exchange],
  secIdType: Option[String],
  secId: Option[String],
  comboLegsDescription: Option[String],
  deltaNeutralContract: Option[DeltaNeutralContract]
)
