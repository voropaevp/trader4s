package utils

import cats.effect.{ Resource, Sync }
import java.util.concurrent.{ Executors, ExecutorService }
import scala.concurrent.ExecutionContext

// taken from doobie https://github.com/tpolecat/doobie/
// https://tpolecat.github.io/doobie/

object ExecutionContexts {

  /** Resource yielding an `ExecutionContext` backed by a fixed-size pool. */
  def fixedThreadPool[F[_]: Sync](size: Int): Resource[F, ExecutionContext] = {
    val alloc = Sync[F].delay(Executors.newFixedThreadPool(size))
    val free  = (es: ExecutorService) => Sync[F].delay(es.shutdown())
    Resource.make(alloc)(free).map(ExecutionContext.fromExecutor)
  }

  /** Resource yielding an `ExecutionContext` backed by an unbounded thread pool. */
  def cachedThreadPool[F[_]](
                              implicit sf: Sync[F]
                            ): Resource[F, ExecutionContext] = {
    val alloc = sf.delay(Executors.newCachedThreadPool)
    val free  = (es: ExecutorService) => sf.delay(es.shutdown())
    Resource.make(alloc)(free).map(ExecutionContext.fromExecutor)
  }

  /** Execution context that runs everything synchronously. This can be useful for testing. */
  object synchronous extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

}