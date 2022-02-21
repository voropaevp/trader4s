package model.datastax.ib.feed.ast

import model.datastax.ib.feed.codec.Stringifiable

sealed trait RequestState extends Stringifiable

object RequestState {
  def apply(s: String): RequestState = s match {
    case "pending_id" => PendingId
    case "in_queue"              => InQueue
    case "send_to_broker"        => SendToBroker
    case "rejected_by_broker"    => RejectedByBroker
    case "waiting_for_broker"    => WaitingForBroker
    case "receiving_data"        => ReceivingData
    case "complete"              => Complete
    case "failed"                => Failed
    case "no_data"               => NoData
    case "cancelled"             => Cancelled
    case _                       => Unknown
  }

  case object PendingId extends RequestState {
    override def toString: String = "pending_id"
  }

  case object InQueue extends RequestState {
    override def toString: String = "in_queue"
  }

  case object SendToBroker extends RequestState {
    override def toString: String = "send_to_broker"
  }

  case object RejectedByBroker extends RequestState {
    override def toString: String = "rejected_by_broker"
  }

  case object WaitingForBroker extends RequestState {
    override def toString: String = "waiting_for_broker"
  }

  case object ReceivingData extends RequestState {
    override def toString: String = "receiving_data"
  }

  case object Complete extends RequestState {
    override def toString: String = "complete"
  }

  case object Failed extends RequestState {
    override def toString: String = "failed"
  }

  case object Cancelled extends RequestState {
    override def toString: String = "cancelled"
  }

  case object NoData extends RequestState {
    override def toString: String = "no_data"
  }

  case object Unknown extends RequestState {
    override def toString: String = "unknown"
  }
}
