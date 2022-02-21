package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{ClusteringColumn, Entity, PartitionKey}
import model.datastax.ib.feed.ast.RequestState

import java.time.Instant
import java.util.{Optional, UUID}
import scala.annotation.meta.field

@Entity
case class RequestStateAudit(
  @(PartitionKey @field)(0) reqId: UUID,
  @(PartitionKey @field)(1) state: RequestState,
  @(ClusteringColumn @field) createTime: Instant = Instant.now(),
  rowsReceived: Long,
  error: Optional[String]
)
