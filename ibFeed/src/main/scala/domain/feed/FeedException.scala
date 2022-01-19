package domain.feed

trait FeedException extends Throwable {
  def message: String
}

object FeedException {
  case class RequestTypeException(message: String) extends FeedException

  case class IbError(message: String = "", private val cause: Throwable = None.orNull)
      extends Exception(message, cause)
      with FeedException

  case class CriticalFeedError(message: String = "", private val cause: Throwable = None.orNull)
      extends Error(message, cause)
      with FeedException
}
