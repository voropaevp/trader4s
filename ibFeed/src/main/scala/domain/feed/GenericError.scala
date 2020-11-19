package domain.feed

trait FeedError

case class GenericError(private val message: String = "", private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
    with FeedError

