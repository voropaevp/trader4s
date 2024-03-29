package model.datastax.ib.feed.response.data

import com.datastax.oss.driver.api.mapper.annotations.{ClusteringColumn, Entity, PartitionKey}
import model.datastax.ib.feed.ast.{BarSize, DataType}
import model.datastax.ib.feed.response.Response
import com.ib.client.{Bar => IbBar}

import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.annotation.meta.field

@Entity
case class Bar(
  @(PartitionKey @field)(1) contId: Int,
  @(PartitionKey @field)(2) size: BarSize,
  @(PartitionKey @field)(3) dataType: DataType,
  @(ClusteringColumn @field) ts: Instant,
  open: Double,
  high: Double,
  low: Double,
  close: Double,
  volume: Long,
  count: Option[Int],
  wap: Option[Double],
  extra: Option[String]
) extends Response

object Bar {
  private val dateParser = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")
  def apply(ibBar: IbBar, contId: Int, dataType: DataType, size: BarSize) = new Bar(
    contId   = contId,
    size     = size,
    dataType = dataType,
    ts       = Instant.from(dateParser.parse(ibBar.time())),
    open     = ibBar.open(),
    high     = ibBar.high(),
    low      = ibBar.low(),
    close    = ibBar.close(),
    volume   = ibBar.volume(),
    count    = Some(ibBar.count()),
    wap      = Some(ibBar.wap()),
    extra    = None
  )
}
