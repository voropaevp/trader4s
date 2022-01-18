package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{Computed, Entity, PartitionKey, Transient}
import model.datastax.ib.feed.ast.{BarSize, DataType, RequestState, RequestType}

import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field
@Entity
case class RequestData(
  @(PartitionKey @field)(1) reqId: UUID,
  requestType: RequestType,
  size: BarSize,
  contId: Int,
  dataType: DataType,
  state: RequestState = RequestState.PendingId,
  startTime: Instant,
  endTime: Instant,
  @(Computed @field)("writetime(reqId)") createTime: Instant = Instant.MIN,
  @(Computed @field)("writetime(state)") updateTime: Instant = Instant.MIN
) extends Request {
  @Transient lazy val toProps: RequestDataByProps =
    RequestDataByProps(
      reqType   = this.requestType,
      contId    = this.contId,
      dataType  = this.dataType,
      state     = this.state,
      startTime = this.startTime,
      reqId     = Set(this.reqId)
    )
}
