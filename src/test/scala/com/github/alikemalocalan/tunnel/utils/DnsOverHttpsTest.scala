package com.github.alikemalocalan.tunnel.utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DnsOverHttpsTest extends AnyFlatSpec with Matchers {

  it should "ipAdressfromJsonTest" in {
    val response = DnsOverHttps.lookUp("google.com")
  }
}
