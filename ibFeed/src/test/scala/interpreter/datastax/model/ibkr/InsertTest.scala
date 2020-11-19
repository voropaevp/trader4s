package interpreter.datastax.model.ibkr
import cats.effect.{IO, Sync}
import cats.effect.testing.scalatest.AsyncIOSpec
import model.datastax.ib.FeedMapperBuilder
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class InsertTest extends AsyncFreeSpec with AsyncIOSpec with SqlSession with Matchers {

  "Cassandra " - {
    " insert IbkrBars" in {

      makeProdSession[IO]
        .use { session =>
          Sync[IO].delay(new FeedMapperBuilder(session).build())
        }
        .asserting(mapper => (mapper.ibkrBarDao.create() shouldBe 1))
    }
  }
}
