package utils.config

import pureconfig._
import pureconfig.generic.auto._
import model.datastax.ib.feed.ast.{BarSize, DataType, Exchange, SecurityType}
import java.util.UUID
import model.datastax.ib.feed.request.RequestContract

import scala.concurrent.duration.FiniteDuration

object Config {

  case class Limits(
    concurrentSubLimit: Int,
    concurrentHistoricLimit: Int,
    sameContractAndSizeLimit: Int,
    waitQueueSize: Int
  )

  case class ContractEntry(
    exchange: Exchange,
    symbol: String,
    secType: SecurityType,
    strikeOpt: Option[Double],
    right: Option[String],
    multiplier: Option[String],
    currency: Option[String],
    localSymbol: Option[String],
    primaryExch: Option[Exchange],
    secIdType: Option[String],
    secId: Option[String],
    marketName: Option[String]
  ) {
    def strike: Double = strikeOpt.getOrElse(.0d)

    def toContractRequest: RequestContract = RequestContract(
      symbol      = symbol,
      secType     = secType,
      exchange    = exchange,
      strike      = strikeOpt,
      right       = right,
      multiplier  = multiplier,
      currency    = currency,
      localSymbol = localSymbol,
      primaryExch = primaryExch,
      secIdType   = secIdType,
      secId       = secId,
      marketName  = marketName,
      reqId       = UUID.randomUUID()
    )
  }

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
