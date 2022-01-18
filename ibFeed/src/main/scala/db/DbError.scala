package db

import domain.feed.FeedException

case class DbError(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)
    with FeedException





