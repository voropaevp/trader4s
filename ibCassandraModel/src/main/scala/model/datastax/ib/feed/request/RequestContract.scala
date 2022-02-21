package model.datastax.ib.feed.request
import com.datastax.oss.driver.api.mapper.annotations.{Computed, Entity, PartitionKey, Transient}
import model.datastax.ib.feed.ast.{Exchange, RequestState, RequestType, SecurityType}
import model.datastax.ib.feed.response.contract.{ComboLeg, ContractEntry, DeltaNeutralContract}

import java.time.Instant
import java.util.{Optional, UUID, List => JList}
import scala.annotation.meta.field
import scala.jdk.OptionConverters.{RichOption, RichOptional}
import scala.jdk.CollectionConverters._

@Entity
case class RequestContract(
  @(PartitionKey @field) reqId: UUID,
  symbol: String,
  secType: SecurityType,
  lastTradeDateOrContractMonth: Optional[String],
  strike: Double,
  right: Optional[String],
  multiplier: Optional[String],
  exchange: Exchange,
  currency: Optional[String],
  localSymbol: Optional[String],
  tradingClass: Optional[String],
  includeExpired: Boolean = false,
  comboLegs: JList[ComboLeg],
  primaryExch: Optional[Exchange],
  secIdType: Optional[String],
  secId: Optional[String],
  comboLegsDescription: Optional[String],
  deltaNeutralContract: DeltaNeutralContract,
  state: RequestState                                      = RequestState.PendingId,
  @(Computed @field)("writetime(symbol)") createTime: Long = 0,
  @(Computed @field)("writetime(state)") updateTime: Long  = 0
) extends Request {
  @Transient val requestType: RequestType = RequestType.ContractDetails

  @Transient val asContractEntry: ContractEntry = ContractEntry(
    symbol                       = symbol,
    secType                      = secType,
    strike                       = Option.when(strike > .000001)(strike),
    lastTradeDateOrContractMonth = lastTradeDateOrContractMonth.toScala,
    right                        = right.toScala,
    multiplier                   = multiplier.toScala,
    exchange                     = exchange,
    currency                     = currency.toScala,
    localSymbol                  = localSymbol.toScala,
    tradingClass                 = tradingClass.toScala,
    includeExpired               = Option.when(includeExpired)(includeExpired),
    comboLegs                    = Option(comboLegs).map(_.asScala.toList),
    primaryExch                  = primaryExch.toScala,
    secIdType                    = secIdType.toScala,
    secId                        = secId.toScala,
    comboLegsDescription         = comboLegsDescription.toScala,
    deltaNeutralContract         = Option(deltaNeutralContract)
  )
}

object RequestContract {

  def apply(contractEntry: ContractEntry): RequestContract = new RequestContract(
    reqId                        = UUID.randomUUID(),
    symbol                       = contractEntry.symbol,
    secType                      = contractEntry.secType,
    strike                       = contractEntry.strike.getOrElse(.0d),
    lastTradeDateOrContractMonth = contractEntry.lastTradeDateOrContractMonth.toJava,
    right                        = contractEntry.right.toJava,
    multiplier                   = contractEntry.multiplier.toJava,
    exchange                     = contractEntry.exchange,
    currency                     = contractEntry.currency.toJava,
    localSymbol                  = contractEntry.localSymbol.toJava,
    tradingClass                 = contractEntry.tradingClass.toJava,
    includeExpired               = contractEntry.includeExpired.getOrElse(false),
    comboLegs                    = contractEntry.comboLegs.map(legs => JList.of(legs: _*)).orNull,
    primaryExch                  = contractEntry.primaryExch.toJava,
    secIdType                    = contractEntry.secIdType.toJava,
    secId                        = contractEntry.secId.toJava,
    state                        = RequestState.PendingId,
    comboLegsDescription         = contractEntry.comboLegsDescription.toJava,
    deltaNeutralContract         = contractEntry.deltaNeutralContract.orNull
  )
}
