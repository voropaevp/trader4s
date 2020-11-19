package model.datastax.ib.feed.ast
import com.ib.client.Types.WhatToShow
import model.datastax.ib.feed.codec.Stringifiable

trait DataType extends Stringifiable {
  def asIb: WhatToShow
}

object DataType {
  def apply(s: String): DataType = s match {
    case "B"  => Bid
    case "A"  => Ask
    case "M"  => Midpoint
    case "BA" => BidAsk
    case "T"  => Trades
    case "IV" => ImpliedVolatility
    case "HV" => HistoricalVolatility
    case _    => Unknown
  }
  case object Bid extends DataType {
    override def asIb: WhatToShow = WhatToShow.BID
    override def toString: String = "B"
  }
  case object Ask extends DataType {
    override def asIb: WhatToShow = WhatToShow.ASK
    override def toString: String = "A"
  }
  case object Midpoint extends DataType {
    override def asIb: WhatToShow = WhatToShow.MIDPOINT
    override def toString: String = "M"
  }
  case object BidAsk extends DataType {
    override def asIb: WhatToShow = WhatToShow.BID_ASK
    override def toString: String = "BA"
  }
  case object Trades extends DataType {
    override def asIb: WhatToShow = WhatToShow.TRADES
    override def toString: String = "T"
  }
  case object HistoricalVolatility extends DataType {
    override def asIb: WhatToShow = WhatToShow.HISTORICAL_VOLATILITY
    override def toString: String = "HV"
  }
  case object ImpliedVolatility extends DataType {
    override def asIb: WhatToShow = WhatToShow.OPTION_IMPLIED_VOLATILITY
    override def toString: String = "IV"
  }
  case object Unknown extends DataType {
    override def asIb: WhatToShow = WhatToShow.MIDPOINT
    override def toString: String = "Unknown"
  }
}
