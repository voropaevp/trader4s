package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.mapper.annotations.{Dao, QueryProvider, Select}
import model.datastax.ib.feed.ast.{Exchange, SecurityType}

import java.util.Optional
import java.util.concurrent.CompletionStage

@Dao
trait ContractDao {
  @Select
  def get(contId: Int): CompletionStage[Optional[Contract]]

  @Select
  def getByEntryHash(entryHash: Int): CompletionStage[Optional[ContractByEntryHash]]

  @QueryProvider(
    providerClass = classOf[ContractQueryProvider],
    entityHelpers = Array(classOf[Contract], classOf[ContractByEntryHash])
  )
  def create(contract: Contract): CompletionStage[Unit]
}
