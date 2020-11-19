package model.datastax.ib.feed.ast

import model.datastax.ib.feed.codec.Stringifiable
import com.ib.client.Types.SecType

sealed trait SecurityType extends Stringifiable {
  def asIb: SecType
}

object SecurityType {
  def apply(s: String): SecurityType = s match {
    case "STK" => Stock
    case _     => Unknown
  }

  case object Stock extends SecurityType {
    override def toString: String = "STK"

    override def asIb: SecType = SecType.STK
  }

  case object Unknown extends SecurityType {
    override def toString: String = "Unknown"

    override def asIb: SecType    = SecType.STK
  }
}
