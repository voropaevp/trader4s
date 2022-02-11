package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey}
import scala.annotation.meta.field

@Entity
case class ContractByEntryHash(
  @(PartitionKey @field) entryHash: Int,
  contId: Int
)
