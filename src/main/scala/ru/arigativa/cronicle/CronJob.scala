package ru.arigativa.cronicle

import cron4s.expr.CronExpr

/**
  * Created by hsslbch on 5/18/17.
  */
case class CronJob[ID](
               id: ID,
               cronExpr: CronExpr,
               exec: () => Any
             )

