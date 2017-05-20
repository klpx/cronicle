Cronicle - precise, cheap & simple cron for Scala
=================================================

This is simple cron management library based on [cron4s](https://github.com/alonsodomin/cron4s) (cron expression library)

It use single java.util.Timer (which is precise enough) to schedule
all jobs and plans only nearest timer tick to ensure that execution of this tick does not shift next ticks.

Install
-------

```scala
libraryDependencies += "ru.arigativa" %% "cronicle" % "1.0.1"
```

Usage
-----

```scala
import java.time.Instant
import java.util.concurrent.Executors

import cron4s.Cron
import ru.arigativa.cronicle.{CronJob, Cronicle}

import scala.concurrent.ExecutionContext
// you can also use global executionContext
val cronicleEc = ExecutionContext.fromExecutor(Executors.newWorkStealingPool())
val cronicle = new Cronicle[String]()(cronicleEc)

val catJob = CronJob(
  "print cat",
  Cron("*/2 * * * * ?").right.get,
  () => println(Instant.now(), "cat")
)
val dogJob = CronJob(
  "print dog",
  Cron("* * * * * ?").right.get,
  () => println(Instant.now(), "dog")
)


cronicle.start() // start cron

cronicle.add(catJob) // add job to schedule
Thread.sleep(2000)
// (2017-05-20T13:14:06.005Z,cat)

cronicle.add(dogJob)
Thread.sleep(2000)
// (2017-05-20T13:14:07Z,dog)
// (2017-05-20T13:14:08.004Z,cat)
// (2017-05-20T13:14:08.004Z,dog)

cronicle.remove(dogJob)
Thread.sleep(2000)
// (2017-05-20T13:14:10.001Z,cat)

cronicle.pause()
Thread.sleep(2000)
// nothing is executed

cronicle.start()
Thread.sleep(2000)
// (2017-05-20T13:14:14.001Z,cat)
```
