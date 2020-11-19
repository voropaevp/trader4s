package model.datastax.ib.feed.ast

import model.datastax.ib.feed.codec.Stringifiable

sealed trait RequestType extends Stringifiable

object RequestType {
  def apply(s: String): RequestType = s match {
    case "SUB"  => Subscription
    case "HIS"  => Historic
    case "CONT" => ContractDetails
    case _      => Unknown
  }

  case object Subscription extends RequestType {
    override def toString: String = "SUB"
  }

  case object Historic extends RequestType {
    override def toString: String = "HIS"
  }

  case object ContractDetails extends RequestType {
    override def toString: String = "CONT"
  }

  case object Unknown extends RequestType {
    override def toString: String = "Unknown"
  }
}
