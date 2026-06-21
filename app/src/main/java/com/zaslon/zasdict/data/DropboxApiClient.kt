package com.zaslon.zasdict.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class DropboxApiClient {

    private val http = OkHttpClient()

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long
    )

    data class FileEntry(
        val name: String,
        val path: String,
        val isFolder: Boolean,
        val size: Long = 0
    )

    // ------------------------------------------------------------------
    // PKCE ユーティリティ
    // ------------------------------------------------------------------

    fun generatePkceVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generatePkceChallenge(verifier: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256)
    }

    fun buildAuthUrl(appKey: String, codeChallenge: String): String =
        "${DropboxConfig.AUTH_URL}" +
            "?client_id=${appKey}" +
            "&response_type=code" +
            "&code_challenge=${codeChallenge}" +
            "&code_challenge_method=S256" +
            "&redirect_uri=${DropboxConfig.REDIRECT_URI}" +
            "&token_access_type=offline"

    // ------------------------------------------------------------------
    // トークン交換 / 更新
    // ------------------------------------------------------------------

    fun exchangeCodeForToken(code: String, verifier: String, appKey: String): TokenResult {
        val body = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("client_id", appKey)
            .add("code_verifier", verifier)
            .add("redirect_uri", DropboxConfig.REDIRECT_URI)
            .build()
        val resp = http.newCall(
            Request.Builder().url(DropboxConfig.TOKEN_URL).post(body).build()
        ).execute()
        val json = JSONObject(resp.body?.string() ?: throw IOException("Empty response"))
        if (!resp.isSuccessful) {
            throw IOException("トークン取得失敗: ${json.optString("error_description", json.optString("error"))}")
        }
        return TokenResult(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
            expiresIn = json.optLong("expires_in", 14400L)
        )
    }

    fun refreshAccessToken(refreshToken: String, appKey: String): TokenResult {
        val body = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("client_id", appKey)
            .build()
        val resp = http.newCall(
            Request.Builder().url(DropboxConfig.TOKEN_URL).post(body).build()
        ).execute()
        val json = JSONObject(resp.body?.string() ?: throw IOException("Empty response"))
        if (!resp.isSuccessful) {
            throw IOException("トークン更新失敗: ${json.optString("error_description", json.optString("error"))}")
        }
        return TokenResult(
            accessToken = json.getString("access_token"),
            refreshToken = null,
            expiresIn = json.optLong("expires_in", 14400L)
        )
    }

    // ------------------------------------------------------------------
    // アカウント情報
    // ------------------------------------------------------------------

    fun getCurrentAccountName(accessToken: String): String {
        val body = "null".toRequestBody("application/json".toMediaType())
        val resp = http.newCall(
            Request.Builder()
                .url(DropboxConfig.CURRENT_ACCOUNT_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()
        ).execute()
        if (!resp.isSuccessful) return ""
        val json = JSONObject(resp.body?.string() ?: return "")
        return json.optJSONObject("name")?.optString("display_name")
            ?: json.optString("email")
            ?: ""
    }

    // ------------------------------------------------------------------
    // ファイル操作
    // ------------------------------------------------------------------

    fun downloadFile(accessToken: String, path: String): String {
        val argJson = JSONObject().put("path", path).toString()
        val resp = http.newCall(
            Request.Builder()
                .url(DropboxConfig.DOWNLOAD_URL)
                .header("Authorization", "Bearer $accessToken")
                .header("Dropbox-API-Arg", argJson)
                .post("".toRequestBody())
                .build()
        ).execute()
        if (!resp.isSuccessful) {
            throw IOException("ダウンロード失敗 (${resp.code}): ${resp.body?.string()}")
        }
        return resp.body?.string() ?: throw IOException("空のレスポンス")
    }

    fun uploadFile(accessToken: String, path: String, content: String) {
        val argJson = JSONObject().apply {
            put("path", path)
            put("mode", "overwrite")
            put("autorename", false)
            put("mute", false)
        }.toString()
        val body = content.toByteArray(Charsets.UTF_8).toRequestBody("application/octet-stream".toMediaType())
        val resp = http.newCall(
            Request.Builder()
                .url(DropboxConfig.UPLOAD_URL)
                .header("Authorization", "Bearer $accessToken")
                .header("Dropbox-API-Arg", argJson)
                .post(body)
                .build()
        ).execute()
        if (!resp.isSuccessful) {
            throw IOException("アップロード失敗 (${resp.code}): ${resp.body?.string()}")
        }
    }

    fun listFolder(accessToken: String, path: String): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()

        val reqBody = JSONObject().apply {
            put("path", path)
            put("recursive", false)
            put("include_media_info", false)
            put("include_deleted", false)
        }.toString().toRequestBody("application/json".toMediaType())

        val resp = http.newCall(
            Request.Builder()
                .url(DropboxConfig.LIST_FOLDER_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(reqBody)
                .build()
        ).execute()
        val json = JSONObject(resp.body?.string() ?: throw IOException("Empty response"))
        if (!resp.isSuccessful) {
            throw IOException("フォルダ一覧取得失敗: ${json.optString("error_summary")}")
        }

        fun parseEntries(arr: org.json.JSONArray) {
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val tag = e.getString(".tag")
                entries.add(
                    FileEntry(
                        name = e.getString("name"),
                        path = e.getString("path_display"),
                        isFolder = tag == "folder",
                        size = if (tag == "file") e.optLong("size", 0) else 0
                    )
                )
            }
        }

        parseEntries(json.getJSONArray("entries"))

        var cursor = json.optString("cursor")
        var hasMore = json.optBoolean("has_more", false)

        while (hasMore && cursor.isNotEmpty()) {
            val contBody = JSONObject().put("cursor", cursor).toString()
                .toRequestBody("application/json".toMediaType())
            val contResp = http.newCall(
                Request.Builder()
                    .url(DropboxConfig.LIST_FOLDER_CONTINUE_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .post(contBody)
                    .build()
            ).execute()
            val contJson = JSONObject(contResp.body?.string() ?: break)
            parseEntries(contJson.getJSONArray("entries"))
            cursor = contJson.optString("cursor")
            hasMore = contJson.optBoolean("has_more", false)
        }

        return entries.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
    }
}
