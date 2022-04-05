import cats.implicits.catsSyntaxApply
import cats.{Applicative, MonadThrow}
import domain.feed.FeedException
import pureconfig._
import pureconfig.generic.auto._
import model.datastax.ib.feed.ast.{BarSize, DataType, Exchange, SecurityType}

import java.util.UUID
import model.datastax.ib.feed.request.RequestContract
import model.datastax.ib.feed.response.contract.{ComboLeg, ContractEntry}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

package object config {

  case class LimitsConfig(
    clientMsgLimit: Int,
    clientMsgDuration: FiniteDuration,
    concurrentSubLimit: Int,
    hist10MinLimit: Int,
    hist10MinDuration: FiniteDuration,
    sameContractAndSizeLimit: Int,
    sameContractAndSizeDuration: FiniteDuration
  )

  case class WatchEntry(
    contractEntry: ContractEntry,
    dataType: DataType,
    size: BarSize
  )

  implicit val myDataTypeReader: ConfigReader[DataType]  = ConfigReader[String].map(DataType.apply)
  implicit val myBarSizeReader: ConfigReader[BarSize]    = ConfigReader[String].map(BarSize.apply)
  implicit val secTypeReader: ConfigReader[SecurityType] = ConfigReader[String].map(SecurityType.apply)
  implicit val myExchReader: ConfigReader[Exchange]      = ConfigReader[String].map(Exchange.apply)

  case class BrokerSettings(
    ip: String,
    port: Int,
    requestTimeout: FiniteDuration,
    clientId: Int,
    limits: LimitsConfig,
    nThreads: Int
  )

  case class AppSettings(
    broker: BrokerSettings,
    watchList: List[WatchEntry]
  )

  def parse[F[_]: Logger: MonadThrow: Applicative]: F[AppSettings] =
    ConfigSource
      .default
      .load[AppSettings]
      .fold(
        err =>
          Logger[F].error(err.prettyPrint()) *>
            MonadThrow[F].raiseError(new FeedException {
              override def message: String = err.prettyPrint()
            }),
        Applicative[F].pure
      )
}
