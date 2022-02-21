package db.dao

import cats.syntax.all._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import db.ConnectedDao.RequestDaoConnected
import db.{ConnectedDao, TestDbSpec}
import model.datastax.ib.feed.ast._

class RequestDaoSpec extends TestDbSpec {

  "Hash of contract request should be the same as original entry" in {
    mockContractReq.asContractEntry shouldBe mockContractEntry
  }

  "Be able to insert request contract row and get it back" in {
    (flushKeyspace("test_req_dao_1_", "meta") >>
      ConnectedDao.initWithPrefix[IO]("test_req_dao_1_").use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          RequestDaoConnected[IO].create(mockContractReq) >>
            RequestDaoConnected[IO].getContractEntryState(mockContractEntry, RequestState.PendingId)

      }).unsafeRunSync().map(_.reqId) shouldBe Some(mockContractReq.reqId)
  }

  "Be able to insert request data row and get it back" in {
    (flushKeyspace("test_req_dao_2_", "meta") >>
      ConnectedDao.initWithPrefix[IO]("test_req_dao_2_").use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          RequestDaoConnected[IO].create(mockDataReqHist) >>
            RequestDaoConnected[IO]
              .getDataByProp(
                mockDataReqHist.requestType,
                mockDataReqHist.dataType,
                mockDataReqHist.state,
                mockDataReqHist.size,
                mockDataReqHist.contId,
                mockDataReqHist.startTime,
                mockDataReqHist.startTime.plusSeconds(1)
              )
              .compile
              .lastOrError

      }).unsafeRunSync().reqId shouldBe mockDataReqHist.reqId
  }

  "Be able to change request state and get it back" in {
    val res = (flushKeyspace("test_req_dao_3_", "meta") >>
      ConnectedDao.initWithPrefix[IO]("test_req_dao_3_").use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          RequestDaoConnected[IO].create(mockDataReqHist) >>
            RequestDaoConnected[IO].changeState(mockDataReqHist.reqId, RequestState.ReceivingData) >>
            (
              RequestDaoConnected[IO]
                .getDataByProp(
                  mockDataReqHist.requestType,
                  mockDataReqHist.dataType,
                  RequestState.ReceivingData,
                  mockDataReqHist.size,
                  mockDataReqHist.contId,
                  mockDataReqHist.startTime,
                  mockDataReqHist.startTime.plusSeconds(1)
                )
                .compile
                .lastOrError,
              RequestDaoConnected[IO]
                .getDataByProp(
                  mockDataReqHist.requestType,
                  mockDataReqHist.dataType,
                  mockDataReqHist.state,
                  mockDataReqHist.size,
                  mockDataReqHist.contId,
                  mockDataReqHist.startTime,
                  mockDataReqHist.startTime.plusSeconds(1)
                )
                .compile
                .last
            ).parTupled
      }).unsafeRunSync()
    res._1.reqId shouldBe mockDataReqHist.reqId
    // existing state should disappear
    res._2 shouldBe None
  }

  "Be able to change request state and log that in audit table" in {
    (flushKeyspace("test_req_dao_3_", "meta") >>
      ConnectedDao.initWithPrefix[IO]("test_req_dao_3_").use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          RequestDaoConnected[IO].create(mockDataReqHist) >>
            RequestDaoConnected[IO].changeState(mockDataReqHist.reqId, RequestState.ReceivingData) >>
            RequestDaoConnected[IO]
              .getDataByProp(
                mockDataReqHist.requestType,
                mockDataReqHist.dataType,
                RequestState.ReceivingData,
                mockDataReqHist.size,
                mockDataReqHist.contId,
                mockDataReqHist.startTime,
                mockDataReqHist.startTime.plusSeconds(1)
              )
              .compile
              .lastOrError
      }).unsafeRunSync().reqId shouldBe mockDataReqHist.reqId
  }
}
