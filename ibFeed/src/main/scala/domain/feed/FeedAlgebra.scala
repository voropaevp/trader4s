package domain.feed

import fs2._
import model.datastax.ib.feed.request.{RequestContract, RequestData}
import model.datastax.ib.feed.response.contract.Contract
import model.datastax.ib.feed.response.data.Bar

trait FeedAlgebra[F[_]] {
  def requestHistBarData(request: RequestData): Stream[F, Bar]

  def subscribeBarData(request: RequestData): Stream[F, Bar]

  def requestContractDetails(request: RequestContract): Stream[F, Contract]
}

object FeedAlgebra {
  def apply[F[_]](implicit ev: FeedAlgebra[F]): FeedAlgebra[F] = ev
}
