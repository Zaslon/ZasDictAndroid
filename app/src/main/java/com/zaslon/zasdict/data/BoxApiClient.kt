package com.zaslon.zasdict.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class BoxApiClient {

    private val http = OkHttpClient()

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long
    )

    data class FileEntry(
        val id: String,
        val name: String,
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

    fun buildAuthUrl(clientId: String, codeChallenge: String): String =
        "${BoxConfig.AUTH_URL}" +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256" +
            "&redirect_uri=${BoxConfig.REDIRECT_URI}"

    // ------------------------------------------------------------------
    // トークン交換 / 更新
    // ------------------------------------------------------------------

    fun exchangeCodeForToken(code: String, verifier: String, clientId: String, clientSecret: String): TokenResult {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code_verifier", verifier)
            .add("redirect_uri", BoxConfig.REDIRECT_URI)
            .build()
        val resp = http.newCall(
            Request.Builder().url(BoxConfig.TOKEN_URL).post(body).build()
        ).execute()
        val json = JSONObject(resp.body?.string() ?: throw IOException("Empty response"))
        if (!resp.isSuccessful) {
            throw IOException("トークン取得失敗: ${json.optString("error_description", json.optString("error"))}")
        }
        return TokenResult(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
            expiresIn = json.optLong("expires_in", 3600L)
        )
    }

    fun refreshAccessToken(refreshToken: String, clientId: String, clientSecret: String): TokenResult {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .build()
        val resp = http.newCall(
            Request.Builder().url(BoxConfig.TOKEN_URL).post(body).build()
        ).execute()
        val json = JSONObject(resp.body?.string() ?: throw IOException("Empty response"))
        if (!resp.isSuccessful) {
            throw IOException("トークン更新失敗: ${json.optString("error_description", json.optString("error"))}")
        }
        return TokenResult(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
            expiresIn = json.optLong("expires_in", 3600L)
        )
    }

    // ------------------------------------------------------------------
    // アカウント情報
    // ------------------------------------------------------------------

    fun getCurrentUser(accessToken: String): String {
        val resp = http.newCall(
            Request.Builder()
                .url("${BoxConfig.API_BASE}/users/me")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        ).execute()
        if (!resp.isSuccessful) return ""
        val json = JSONObject(resp.body?.string() ?: return "")
        return json.optString("name").takeIf { it.isNotEmpty() }
            ?: json.optString("login")
            ?: ""
    }

    // ------------------------------------------------------------------
    // フォルダ一覧
    // ------------------------------------------------------------------

    fun listFolder(accessToken: String, folderId: String): List<FileEntry> {
        val url = "${BoxConfig.API_BASE}/folders/$folderId/items?fields=id,name,type,size&limit=1000"
        val resp = http.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        ).execute()
        val bodyStr = resp.body?.string() ?: throw IOException("Empty response")
        if (!resp.isSuccessful) {
            val msg = runCatching { JSONObject(bodyStr).optString("message") }.getOrDefault(bodyStr)
            throw IOException("フォルダ一覧取得失敗 (${resp.code}): $msg")
        }
        val arr = JSONObject(bodyStr).getJSONArray("entries")
        val entries = mutableListOf<FileEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val type = e.optString("type")
            if (type != "file" && type != "folder") continue
            entries.add(FileEntry(
                id = e.getString("id"),
                name = e.getString("name"),
                isFolder = type == "folder",
                size = e.optLong("size", 0)
            ))
        }
        return entries.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
    }

    // ------------------------------------------------------------------
    // ファイル操作
    // ------------------------------------------------------------------

    fun downloadFile(accessToken: String, fileId: String): String {
        val resp = http.newCall(
            Request.Builder()
                .url("${BoxConfig.API_BASE}/files/$fileId/content")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        ).execute()
        if (!resp.isSuccessful) {
            throw IOException("ダウンロード失敗 (${resp.code}): ${resp.body?.string()}")
        }
        return resp.body?.string() ?: throw IOException("空のレスポンス")
    }

    /** 新規ファイルをアップロードして Box ファイル ID を返す */
    fun uploadNewFile(accessToken: String, folderId: String, name: String, content: String): String {
        val attributes = JSONObject().apply {
            put("name", name)
            put("parent", JSONObject().put("id", folderId))
        }.toString()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("attributes", attributes)
            .addFormDataPart(
                "file", name,
                content.toByteArray(Charsets.UTF_8).toRequestBody("application/octet-stream".toMediaType())
            )
            .build()
        val resp = http.newCall(
            Request.Builder()
                .url("${BoxConfig.UPLOAD_BASE}/files/content")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()
        ).execute()
        val bodyStr = resp.body?.string() ?: throw IOException("Empty response")
        if (!resp.isSuccessful) {
            val msg = runCatching { JSONObject(bodyStr).optString("message") }.getOrDefault(bodyStr)
            throw IOException("アップロード失敗 (${resp.code}): $msg")
        }
        return JSONObject(bodyStr).getJSONArray("entries").getJSONObject(0).getString("id")
    }

    /** 既存ファイルの内容を上書きする */
    fun updateFile(accessToken: String, fileId: String, name: String, content: String) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", name,
                content.toByteArray(Charsets.UTF_8).toRequestBody("application/octet-stream".toMediaType())
            )
            .build()
        val resp = http.newCall(
            Request.Builder()
                .url("${BoxConfig.UPLOAD_BASE}/files/$fileId/content")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()
        ).execute()
        if (!resp.isSuccessful) {
            val bodyStr = resp.body?.string() ?: ""
            val msg = runCatching { JSONObject(bodyStr).optString("message") }.getOrDefault(bodyStr)
            throw IOException("アップロード失敗 (${resp.code}): $msg")
        }
    }

    /** フォルダ内のファイルを名前で検索して ID を返す。見つからなければ null */
    fun findFileInFolder(accessToken: String, folderId: String, name: String): String? = try {
        listFolder(accessToken, folderId).find { !it.isFolder && it.name == name }?.id
    } catch (_: Exception) { null }
}
