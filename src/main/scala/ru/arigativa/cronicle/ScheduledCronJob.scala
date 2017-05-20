package ru.arigativa.cronicle

import java.time.LocalDateTime

/**
  * Created by hsslbch on 5/19/17.
  */
case class ScheduledCronJob[ID](
                          task: CronJob[ID],
                          nextRunAt: Option[LocalDateTime]
                      )
