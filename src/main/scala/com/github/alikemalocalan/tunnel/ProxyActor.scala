package com.github.alikemalocalan.tunnel

import java.net.Socket

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}

import scala.concurrent.duration._
import scala.language.postfixOps


class ProxyActor extends Actor with ActorLogging {

  var running = true

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 1, withinTimeRange = 5 seconds) {
      case _: Exception => Stop
    }

  var requestActorRouter: Router = {
    val routees = Vector.fill(1) {
      val r = context.actorOf(Props(new ProxyActor()))
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  override def receive: Receive = {

    case serverSocket: Socket =>
      requestActorRouter.route(serverSocket, sender())

    case Terminated(s) =>
      log.error(s"${s.toString()} is terminated and will be killed.")
      requestActorRouter = requestActorRouter.removeRoutee(s)
      val r = context.actorOf(Props(new ProxyActor()))
      context.watch(r)
      requestActorRouter = requestActorRouter.addRoutee(r)

    case _ => log.error("Not known type as incoming message")
  }

  override def postRestart(reason: Throwable): Unit =
    log.info(s"Actor restarted due to ${reason.toString}")

}