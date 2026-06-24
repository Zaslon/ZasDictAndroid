package com.zaslon.zasdict.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ZpdicApiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class OfferData(
        val translation: String,
        val supplement: String,
        val author: String
    )

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Failure(val error: String) : Result<Nothing>()
    }

    private fun catalogEncoded(catalog: String): String =
        URLEncoder.encode(catalog, "UTF-8")

    private fun requestBuilder(apiKey: String, url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("X-Api-Key", apiKey)

    private fun errorFromCode(code: Int): String = when (code) {
        400 -> "bad_request"
        401 -> "auth_failed"
        404 -> "not_found"
        429 -> "rate_limit"
        else -> "HTTP $code"
    }

    /** 単一出典を取得する（/api/v0/exampleOffer/{catalog}/{number}） */
    fun fetchOffer(catalog: String, number: Int, apiKey: String): Result<OfferData> {
        if (!apiKey.all { it.code < 128 }) return Result.Failure("api_key_non_ascii")
        val url = "${ZpdicConfig.API_BASE}/exampleOffer/${catalogEncoded(catalog)}/$number"
        return try {
            val resp = http.newCall(requestBuilder(apiKey, url).get().build()).execute()
            val body = resp.body?.string() ?: ""
            if (resp.code == 200) {
                val obj = JSONObject(body).optJSONObject("exampleOffer") ?: JSONObject()
                Result.Success(OfferData(
                    translation = obj.optString("translation", ""),
                    supplement = obj.optString("supplement", ""),
                    author = obj.optString("author", "")
                ))
            } else {
                Result.Failure(errorFromCode(resp.code))
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "network_error")
        }
    }

}
