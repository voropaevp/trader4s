package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey}
import model.datastax.ib.feed.ast.{Exchange, SecurityType}

import scala.annotation.meta.field

@Entity
case class ContractByProps(
  @(PartitionKey @field)(1) exchange: Exchange,
  @(PartitionKey @field)(2) symbol: String,
  @(PartitionKey @field)(3) secType: SecurityType,
  @(PartitionKey @field)(4) strike: Double,
  @(PartitionKey @field)(5) right: Option[String],
  @(PartitionKey @field)(6) multiplier: Option[String],
  @(PartitionKey @field)(7) currency: Option[String],
  @(PartitionKey @field)(8) localSymbol: Option[String],
  @(PartitionKey @field)(9) primaryExch: Option[Exchange],
  @(PartitionKey @field)(11) secIdType: Option[String],
  @(PartitionKey @field)(12) secId: Option[String],
  @(PartitionKey @field)(13) marketName: Option[String],
  contId: Int
)
