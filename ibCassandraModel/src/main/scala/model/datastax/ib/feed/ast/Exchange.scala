package model.datastax.ib.feed.ast

import model.datastax.ib.feed.codec.Stringifiable

import java.util.TimeZone

sealed trait Exchange extends Stringifiable {
  override def toString: String = name

  def name: String
  def timeZone: TimeZone
}

object Exchange {
  def apply(s: String): Exchange = s match {
    case "NYSE"   => Exchange.NYSE
    case "ISLAND" => Exchange.ISLAND
    case _        => Exchange.UNKNOWN
  }
  case object NYSE extends Exchange {
    override def name = "NYSE"

    override def timeZone: TimeZone = TimeZone.getTimeZone("America/New_York")
  }

  case object ISLAND extends Exchange {
    override def name = "ISLAND"

    override def timeZone: TimeZone = TimeZone.getTimeZone("America/New_York")
  }

  case object UNKNOWN extends Exchange {
    override def name = "UNKNOWN"

    override def timeZone: TimeZone = TimeZone.getTimeZone("America/New_York")
  }
}
