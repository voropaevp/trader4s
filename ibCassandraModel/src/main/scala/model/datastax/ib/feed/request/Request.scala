package model.datastax.ib.feed.request

import model.datastax.ib.feed.ast.RequestType

import java.util.UUID

trait Request extends Product with Serializable {
  val reqId: UUID
  val requestType: RequestType
}
