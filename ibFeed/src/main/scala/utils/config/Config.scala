package utils.config

import pureconfig._
import pureconfig.generic.auto._
import model.datastax.ib.feed.ast.{BarSize, DataType, Exchange, SecurityType}
import model.datastax.ib.feed.response.contract.{ComboLeg, Contract, DeltaNeutralContract}
import com.typesafe.config._
import com.ib.client.{Contract => IbContact}
import model.datastax.ib.feed.request.RequestContract

import scala.concurrent.duration.FiniteDuration

object Config {

  case class ContractEntry(
    exchange: Exchange,
    symbol: String,
    secType: SecurityType,
    strike: Double,
    right: Option[String],
    multiplier: Option[String],
    currency: Option[String],
    localSymbol: Option[String],
    primaryExch: Option[Exchange],
    secIdType: Option[String],
    secId: Option[String],
    marketName: Option[String]
  )

  case class WatchEntry(
    contract: ContractEntry,
    dataType: DataType,
    size: BarSize,
    syncInterval: FiniteDuration
  )

  implicit val myDataTypeReader: ConfigReader[DataType] = ConfigReader[String].map(DataType.apply)
  implicit val myBarSizeReader: ConfigReader[BarSize]   = ConfigReader[String].map(BarSize.apply)
  implicit val myExchReader: ConfigReader[Exchange]     = ConfigReader[String].map(Exchange.apply)

  case class BrokerSettings(ip: String, port: Int, requestTimeout: FiniteDuration, clientId: Int)

  case class AppSettings(broker: BrokerSettings, watchList: List[WatchEntry])

  ConfigSource.default.load[AppSettings]
}
