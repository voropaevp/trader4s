package model.datastax.ib.feed

import com.datastax.oss.driver.api.mapper.annotations.{DaoFactory, Mapper}
import model.datastax.ib.feed.request.RequestDao
import model.datastax.ib.feed.response.contract.ContractDao
import model.datastax.ib.feed.response.data.BarDao

@Mapper
trait FeedMapper {

  @DaoFactory
  def BarDao(): BarDao

  @DaoFactory
  def ContractDao(): ContractDao

  @DaoFactory
  def RequestDao(): RequestDao
}
