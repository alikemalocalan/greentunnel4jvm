package com.github.alikemalocalan.greentunnel4jvm.utils

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit.SECONDS

data class Answer(val TTL: Long, val data: String)

data class CloudFlareResponse(val Answer: Array<Answer>)

object DNSOverHttps {
    fun requestUrl(url: String): String = "https://ads-doh.securedns.eu/dns-query?name=$url&type=A"

    val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, SECONDS)
            .writeTimeout(60, SECONDS)
            .readTimeout(60, SECONDS)
            .build()
    }

    fun lookUp(address: String): String {
        val response = doGetHttpCall(requestUrl(address)).body!!.string()
        return ipAdressfromJson(response)
    }

    fun ipAdressfromJson(jsonStr: String): String =
        Gson().fromJson(jsonStr, CloudFlareResponse::class.java)
            .Answer.first().data


    fun doGetHttpCall(url: String): Response {
        val request = Request.Builder()
            .url(url).method("GET", null)
            .build()
        return client.newCall(request).execute()
    }
}