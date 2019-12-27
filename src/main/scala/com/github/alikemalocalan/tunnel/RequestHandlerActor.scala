package com.github.alikemalocalan.tunnel

import java.io.{BufferedWriter, OutputStreamWriter}
import java.net.Socket

import com.github.alikemalocalan.tunnel.utils.DnsOverHttps
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import rawhttp.core.{RawHttp, RawHttpHeaders, RawHttpRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class RequestHandlerActor(clientSocket: Socket) extends Thread {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  clientSocket.setSoTimeout(10000)
  val proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream));


  val request: RawHttpRequest = Try {
    val request = new RawHttp().parseRequest(clientSocket.getInputStream)
    val headers = RawHttpHeaders.newBuilder(request.getHeaders)
      .remove("Proxy-Connection").build()
    request.withHeaders(headers, false)
    request
  } match {
    case Success(value) => value
    case Failure(exception) =>
      logger.error(s"Invalid HttpRequest Error : ${exception.getMessage}")
      close()
      throw exception
  }

  override def run(): Unit = {

    if (request != null)
      if (request.getMethod.toUpperCase == "CONNECT" &&
        clientSocket.isConnected)
        handleHttpsRequest(request)
      else handleHttpRequest(request)
  }

  def handleHttpRequest(request: RawHttpRequest): Unit = {
    println("HTTP Request for : " + request.getUri + "\n")

    val url = DnsOverHttps.lookUp(request.getUri.getHost)

    val port: Int = Try {
      request.getUri.getPort
    } match {
      case Success(value) => if (value == -1) 80 else value
      case Failure(_) => 80
    }

    val serverSocket: Socket = Try {
      new Socket(url, port)
    } match {
      case Success(value) => value
      case Failure(exception) => close()
        throw exception
    }

    val firstChunk = Future {
      request.writeTo(serverSocket.getOutputStream)
      1
    } recover { ex =>
      logger.error("Error on First Request", ex)
      close()
      serverSocket.close()
      0
    }

    val dataReq = Future {
      IOUtils.copy(serverSocket.getInputStream, clientSocket.getOutputStream)
      1
    } recover { ex =>
      logger.error("Error on Data Request", ex)
      close()
      serverSocket.close()
      0

    }

    dataReq.onComplete { rs =>
      //IOUtils.copy(proxyToServerSocket.getInputStream, clientSocket.getOutputStream)
      logger.info("complete success")
      close()
      serverSocket.close()
    }

    Await.result(dataReq, 60 seconds)

  }

  def handleHttpsRequest(request: RawHttpRequest): Unit = {
    println("HTTPS Request for : " + request.getUri + "\n")
    val url = DnsOverHttps.lookUp(request.getUri.getHost)

    val port: Int = Try {
      request.getUri.getPort
    } match {
      case Success(value) => value
      case Failure(_) => 443
    }


    val serverSocket: Socket = Try {
      new Socket(url, port)
    } match {
      case Success(value) => value
      case Failure(exception) => close()
        throw exception
    }

    val firstReq: Future[Int] = Future {
      IOUtils.copy(clientSocket.getInputStream, serverSocket.getOutputStream)
      1
    }.recover { ex =>
      logger.error("Error on First Request", ex)
      close()
      serverSocket.close()
      0
    }


    val dataReq: Future[Int] = Future {
      Await.result(firstReq, 5 seconds)
      IOUtils.copy(serverSocket.getInputStream, clientSocket.getOutputStream)
      1
    }.recover { ex =>
      logger.error("Error on Data Request", ex)
      close()
      serverSocket.close()
      0
    }

    dataReq.onComplete { rs =>
      //IOUtils.copy(proxyToServerSocket.getInputStream, clientSocket.getOutputStream)
      logger.info("complete success")
      sendConnectionEstablished()

    }

    Await.result(dataReq, 60 seconds)
    serverSocket.close()
    close()
  }


  def close() = {
    val errorReq = "HTTP/1.0 504 Timeout Occured after 10s\n" +
      "User-Agent: ProxyServer/1.0\n" +
      "\r\n"
    proxyToClientBw.write(errorReq)
    proxyToClientBw.flush()
    clientSocket.close()
  }

  def sendConnectionEstablished(): Unit = {
    // Send Connection established to the client
    val acceptReq = "HTTP/1.0 200 Connection established\r\n" +
      "\r\n"
    proxyToClientBw.write(acceptReq)
    proxyToClientBw.flush()

  }
}
