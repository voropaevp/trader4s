package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey}
import model.datastax.ib.feed.ast.{Exchange, RequestState, SecurityType}

import java.util.UUID
import scala.annotation.meta.field

@Entity
case class RequestContractByEntryHash(
  @(PartitionKey @field)(1) entryHash: Int,
  @(PartitionKey @field)(2) state: RequestState,
  reqId: UUID
)
