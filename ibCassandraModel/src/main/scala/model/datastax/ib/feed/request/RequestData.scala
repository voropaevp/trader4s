package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{Computed, Entity, PartitionKey, Transient}
import model.datastax.ib.feed.ast.{BarSize, DataType, RequestState, RequestType}

import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field
@Entity
case class RequestData(
  @(PartitionKey @field) reqId: UUID = UUID.randomUUID(),
  requestType: RequestType,
  size: BarSize,
  contId: Int,
  dataType: DataType,
  state: RequestState,
  startTime: Instant,
  endTime: Instant,
  @(Computed @field)("writetime(cont_id)") createTime: Long = 0,
  @(Computed @field)("writetime(state)") updateTime: Long   = 0
) extends Request {
  @Transient lazy val toProps: RequestDataByProps =
    RequestDataByProps(
      requestType = this.requestType,
      contId      = this.contId,
      size        = this.size,
      dataType    = this.dataType,
      state       = this.state,
      startTime   = this.startTime,
      reqId       = this.reqId,
      createTime  = Instant.ofEpochMilli(this.updateTime)
    )
}
