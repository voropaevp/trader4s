package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._

import java.util.concurrent.CompletionStage
import scala.jdk.OptionConverters.RichOptional

class ContractQueryProvider(
  val context: MapperContext,
  val contractHelper: EntityHelper[Contract],
  val contractByEntryHash: EntityHelper[ContractByEntryHash]
) {
  private val session                           = context.getSession
  private val preparedInsertContract            = prepareInsert(session, contractHelper)
  private val preparedInsertContractByEntryHash = prepareInsert(session, contractByEntryHash)

  def create(contract: Contract): CompletionStage[Unit] = batch(
    Seq(
      bind(preparedInsertContract, contract, contractHelper),
      bind(
        preparedInsertContractByEntryHash,
        ContractByEntryHash(contract.entryHash, contract.contId),
        contractByEntryHash
      )
    ),
    session
  )
}
