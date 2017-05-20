package ru.arigativa.cronicle

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.{Timer, TimerTask}

import cron4s.expr.CronExpr
import cron4s.lib.javatime._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

/**
  * @param initiallyPaused If true cron job will not be executed until start() is called
  * @param executionContext Execution context, which is used to execute tasks
  * @tparam JobId Is type for CronJob payload (for identifying task etc.)
  */
class Cronicle[JobId](initiallyPaused: Boolean = true)(implicit executionContext: ExecutionContext) { cronicle =>

  class CronicleTimerTask(val ts: LocalDateTime, val jobs: List[CronJob[JobId]]) extends TimerTask {
    override def run(): Unit = {
      jobs.foreach { job =>
        executionContext.execute(new Runnable {
          override def run(): Unit = job.exec()
        })
      }
      cronicle.planNextTick()
    }
  }

  private val logger = LoggerFactory.getLogger(getClass)

  private val jobs: ArrayBuffer[CronJob[JobId]] = ArrayBuffer.empty
  private val timer: Timer = new Timer("cronicle-timer", true)
  private val tsCache = mutable.HashMap.empty[CronExpr, LocalDateTime]
  private var nextTick: CronicleTimerTask = null
  private var isPaused: Boolean = initiallyPaused

  private def getNextTs(from: LocalDateTime, job: CronJob[JobId]): Option[LocalDateTime] =
    tsCache.get(job.cronExpr) match {
      case Some(nextTs) if nextTs.isAfter(from) => Some(nextTs)
      case _ =>
        val maybeNextTs = job.cronExpr.next(from)
        maybeNextTs match {
          case Some(nextTs) => tsCache(job.cronExpr) = nextTs
          case None => tsCache -= job.cronExpr
        }
        maybeNextTs
    }

  @inline
  private def positiveOrZero(x: Long): Long = if (x >= 0) x else 0

  private def planNextTick(): Unit = synchronized {
    if (nextTick != null) {
      nextTick.cancel()
    }
    if (!isPaused && jobs.nonEmpty) {
      val now = LocalDateTime.now()
      val (nextTs, nextJobs) =
        jobs
          .iterator
          .map { job =>
            val maybeTs = getNextTs(now, job)
            if (maybeTs.isEmpty) {
              logger.warn(s"Cannot compute next execution time for $job")
            }
            maybeTs -> job
          }
          .collect { case (Some(ts), t) => ts -> t }
          .foldLeft(LocalDateTime.MAX -> List.empty[CronJob[JobId]]) {
            case ((accTs, _), (ts, job)) if ts.isBefore(accTs) => ts -> (job :: Nil)
            case ((accTs, accJobs), (ts, _)) if ts.isAfter(accTs) => accTs -> accJobs
            case ((_, accJobs), (ts, job)) => ts -> (job :: accJobs)
          }

      if (nextJobs.nonEmpty) {
        nextTick = new CronicleTimerTask(nextTs, nextJobs)
        val delay = positiveOrZero(ChronoUnit.MILLIS.between(now, nextTs))
        logger.trace(s"Scheduled next tick after $delay ms ($nextTs) with ${nextJobs.size} jobs")
        timer.schedule(nextTick, delay)
      }
    }
  }

  /**
    * Add a new job to schedule
    * @param job
    */
  def add(job: CronJob[JobId]): Unit = modify { jobs += job }

  /**
    * Remove a job from schedule
    * @param job
    */
  def remove(job: CronJob[JobId]): Unit = modify { jobs -= job }

  /**
    * Get scheduled jobs with next execution LocalDateTime
    * @return
    */
  def getAll: List[ScheduledCronJob[JobId]] = synchronized {
    jobs
      .toList
      .map { t =>
        ScheduledCronJob(t, tsCache.get(t.cronExpr))
      }
  }

  /**
    * Get time and list of jobs for next execution of schedule
    * @return
    */
  def nextRun: Option[(LocalDateTime, List[CronJob[JobId]])] =
    Option(nextTick).map { t => (t.ts, t.jobs) }

  /**
    * Clean schedule
    */
  def removeAll(): Unit = modify { jobs.clear() }

  /**
    * Enables planning and execution of schedule
    */
  def start(): Unit = modify { isPaused = false }

  /**
    * Disable planning and execution of schedule
    */
  def pause(): Unit = modify { isPaused = true }

  @inline
  private def modify[T](code: => T): T =
    synchronized {
      val result = code
      planNextTick()
      result
    }
}
