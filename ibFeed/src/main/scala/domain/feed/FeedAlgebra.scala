package domain.feed

import cats.data.EitherT
import model.datastax.ib.feed.request.{RequestContract, RequestData}
import fs2._
import model.datastax.ib.feed.response.contract.Contract
import model.datastax.ib.feed.response.data.Bar

trait FeedAlgebra[F[_]] {
  def requestHistBarData(request: RequestData): EitherT[F, FeedException,  QueuedFeedRequest[F]]

  def subscribeBarData(request: RequestData): EitherT[F, FeedException,  QueuedFeedRequest[F]]

  def requestContractDetails(request: RequestContract): EitherT[F, FeedException, QueuedFeedRequest[F]]
}

object FeedAlgebra {
  def apply[F[_]](implicit ev: FeedAlgebra[F]): FeedAlgebra[F] = ev
}
