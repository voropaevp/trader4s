package model.datastax.ib.feed.response.data
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.mapper.annotations.{CqlName, Dao, Insert, Query, Select}
import model.datastax.ib.feed.ast.{BarSize, DataType}

import java.time.Instant
import java.util.concurrent.CompletionStage

@Dao
trait BarDao {
  @Insert
  def create(bar: Bar): CompletionStage[Void]

  @Select(customWhereClause = """
      cont_id = :contId
      AND size = :sizeId
      AND data_type = :dataType
      AND ts >= :start 
      AND ts < :end     
    """, limit = ":qLimit")
  def selectRangeLimit(
    @CqlName("contId") contId: Int,
    @CqlName("sizeId") size: BarSize,
    @CqlName("dataType") dataType: DataType,
    @CqlName("start") start: Instant,
    @CqlName("end") end: Instant,
    @CqlName("qLimit") limit: Int
  ): CompletionStage[MappedAsyncPagingIterable[Bar]]

//  @Select(customWhereClause = """
//      WHERE cont_id = :contId
//      AND size = :sizeId
//      AND date >= :startDate
//      AND date < :endDate
//      AND ts >= :start
//      AND ts < :end
//    """)
//  def selectRange(
//    @CqlName("contId") contId: Int,
//    @CqlName("sizeId") size: BarSize,
//    @CqlName("startDate") startDate: String,
//    @CqlName("endDate") endDate: String,
//    @CqlName("start") start: Instant,
//    @CqlName("end") end: Instant
//  ): CompletionStage[MappedAsyncPagingIterable[Bar]]

  @Query("""
      SELECT  
        ts
      FROM ${keyspaceId}.bar
      WHERE cont_id = :contId
        AND size = :size
        AND data_type = :dataType
      ORDER BY ts DESC
      LIMIT 1
    """)
  def headTs(
    @CqlName("contId") contId: Int,
    @CqlName("size") size: BarSize,
    @CqlName("dataType") dataType: DataType
  ): CompletionStage[Instant]

  @Query("""
      SELECT  
        ts
      FROM ${keyspaceId}.bar
      WHERE cont_id = :contId
        AND size = :sizeId
        AND data_type = :dataType
      ORDER BY ts ASC
      LIMIT 1
    """)
  def tailTs(
    @CqlName("contId") contId: Int,
    @CqlName("sizeId") size: BarSize,
    @CqlName("dataType") dataType: DataType
  ): CompletionStage[Instant]
}
