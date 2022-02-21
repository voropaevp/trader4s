package db.dao

import cats.syntax.all._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import db.ConnectedDao.ContractDaoConnected
import db.{ConnectedDao, TestDbSpec}

class ContractDaoSpec extends TestDbSpec {

  import ContractDaoSpec._

  "Create the meta keyspace" in {
    (flushKeyspace("test_cont_dao_1_", "meta") >>
      getFormattedContents(
        "test_cont_dao_1_",
        keyspace,
        s"""SELECT
           | keyspace_name
           |  FROM system_schema.keyspaces
           |  WHERE keyspace_name = 'test_cont_dao_1_meta'
           |  """.stripMargin
      )).unsafeRunSync() shouldBe (s"""[keyspace_name:'test_cont_dao_1_meta']""")
  }

  "Contract derived contract entry should match the mock contract " in {
    mockContract.asEntry shouldBe mockContractEntry
  }

  "Be able to insert raw the get it by id and contract entry" in {
    val result = (flushKeyspace("test_cont_dao_3_", "meta") >> ConnectedDao.initWithPrefix[IO]("test_cont_dao_2_").use {
      case (a, b, c) =>
        implicit val (_a, _b, _c) = (a, b, c)
        ContractDaoConnected[IO].create(mockContract) >>
          (
            ContractDaoConnected[IO].get(0),
            ContractDaoConnected[IO].getByContractEntry(mockContractEntry),
            ContractDaoConnected[IO].getByContractEntry(mockContractEntry.copy(primaryExch = None))
          ).parTupled
    }).unsafeRunSync()
    result._1 shouldBe Some(mockContract)
    result._2 shouldBe Some(mockContract)
    result._3 shouldBe None
  }

}

object ContractDaoSpec {
  val keyspace = "meta"
}
