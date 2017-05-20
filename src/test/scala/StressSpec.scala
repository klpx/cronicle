import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.concurrent.atomic.AtomicInteger

import cron4s.Cron
import cron4s.expr.CronExpr
import org.scalatest.{FlatSpec, Matchers}
import ru.arigativa.cronicle.{Cronicle, ScheduledCronJob, CronJob}

/**
  * Created by hsslbch on 5/19/17.
  */
class StressSpec extends FlatSpec with Matchers {
  import scala.concurrent.ExecutionContext.Implicits.global

  val ParallelSize = 1000
  val CheckpointA = 30
  val CheckpointB = 10

  "Cronicle" should "precisely run thousands of tasks" in {
    val cronicle = new Cronicle[String]

    val countA = new AtomicInteger(0)
    val countB = new AtomicInteger(0)

    def taskA(i: Int) = CronJob(s"task a $i", Cron("* * * * * ?").right.get, () => countA.incrementAndGet())
    def taskB(i: Int) = CronJob(s"task b $i", Cron("*/2 * * * * ?").right.get, () => countB.incrementAndGet())

    // schedule tasks
    for {i <- 0 until ParallelSize} {
      cronicle.add(taskA(i))
      cronicle.add(taskB(i))
    }

    def expectedA(i: Int) = i * ParallelSize
    def expectedB(i: Int) = (i / 2) * ParallelSize

    // align time to even seconds
    Thread.sleep(2000 - (System.currentTimeMillis() % 2000))
    cronicle.start()
    Thread.sleep(10)

    for {i <- 0 until CheckpointA} {
      withClue(i -> Instant.now()) {
        countA.get() shouldBe expectedA(i)
        countB.get() shouldBe expectedB(i)
      }
      Thread.sleep(1000)
    }

    // remove `A` tasks for test it will not running after that
    cronicle.getAll
      .collect { case ScheduledCronJob(t, _) if t.id.startsWith("task a") => t }
      .foreach(cronicle.remove)

    Thread.sleep(CheckpointB * 1000) // running `B` tasks for {checkpointB} seconds

    countA.get() shouldBe expectedA(CheckpointA)
    countB.get() shouldBe expectedB(CheckpointA + CheckpointB)
  }
}
