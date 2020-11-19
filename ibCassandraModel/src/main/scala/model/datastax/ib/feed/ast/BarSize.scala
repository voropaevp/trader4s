package model.datastax.ib.feed.ast

import model.datastax.ib.feed.codec.Stringifiable
import com.ib.client.Types.{BarSize => IbBarSize}

import java.time.Duration

sealed trait BarSize extends Stringifiable {
  def asIb: IbBarSize
  def ibBackStep: Duration
  override def toString: String = asIb.toString
}

object BarSize {
  def apply(s: String): BarSize = s match {
    case "1S"  => Secs1
    case "5S"  => Secs5
    case "10S" => Secs10
    case "15S" => Secs15
    case "30S" => Secs30
    case "1M"  => Min1
    case "2M"  => Mins2
    case "3M"  => Mins3
    case "5M"  => Mins5
    case "10M" => Mins10
    case "15M" => Mins15
    case "20M" => Mins20
    case "30M" => Mins30
    case "1H"  => Hour1
    case "4H"  => Hours4
    case "1D"  => Day1
    case "W"   => Week1
    case "M"   => Month1
    case _     => Unknown
  }

  //Duration	Allowed Bar Sizes
  //60 S	1 sec - 1 mins
  //120 S	1 sec - 2 mins
  //1800 S (30 mins)	1 sec - 30 mins
  //3600 S (1 hr)	5 secs - 1 hr
  //14400 S (4hr)	10 secs - 3 hrs
  //28800 S (8 hrs)	30 secs - 8 hrs
  //1 D	1 min - 1 day
  //2 D	2 mins - 1 day
  //1 W	3 mins - 1 week
  //1 M	30 mins - 1 month
  //1 Y	1 day - 1 month

  case object Secs1 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_secs
    override def ibBackStep: Duration = Duration.ofMinutes(30L)
    override def toString: String     = "1S"
  }

  case object Secs5 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._5_secs
    override def ibBackStep: Duration = Duration.ofHours(1L)
    override def toString: String     = "5S"
  }

  case object Secs10 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._10_secs
    override def ibBackStep: Duration = Duration.ofHours(3L)
    override def toString: String     = "10S"
  }

  case object Secs15 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._15_secs
    override def ibBackStep: Duration = Duration.ofHours(3L)
    override def toString: String     = "15S"
  }

  case object Secs30 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._30_secs
    override def ibBackStep: Duration = Duration.ofHours(8L)
    override def toString: String     = "30S"
  }

  case object Min1 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_min
    override def ibBackStep: Duration = Duration.ofDays(1L)
    override def toString: String     = "1M"
  }

  case object Mins2 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._2_mins
    override def ibBackStep: Duration = Duration.ofDays(2L)
    override def toString: String     = "2M"
  }

  case object Mins3 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._3_mins
    override def ibBackStep: Duration = Duration.ofDays(7L)
    override def toString: String     = "3M"
  }

  case object Mins5 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._5_mins
    override def ibBackStep: Duration = Duration.ofDays(7L)
    override def toString: String     = "5M"
  }

  case object Mins10 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._10_mins
    override def ibBackStep: Duration = Duration.ofDays(7L)
    override def toString: String     = "10M"
  }

  case object Mins15 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._15_mins
    override def ibBackStep: Duration = Duration.ofDays(7L)
    override def toString: String     = "15M"
  }

  case object Mins20 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._20_mins
    override def ibBackStep: Duration = Duration.ofDays(7L)
    override def toString: String     = "20M"
  }

  case object Mins30 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._30_mins
    override def ibBackStep: Duration = Duration.ofDays(31L)
    override def toString: String     = "30M"
  }

  case object Hour1 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_hour
    override def ibBackStep: Duration = Duration.ofDays(31L)
    override def toString: String     = "1H"
  }

  case object Hours4 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._4_hours
    override def ibBackStep: Duration = Duration.ofDays(30L)
    override def toString: String     = "4H"
  }

  case object Day1 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_day
    override def ibBackStep: Duration = Duration.ofDays(365L)
    override def toString: String     = "1D"
  }

  case object Week1 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_week
    override def ibBackStep: Duration = Duration.ofDays(365L)
    override def toString: String     = "W"
  }

  case object Month1 extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_month
    override def ibBackStep: Duration = Duration.ofDays(365L)
    override def toString: String     = "M"
  }

  case object Unknown extends BarSize {
    override def asIb: IbBarSize      = IbBarSize._1_secs
    override def ibBackStep: Duration = Duration.ofDays(365L)
    override def toString: String     = "UNKNOWN"
  }
}
