package com.github.alikemalocalan.tunnel

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.routing.RoundRobinPool
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.util.{ByteString, Timeout}
import rawhttp.core.RawHttp

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object StreamProxy extends Config {

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  implicit val timeout: Timeout = Timeout(25 seconds)

  val logger = actorSystem.log
  val masterProps: Props = Props(new ProxyActor())
    .withRouter(new RoundRobinPool(masterCount))
  val pulseActor: ActorRef = actorSystem.actorOf(masterProps, "pulseinsert-actor")

  def main(args: Array[String]): Unit = {


    val handler = Flow[ByteString]
      .map(x=>x.utf8String)
      .map{ req=>
        val result =new RawHttp().parseRequest(req)
        logger.debug(s"Request is : $result")
        result
      }
      .map(req=>ByteString(req.toString))


    val connections = Tcp().bind(address, port)

    connections.runForeach { connection =>
      println(s"New connection from: ${connection.remoteAddress}")
      connection.handleWith(handler)

    }
  }


}
