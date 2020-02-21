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

  def fromByteBuf(in: ByteBuf): Option[HttpRequest] = {
    val chunky = HttpServiceUtils.readMainPart(in)
    if(chunky.isEmpty) None
    else Some(parseBeyteBuf(chunky,in))
  }

  def parseBeyteBuf(chunky:String,in:ByteBuf): HttpRequest ={
    val firstLine = chunky.split(" ")
    val method = firstLine(0)
    val host = firstLine(1).toLowerCase
    val protocolVersion = firstLine(2)
    if (method.toUpperCase.startsWith("CONNECT")) { // Https request
      val uri = if (host.startsWith("https://")) new URI(host) else new URI(s"https://$host")
      val port = Try(uri.getPort).toOption.filter(_ != -1).getOrElse(443)
      HttpRequest(method, uri, port = port, protocolVersion = protocolVersion, isHttps = true)
    }
    else { // Http request
      val uri = if (host.startsWith("http://")) new URI(host) else new URI(s"http://$host")
      val port = Try(uri.getPort).toOption.filter(_ != -1).getOrElse(80)

      val reqAsString: String = in.asReadOnly.toString(Charset.defaultCharset)
      val mainPart = reqAsString.split("\r\n\r\n") // until payload
      val headerLine = mainPart.head.split("\r\n") // for headers

      val payLoad = if (mainPart.length == 2) mainPart(1) else ""

      val headers = new DefaultHttpHeaders()
      for (headerLine <- headerLine) {
        val arr = headerLine.split(":")
        headers.add(arr(0), arr(1))
      }
      headers.remove("Proxy-Connection")
      HttpRequest(method, uri, protocolVersion, port, isHttps = false, Some(headers), Some(payLoad))
    }
  }

  def readMainPart(in: ByteBuf): String = {
    val lineBuf = new StringBuffer()

    @scala.annotation.tailrec
    def readByteBuf(isReadable: Boolean, result: Option[String] = None): Option[String] = {
      if (isReadable) {
        val b: Byte = in.readByte()
        lineBuf.append(b.toChar)
        val len: Int = lineBuf.length()
        if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
          readByteBuf(isReadable = false, Some(lineBuf.substring(0, len - 2)))
        } else readByteBuf(in.isReadable, None)
      } else result
    }

    readByteBuf(in.isReadable, None)
      .getOrElse{
      in.release()
      ""
    }
  }

  @scala.annotation.tailrec
  def writeToHttps(in: ByteBuf, remoteChannel: Channel): Unit = {
    if (in.isReadable) {
      val bufSize: Int = if (in.readableBytes > clientHelloMTU) clientHelloMTU else in.readableBytes
      remoteChannel.writeAndFlush(in.readSlice(bufSize).retain())
        .addListener { future: ChannelFuture =>
          if (future.isSuccess) remoteChannel.read()
          else future.channel().close()
        }
      writeToHttps(in, remoteChannel)
    }
  }

  def closeOnFlush(ch: Channel): Unit =
    if (ch != null && ch.isActive) ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
}
