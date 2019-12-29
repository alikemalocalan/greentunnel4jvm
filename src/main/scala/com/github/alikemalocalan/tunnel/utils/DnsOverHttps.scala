package com.github.alikemalocalan.tunnel.utils


import upickle.default.{macroRW, ReadWriter => RW, _}

import scala.io.Source._

case class Answer(TTL: Long, data: String)

case class CloudFlareResponse(Answer: Array[Answer])

object CloudFlareResponse {
  implicit val rwAnswer: RW[Answer] = macroRW
  implicit val rw: RW[CloudFlareResponse] = macroRW

  def ipAdressfromJson(jsonStr: String): String = {
    import CloudFlareResponse.rw
    read[CloudFlareResponse](jsonStr).Answer.head.data
  }

}

object DnsOverHttps {
  // Alternative https://cloudflare-dns.com/dns-query
  val request: String => String = (url: String) => s"https://ads-doh.securedns.eu/dns-query?name=$url&type=A"

  def lookUp(address: String): String = {
    val response = fromURL(request(address))
    CloudFlareResponse.ipAdressfromJson(response.withClose(() => response.close()).mkString)

  }
}
