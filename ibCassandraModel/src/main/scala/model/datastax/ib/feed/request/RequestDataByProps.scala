package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{ClusteringColumn, Computed, Entity, PartitionKey}
import model.datastax.ib.feed.ast.{BarSize, DataType, RequestState, RequestType}

import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field

@Entity
case class RequestDataByProps(
  @(PartitionKey @field)(1) requestType: RequestType,
  @(PartitionKey @field)(2) contId: Int,
  @(PartitionKey @field)(3) dataType: DataType,
  @(PartitionKey @field)(4) size: BarSize,
  @(PartitionKey @field)(5) state: RequestState,
  @(ClusteringColumn @field) startTime: Instant,
  reqId: UUID,
  createTime: Instant,
  @(Computed @field)("writetime(req_id)") updateTime: Long = 0L
)
