package domain.feed

import scala.concurrent.duration.FiniteDuration

trait FeedException extends Throwable {
  def message: String
}

object FeedException {
  case class RequestTypeException(message: String) extends FeedException

  case class IbReqError(code: Int, message: String = "", private val cause: Throwable = None.orNull)
      extends Exception(message, cause)
      with FeedException

  case class IbCriticalError(message: String = "", private val cause: Throwable = None.orNull)
      extends Exception(message, cause)
      with FeedException

  case class RequestTimeout(t: FiniteDuration) extends FeedException {
    override def message: String = s"Request timed out after $t of inactivity"
  }

  case object FeedShutdown extends FeedException {
    override def message: String = s"Feed is shutting down"
  }

  case object GatewayInactive extends FeedException {
    override def message: String = s"TWS gateway is not connected"
  }

  case class CriticalFeedError(message: String = "", private val cause: Throwable = None.orNull)
      extends Error(message, cause)
      with FeedException
}
