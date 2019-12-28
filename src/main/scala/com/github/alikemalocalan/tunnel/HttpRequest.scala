package com.github.alikemalocalan.tunnel

import java.net.URI

import io.netty.handler.codec.http.HttpHeaders

case class HttpRequest(method: String, uri: URI, protocolVersion: String, port: Int, isHttps: Boolean, headers: Option[HttpHeaders] = None, payload: Option[String] = None) {

  def headersAsString: String = {
    val builder = new StringBuilder("")

    headers.foreach(_.entries().forEach { header =>
      builder.append(String.format("%s: %s\r\n", header.getKey, header.getValue.trim))
    })
    builder.toString
  }


  def getPath: String = {
    Option(uri.getPath).filter(_.nonEmpty) getOrElse ("/")
  }


  def host: String = uri.getHost

  override def toString: String = String.format("%s %s %s\r\n", method, getPath, protocolVersion) + headersAsString + "\r\n" + payload.getOrElse("")

  def toStringForHTTPS: String = String.format("CONNECT %s:%s %s\r\n", host, port, protocolVersion)
}
