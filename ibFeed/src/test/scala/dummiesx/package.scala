import cats.data.Validated.Valid
import domain.instruments.{Exchange, MdInstrument, Side, MdSize, Parser, Contract, ContractId, TickerSymbol, SecurityType}

import java.util.Currency
import cats.implicits._
import domain.model.BarRangeRequest

import java.time.{Duration, ZonedDateTime}

package object dummiesx {
  val instrument: MdInstrument = (
    Parser[Exchange].parse("NYSE"),
    Parser[SecurityType].parse("stock"),
    Parser[Currency].parse("USD"),
    Parser[MdSize].parse("1m"),
    Parser[Side].parse("bid")).mapN(
    (exch, tType, currency, size, side)
    => MdInstrument(Ticker(TickerId(1), TickerSymbol("VFF"), exch, tType, currency), size, side)
  ) match {
    case Valid(a) => a
    case _ => throw new Exception("bad instrument")
  }

  val rangeRequest: BarRangeRequest = BarRangeRequest(instrument, ZonedDateTime.now().minusDays(5), ZonedDateTime.now())

}
