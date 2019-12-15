package com.github.alikemalocalan.tunnel

import java.io.{InputStream, OutputStream}

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, OneForOneStrategy}
import rawhttp.core.errors.InvalidHttpRequest

import scala.concurrent.duration._
import scala.language.postfixOps

case class Start()

case class Transmit(is:InputStream,os:OutputStream)
class ClientToServerHttpsTransmitActor(ts: Transmit) extends Actor with ActorLogging {

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 1, withinTimeRange = 5 seconds) {
      case _: InvalidHttpRequest =>
        Stop
    }

  override def receive: Receive = {

    case Start =>
      org.apache.commons.io.IOUtils.copy(ts.is, ts.os)
  }

  override def postStop(): Unit = {
    //ts.is.close()
  }
}
