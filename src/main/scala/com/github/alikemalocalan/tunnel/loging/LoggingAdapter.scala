package com.github.alikemalocalan.tunnel.loging

import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry, LoggingMagnet}
import akka.http.scaladsl.server.{Route, RouteResult}

object LoggingAdapter {
  /**
   * @see <a href="https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/debugging-directives/logRequestResult.html#building-advanced-directives"></a>
   *
   */

  def printResponseTime(log: LoggingAdapter): HttpRequest => RouteResult => Unit = {
    val requestTimestamp = System.nanoTime
    akkaResponseTimeLoggingFunction(log)
  }

  def clientRouteLogged(routes: Route): Route = DebuggingDirectives.logRequestResult(LoggingMagnet(akkaResponseTimeLoggingFunction(_)))(routes)

  def akkaResponseTimeLoggingFunction(loggingAdapter: LoggingAdapter,
                                      level: LogLevel = Logging.InfoLevel)(req: HttpRequest)(res: RouteResult): Unit = {
    val entry = res match {
      case Complete(resp) =>
        //val responseTimestamp: Long = System.nanoTime
        //val elapsedTime: Long = (responseTimestamp - requestTimestamp) / 1000000
        val loggingString = s"""Request | ${req.method.value}| ${req.uri}| ${resp.status}"""
        LogEntry(loggingString, level)
      case Rejected(reason) =>
        LogEntry(s"Rejected Reason: ${reason.mkString(",")}", level)
    }
    entry.logTo(loggingAdapter)
  }
}
