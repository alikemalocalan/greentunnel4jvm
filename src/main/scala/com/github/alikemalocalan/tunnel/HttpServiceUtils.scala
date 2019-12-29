package com.github.alikemalocalan.tunnel

import java.net.URI
import java.nio.charset.Charset

import com.github.alikemalocalan.tunnel.models.HttpRequest
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.DefaultHttpHeaders

import scala.util.Try

object HttpServiceUtils {
  val clientHelloMTU: Int = 100

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
    in.clear()
    result
  }

  @scala.annotation.tailrec
  def writeToHttps(in: ByteBuf, remoteChannel: Channel): Unit = {
    if (in.isReadable && remoteChannel.isOpen && remoteChannel.isActive && remoteChannel.isWritable) {
      if (in.readableBytes > clientHelloMTU) {
        remoteChannel.writeAndFlush(in.readSlice(clientHelloMTU).retain())
          .addListener { future: ChannelFuture =>
            if (future.isSuccess) remoteChannel.read()
            else future.channel().close()
          }
        writeToHttps(in, remoteChannel)
      } else remoteChannel.writeAndFlush(in.readSlice(in.readableBytes).retain())
        .addListener { future: ChannelFuture =>
          if (future.isSuccess) remoteChannel.read()
          else future.channel().close()
        }
    }
  }

  def closeOnFlush(ch: Channel): Unit =
    if (ch != null && ch.isActive) ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
}
