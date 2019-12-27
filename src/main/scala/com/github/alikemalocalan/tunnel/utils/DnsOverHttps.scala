package com.github.alikemalocalan.tunnel.utils

import com.github.jgonian.ipmath
import com.github.jgonian.ipmath.Ipv4
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization

import scala.util.{Failure, Success, Try}

case class Answer(TTL: Long, data: String)

case class CloudFlareResponse(Answer: Array[Answer])

object DnsOverHttps {

  val request: String => String = (url: String) => s"https://ads-doh.securedns.eu/dns-query?name=$url&type=A"

  implicit val formats: AnyRef with Formats = Serialization.formats(NoTypeHints)


  @scala.annotation.tailrec
  def lookUp(address: String): String = {
    if (isDomainName(address)) {
      val domain: String = parse {
        OkHttpClientUtil.doGetHttpCall(request(address))
          .body().string()
      }.extract[CloudFlareResponse]
        .Answer.head.data

      val tmpDomain = if (domain.last == '.') domain.dropRight(1) else domain
      lookUp(tmpDomain)
    }
    else address
  }

  def isIpAddress(address: String): Boolean = {


    def isIPV4 =Try(Ipv4.of(address).toString == address) match {
      case Success(_) => true
      case Failure(_) => false
    }
    def isIPV6= Try(ipmath.Ipv6.of(address).toString == address) match {
      case Success(_) => true
      case Failure(_) => false
    }

    isIPV4 || isIPV6

  }

  def isDomainName: String => Boolean = !isIpAddress(_)
}
