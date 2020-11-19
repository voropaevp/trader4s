package domain.feed

import cats.data.EitherT
import model.datastax.ib.feed.request.{RequestData, RequestContract}

trait FeedAlgebra[F[_]] {
  def requestHistBarData(request: RequestData): EitherT[F, FeedError, QueuedFeedRequest[F]]

  def subscribeBarData(request: RequestData): EitherT[F, FeedError, QueuedFeedRequest[F]]

  def requestContractDetails(request: RequestContract): EitherT[F, FeedError, QueuedFeedRequest[F]]
}
