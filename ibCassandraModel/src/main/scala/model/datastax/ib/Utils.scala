package model.datastax.ib

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{BatchStatement, BoundStatement, DefaultBatchType, PreparedStatement}
import com.datastax.oss.driver.api.mapper.entity.EntityHelper
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy

import java.util.concurrent.CompletionStage

object Utils {
  def prepareInsert[T](session: CqlSession, entityHelper: EntityHelper[T]): PreparedStatement =
    session.prepare(entityHelper.insert.asCql)

  def deleteByPrimaryKey[T](session: CqlSession, entityHelper: EntityHelper[T]): PreparedStatement =
    session.prepare(entityHelper.deleteByPrimaryKey().asCql)

  def prepareUpdate[T](session: CqlSession, entityHelper: EntityHelper[T]): PreparedStatement =
    session.prepare(entityHelper.updateByPrimaryKey().asCql)

  def batch[T](iterable: Iterable[BoundStatement], session: CqlSession): CompletionStage[Unit] = {
    val batch = BatchStatement.builder(DefaultBatchType.LOGGED)
    iterable.foreach(batch.addStatement)
    session.executeAsync(batch.build)
      .thenApply(_ => ())
  }

  def bind[T](preparedStatement: PreparedStatement, entity: T, entityHelper: EntityHelper[T]): BoundStatement = {
    val boundStatement = preparedStatement.boundStatementBuilder()
    entityHelper.set(entity, boundStatement, NullSavingStrategy.DO_NOT_SET, true)
    boundStatement.build
  }

}
