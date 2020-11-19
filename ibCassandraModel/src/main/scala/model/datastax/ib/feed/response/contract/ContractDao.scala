package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.mapper.annotations.{CqlName, Dao, Query, QueryProvider, Select}
import model.datastax.ib.feed.ast.{Exchange, SecurityType}

import java.util.concurrent.CompletionStage

@Dao
trait ContractDao {
  @Select
  def get(contId: Int): CompletionStage[Option[Contract]]

  @Select
  def getByProps(
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
  ): CompletionStage[Option[ContractByProps]]

  @QueryProvider(
    providerClass = classOf[ContractQueryProvider],
    entityHelpers = Array(classOf[Contract], classOf[ContractByProps])
  )
  def create(contract: Contract): CompletionStage[Unit]
}
