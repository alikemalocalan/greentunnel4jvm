package com.github.alikemalocalan.tunnel

import java.net.URI
import java.nio.charset.Charset

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.DefaultHttpHeaders

import scala.util.Try

object HttpRequestUtils {

  def fromByteBuf(in: ByteBuf, chunky: String): HttpRequest = {
    if (chunky.toUpperCase.startsWith("CONNECT")) {
      val firstLine = chunky.split(" ")
      val method = firstLine(0)
      val uri = if (firstLine(1).toLowerCase.startsWith("https://")) new URI(firstLine(1).toLowerCase) else new URI(s"https://${firstLine(1).toLowerCase}")
      val protocolVersion = firstLine(2)
      val port = if (Try(uri.getPort).get != -1) Try(uri.getPort).getOrElse(443) else 443
      HttpRequest(method, uri, port = port, protocolVersion = protocolVersion, isHttps = true)
    } else {
      val reqAsString: String = in.asReadOnly.toString(Charset.defaultCharset)
      val mainPart = reqAsString.split("\r\n\r\n")
      val headerLine = mainPart.head.split("\r\n")
      val firstLine = headerLine.head.split(" ")

      val method = firstLine(0)
      val uri = if (firstLine(1).toLowerCase.startsWith("http://")) new URI(firstLine(1)) else new URI(s"http://${firstLine(1)}")
      val port = if (Try(uri.getPort).get != -1) Try(uri.getPort).get else 80
      val protocolVersion = firstLine(2)
      val payLoad = if (mainPart.length == 2) mainPart(1) else ""

      val headers = new DefaultHttpHeaders()
      for (headerLine <- headerLine.drop(1)) {
        val arr = headerLine.split(":")
        if (headerLine.startsWith("Host: ")) {
          headers.add("Host", arr(1))
        }
        else headers.add(arr.head, arr(1))
      }
      headers.remove("Proxy-Connection")
      HttpRequest(method, uri, protocolVersion, port, false, Some(headers), Some(payLoad))
    }
  }

  def readMainPart(in: ByteBuf): String = {
    val lineBuf = new StringBuffer()

    @scala.annotation.tailrec
    def readByteBuf(isReadable: Boolean): String = {
      if (isReadable) {
        val b: Byte = in.readByte()
        lineBuf.append(b.toChar)
        val len: Int = lineBuf.length()
        if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
          lineBuf.substring(0, len - 2)
        } else readByteBuf(in.isReadable)
      } else null
    }

    val result = readByteBuf(in.isReadable)
    while (in.isReadable) {
      in.readByte()
    }
    result
  }

}
