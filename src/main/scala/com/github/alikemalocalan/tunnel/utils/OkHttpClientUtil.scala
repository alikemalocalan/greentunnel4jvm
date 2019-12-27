package com.github.alikemalocalan.tunnel.utils

import java.util.concurrent.TimeUnit.SECONDS

import okhttp3.{MediaType, OkHttpClient, Request, Response}
import org.apache.log4j.Logger
import org.slf4j
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object OkHttpClientUtil {
  val logger: slf4j.Logger = LoggerFactory.getLogger(this.getClass)

  private val APPLICATION_JSON: MediaType = MediaType.parse("application/json; charset=utf-8")
  private val defaultRetryCount = 5

  private val client = new OkHttpClient.Builder()
    .connectTimeout(120, SECONDS)
    .writeTimeout(120, SECONDS)
    .readTimeout(120, SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  def doGetHttpCall(url: String): Response = {
    val request = new Request.Builder()
      .url(url)
      .method("GET", null)
      .addHeader("accept", "application/dns-json")
      .build()
    retryableRequest(defaultRetryCount)(request)
  }

  @tailrec
  def retryableRequest(count: Int)(request: Request): Response = {
    Try {
      client.newCall(request).execute()
    } match {
      case Success(x) if x.isSuccessful => x
      case _ if count > 0 => retryableRequest(count - 1)(request)
      case Success(x) => logger.error(x.body().string())
        throw new Exception(x.body().string)
      case Failure(e) => throw e
    }
  }
}
