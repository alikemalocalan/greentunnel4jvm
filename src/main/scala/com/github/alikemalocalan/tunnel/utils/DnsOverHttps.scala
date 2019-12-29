package com.github.alikemalocalan.tunnel.utils

import org.json4s._
import org.json4s.native.JsonMethods._

case class Answer(TTL: Long, data: String)

case class CloudFlareResponse(Answer: Array[Answer])

object DnsOverHttps {
  // Alternative https://ads-doh.securedns.eu/dns-query
  val request: String => String = (url: String) => s"https://cloudflare-dns.com/dns-query?name=$url&type=A"

  implicit val formats = DefaultFormats


  def lookUp(address: String): String =
    parse(OkHttpClientUtil.doGetHttpCall(request(address)).body().string())
      .extract[CloudFlareResponse]
      .Answer.head.data
}
