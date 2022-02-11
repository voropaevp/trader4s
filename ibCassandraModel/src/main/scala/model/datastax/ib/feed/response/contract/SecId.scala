package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.Entity

@Entity
case class SecId(
  m_tag: String,
  m_value: String
)
