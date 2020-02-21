package com.github.alikemalocalan.tunnel

import java.net.ServerSocket

import org.slf4j.{Logger, LoggerFactory}


object Proxy extends App with Config {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val serverSocket = new ServerSocket(port)
  serverSocket.setSoTimeout(100000)

  System.out.println("Waiting for client on port " + serverSocket.getLocalPort + "..")

  while (true){
   val newConnection= new RequestHandlerActor(serverSocket.accept())

    newConnection.setUncaughtExceptionHandler((t: Thread, e: Throwable) => {
        logger.error("Client Socket connection error",e)
    })

      newConnection.start()
  }
}
