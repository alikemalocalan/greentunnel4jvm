package com.github.alikemalocalan.tunnel

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.net.Socket

import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import rawhttp.core.{RawHttp, RawHttpRequest}

import scala.util.{Failure, Success, Try}


class RequestHandlerActor(clientSocket: Socket) extends Thread {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  clientSocket.setSoTimeout(10000)

  val request: RawHttpRequest = Try {
    new RawHttp().parseRequest(clientSocket.getInputStream)
  } match {
    case Success(value) => value
    case Failure(exception) => logger.error(s"Invalid HttpRequest Error : ${exception.getMessage}")
      sendErrorResponse()
      throw exception
  }

  val url = request.getUri.getHost
  val port = request.getUri.getPort

  val proxyToClientBw: BufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream))

  val line = "HTTP/1.0 200 Connection established\r\n" +
    "Proxy-Agent: ProxyServer/1.0\r\n" +
    "\r\n"
  proxyToClientBw.write(line)
  proxyToClientBw.flush()

  override def run(): Unit = {

    if (request.getMethod.equals("CONNECT")) {
      System.out.println("HTTPS Request for : " + request.getUri + "\n");

      val proxyToServerSocket = new Socket(url, port)
      proxyToServerSocket.setSoTimeout(20000)

      val clientToProxyThread = new Thread {
        override def run(): Unit = {
          startTransmitThread(clientSocket.getInputStream,
            proxyToServerSocket.getOutputStream,
            "Fail on Socket for Client to Proxy: "
          )
        }

        override def setUncaughtExceptionHandler(eh: Thread.UncaughtExceptionHandler): Unit = {
          Try(proxyToServerSocket.close())
        }
      }

      clientToProxyThread.start()


      startTransmitThread(proxyToServerSocket.getInputStream,
        clientSocket.getOutputStream,
        "Fail on Socket for Proxy to Client: "
      )
      Try(proxyToServerSocket.close())
      Try(proxyToClientBw.close())
      Try(clientSocket.close())

    }
  }

  def sendErrorResponse(): Unit = {
    val line = "HTTP/1.0 200 Connection established\r\n" +
      "Proxy-Agent: ProxyServer/1.0\r\n" +
      "\r\n";
    Try{
      proxyToClientBw.write(line)
      proxyToClientBw.flush()
    }
  }

  def startTransmitThread(io: InputStream, os: OutputStream, onFailMsg: String): Unit =this.synchronized {
    Try {
      IOUtils.copy(io, os)
    } match {
      case Success(_) =>
      case Failure(exception) =>
        logger.error(onFailMsg, exception)
        sendErrorResponse()
    }
  }
}
