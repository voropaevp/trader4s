package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.{Entity, Transient}
import com.ib.client.{DeltaNeutralContract => IbDeltaNeutralContract}

@Entity
case class DeltaNeutralContract(
  contId: Int,
  delta: Double,
  price: Double
) {
  @Transient def toIb: IbDeltaNeutralContract = new IbDeltaNeutralContract(contId, delta, price)
}

object DeltaNeutralContract {
  def apply(d: IbDeltaNeutralContract): DeltaNeutralContract = new DeltaNeutralContract(d.conid, d.delta, d.price)
}
