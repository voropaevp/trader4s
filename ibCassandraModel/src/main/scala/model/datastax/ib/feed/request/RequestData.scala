package model.datastax.ib.feed.request

import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey}
import model.datastax.ib.feed.ast.{BarSize, DataType, RequestState, RequestType}

import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field
@Entity
case class RequestData(
  @(PartitionKey @field)(1) reqId: UUID,
  reqType: RequestType,
  size: BarSize,
  contId: Int,
  dataType: DataType,
  state: RequestState,
  startTime: Instant,
  endTime: Instant,
  createTime: Option[Instant] = None,
  updateTime: Instant         = Instant.now()
) extends Request
