package com.github.alikemalocalan.tunnel.utils

import com.dslplatform.json._
import com.dslplatform.json.runtime.Settings

import scala.io.Source._

case class Answer(TTL: Long, data: String)

case class CloudFlareResponse(Answer: Array[Answer])

object DnsOverHttps {
  // Alternative https://cloudflare-dns.com/dns-query
  val request: String => String = (url: String) => s"https://ads-doh.securedns.eu/dns-query?name=$url&type=A"

  def lookUp(address: String): String = {
    val response = fromURL(request(address))
    ipAdressfromJson(response.withClose(() => response.close()).mkString)
  }

  def ipAdressfromJson(jsonStr: String): String = {
    implicit val dslJson = new DslJson[CloudFlareResponse](Settings.withRuntime().allowArrayFormat(true).includeServiceLoader())
    dslJson.decode[CloudFlareResponse](jsonStr.getBytes).Answer.head.data
  }
}
