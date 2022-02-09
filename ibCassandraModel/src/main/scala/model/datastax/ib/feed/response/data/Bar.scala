package model.datastax.ib.feed.response.data

import com.datastax.oss.driver.api.mapper.annotations.{ClusteringColumn, Entity, PartitionKey}
import model.datastax.ib.feed.ast.{BarSize, DataType}
import model.datastax.ib.feed.response.Response
import com.ib.client.{Bar => IbBar}

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Optional
import scala.annotation.meta.field
import scala.jdk.OptionConverters.RichOption

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
  count: Int,
  wap: Double,
  extra: String
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
    count    = ibBar.count(),
    wap      = ibBar.wap(),
    extra    = ""
  )
}
