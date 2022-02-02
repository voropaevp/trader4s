package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{ClusteringColumn, Entity, PartitionKey}
import model.datastax.ib.feed.ast.RequestState

import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field

@Entity
case class RequestStateAudit(
  @(PartitionKey @field) reqId: UUID,
  @(ClusteringColumn @field) createTime: Instant = Instant.now(),
  state: RequestState,
  rowsReceived: Option[Long] = None,
  error: Option[String] = None
)
