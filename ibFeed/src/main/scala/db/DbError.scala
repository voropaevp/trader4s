package db

import domain.feed.FeedException

case class DbError(message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)
    with FeedException





