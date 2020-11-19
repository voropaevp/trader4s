package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.MapperContext
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import model.datastax.ib.Utils._

import java.util.concurrent.CompletionStage

class ContractQueryProvider(
  val context: MapperContext,
  val contractHelper: EntityHelper[Contract],
  val contractByProps: EntityHelper[ContractByProps]
) {
  private val session                       = context.getSession
  private val preparedInsertContract        = prepareInsert(session, contractHelper)
  private val preparedInsertContractByProps = prepareInsert(session, contractByProps)

  def create(contract: Contract): CompletionStage[Unit] = batch(
    Seq(
      bind(preparedInsertContract, contract, contractHelper),
      bind(
        preparedInsertContractByProps,
        contractToContractByProps(contract),
        contractByProps
      )
    ),
    session
  )

  private def contractToContractByProps(contract: Contract) =
    ContractByProps(
      contract.exchange,
      contract.symbol,
      contract.secType,
      contract.strike,
      contract.right,
      contract.multiplier,
      contract.currency,
      contract.localSymbol,
      contract.primaryExch,
      contract.secIdType,
      contract.secId,
      contract.marketName,
      contract.contId
    )
}
