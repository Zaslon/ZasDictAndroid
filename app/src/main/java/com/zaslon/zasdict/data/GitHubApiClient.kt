package com.zaslon.zasdict.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64

class GitHubApiClient {

    private val http = OkHttpClient()

    data class FileEntry(
        val name: String,
        val path: String,
        val isFolder: Boolean,
        val size: Long = 0
    )

    data class FileContent(
        val text: String,
        val sha: String
    )

    private fun requestBuilder(token: String, url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

    // ------------------------------------------------------------------
    // アカウント情報
    // ------------------------------------------------------------------

    fun getCurrentUser(token: String): String {
        val resp = http.newCall(requestBuilder(token, GitHubConfig.USER_URL).get().build()).execute()
        if (!resp.isSuccessful) throw IOException("認証失敗 (${resp.code})")
        val json = JSONObject(resp.body?.string() ?: return "")
        return json.optString("name").takeIf { it.isNotEmpty() }
            ?: json.optString("login")
            ?: ""
    }

    // ------------------------------------------------------------------
    // ファイル操作
    // ------------------------------------------------------------------

    /** ファイルの内容と SHA を取得する */
    fun getFileContent(token: String, owner: String, repo: String, path: String): FileContent {
        val url = "${GitHubConfig.API_BASE}/repos/$owner/$repo/contents/$path"
        val resp = http.newCall(requestBuilder(token, url).get().build()).execute()
        val bodyStr = resp.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("レスポンスが空です (${resp.code})")
        if (!resp.isSuccessful) {
            val errMsg = runCatching { JSONObject(bodyStr).optString("message") }.getOrDefault(bodyStr)
            throw IOException("ファイル取得失敗 (${resp.code}): $errMsg")
        }
        val json = JSONObject(bodyStr)
        val sha = json.getString("sha")
        val encoding = json.optString("encoding", "base64")

        // ファイルが 1MB 超の場合、GitHub は encoding:"none" + content:"" を返す
        // その場合は download_url から直接ダウンロードする
        if (encoding != "base64") {
            val downloadUrl = json.optString("download_url").takeIf { it.isNotEmpty() }
                ?: throw IOException("ファイルが大きすぎて取得できません (encoding: $encoding)")
            val dlResp = http.newCall(
                Request.Builder().url(downloadUrl)
                    .header("Authorization", "Bearer $token")
                    .get().build()
            ).execute()
            if (!dlResp.isSuccessful) {
                throw IOException("ダウンロード失敗 (${dlResp.code})")
            }
            val dlBody = dlResp.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("ダウンロードされたファイルが空です")
            return FileContent(text = dlBody, sha = sha)
        }

        val base64Content = json.optString("content", "")
            .replace("\n", "").replace("\r", "").trim()
        if (base64Content.isEmpty()) {
            throw IOException("ファイルの内容を取得できませんでした（content が空）")
        }
        val decoded = try {
            // getMimeDecoder は base64 以外の文字を無視するため、余分な空白等に対して堅牢
            val raw = String(Base64.getMimeDecoder().decode(base64Content), Charsets.UTF_8)
            if (raw.isNotEmpty() && raw[0].code == 0xFEFF) raw.substring(1) else raw // UTF-8 BOM を除去
        } catch (e: IllegalArgumentException) {
            throw IOException("Base64デコードエラー: ${e.message}")
        }
        if (decoded.isBlank()) {
            throw IOException("デコード後のファイルが空です")
        }
        return FileContent(text = decoded, sha = sha)
    }

    /** ファイルの SHA だけを取得する。ファイルが存在しない場合は null を返す */
    fun getFileSha(token: String, owner: String, repo: String, path: String): String? {
        return try { getFileContent(token, owner, repo, path).sha } catch (_: Exception) { null }
    }

    /**
     * ファイルを作成または更新してコミットを作成する。
     * sha = null で新規作成、sha 指定で既存ファイルの更新。
     * アップロード前に getFileSha で最新 sha を取得して渡すこと。
     */
    fun updateFile(
        token: String, owner: String, repo: String, branch: String,
        path: String, content: String, sha: String?, message: String
    ) {
        val url = "${GitHubConfig.API_BASE}/repos/$owner/$repo/contents/$path"
        val base64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val bodyObj = JSONObject().apply {
            put("message", message)
            put("content", base64)
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }
        val body = bodyObj.toString().toRequestBody("application/json".toMediaType())
        val resp = http.newCall(requestBuilder(token, url).put(body).build()).execute()
        if (!resp.isSuccessful) {
            val bodyStr = resp.body?.string() ?: ""
            val errMsg = runCatching { JSONObject(bodyStr).optString("message") }.getOrDefault(bodyStr)
            throw IOException("アップロード失敗 (${resp.code}): $errMsg")
        }
    }

    // ------------------------------------------------------------------
    // 複数ファイルを1コミットにまとめる（Git Data API）
    // ------------------------------------------------------------------

    data class FileChange(val path: String, val content: String)

    /**
     * 複数ファイルへの変更を1つのコミットとして作成する。
     * Git Data API（Trees API）を使用するため、ファイルの現在のSHAは不要。
     */
    fun commitFiles(
        token: String, owner: String, repo: String, branch: String,
        files: List<FileChange>, message: String
    ) {
        // 1. 現在のブランチのコミットSHAを取得
        val refUrl = "${GitHubConfig.API_BASE}/repos/$owner/$repo/git/ref/heads/$branch"
        val refResp = http.newCall(requestBuilder(token, refUrl).get().build()).execute()
        val refBody = refResp.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("ブランチ参照の取得に失敗しました (${refResp.code})")
        if (!refResp.isSuccessful) {
            throw IOException("ブランチ参照取得失敗 (${refResp.code}): ${runCatching { JSONObject(refBody).optString("message") }.getOrDefault(refBody)}")
        }
        val currentCommitSha = JSONObject(refBody).getJSONObject("object").getString("sha")

        // 2. 現在のコミットからベースツリーのSHAを取得
        val commitUrl = "${GitHubConfig.API_BASE}/repos/$owner/$repo/git/commits/$currentCommitSha"
        val commitResp = http.newCall(requestBuilder(token, commitUrl).get().build()).execute()
        val commitBody = commitResp.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("コミット情報の取得に失敗しました")
        if (!commitResp.isSuccessful) {
            throw IOException("コミット情報取得失敗 (${commitResp.code})")
        }
        val baseTreeSha = JSONObject(commitBody).getJSONObject("tree").getString("sha")

        // 3. 新しいツリーを作成（ファイル内容はbase64ではなく文字列で渡す）
        val treeEntries = JSONArray()
        for (file in files) {
            treeEntries.put(JSONObject().apply {
                put("path", file.path)
                put("mode", "100644")
                put("type", "blob")
                put("content", file.content)
            })
        }
        val treeReqBody = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeEntries)
        }.toString().toRequestBody("application/json".toMediaType())

        val treeUrl = "${GitHubConfig.API_BASE}/repos/$owner/$repo/git/trees"
        val treeResp = http.newCall(requestBuilder(token, treeUrl).post(treeReqBody).build()).execute()
        val treeRespBody = treeResp.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("ツリーの作成に失敗しました")
        if (!treeResp.isSuccessful) {
            throw IOException("ツリー作成失敗 (${treeResp.code}): ${runCatching { JSONObject(treeRespBody).optString("message") }.getOrDefault(treeRespBody)}")
        }
        val newTreeSha = JSONObject(treeRespBody).getString("sha")

        // 4. 新しいコミットを作成
        val newCommitReqBody = JSONObject().apply {
            put("message", message)
            put("tree", newTreeSha)
            put("parents", JSONArray().put(currentCommitSha))
        }.toString().toRequestBody("application/json".toMediaType())

        val newCommitUrl = "${GitHubConfig.API_BASE}/repos/$owner/$repo/git/commits"
        val newCommitResp = http.newCall(requestBuilder(token, newCommitUrl).post(newCommitReqBody).build()).execute()
        val newCommitRespBody = newCommitResp.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("コミットの作成に失敗しました")
        if (!newCommitResp.isSuccessful) {
            throw IOException("コミット作成失敗 (${newCommitResp.code}): ${runCatching { JSONObject(newCommitRespBody).optString("message") }.getOrDefault(newCommitRespBody)}")
        }
        val newCommitSha = JSONObject(newCommitRespBody).getString("sha")

        // 5. ブランチのrefを新しいコミットに更新
        val updateRefReqBody = JSONObject().put("sha", newCommitSha)
            .toString().toRequestBody("application/json".toMediaType())
        val updateRefUrl = "${GitHubConfig.API_BASE}/repos/$owner/$repo/git/refs/heads/$branch"
        val updateRefResp = http.newCall(
            requestBuilder(token, updateRefUrl).patch(updateRefReqBody).build()
        ).execute()
        if (!updateRefResp.isSuccessful) {
            val updateRefRespBody = updateRefResp.body?.string() ?: ""
            throw IOException("ブランチ更新失敗 (${updateRefResp.code}): ${runCatching { JSONObject(updateRefRespBody).optString("message") }.getOrDefault(updateRefRespBody)}")
        }
    }

    // ------------------------------------------------------------------
    // ディレクトリ一覧
    // ------------------------------------------------------------------

    fun getContents(token: String, owner: String, repo: String, path: String): List<FileEntry> {
        val encodedPath = if (path.isEmpty()) "" else "/$path"
        val url = "${GitHubConfig.API_BASE}/repos/$owner/$repo/contents$encodedPath"
        val resp = http.newCall(requestBuilder(token, url).get().build()).execute()
        val bodyStr = resp.body?.string() ?: throw IOException("Empty response")
        if (!resp.isSuccessful) {
            val errMsg = runCatching { JSONObject(bodyStr).optString("message") }.getOrDefault(bodyStr)
            throw IOException("フォルダ一覧取得失敗 (${resp.code}): $errMsg")
        }
        val arr = JSONArray(bodyStr)
        val entries = mutableListOf<FileEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val type = e.optString("type")
            if (type != "file" && type != "dir") continue
            entries.add(
                FileEntry(
                    name = e.getString("name"),
                    path = e.getString("path"),
                    isFolder = type == "dir",
                    size = e.optLong("size", 0)
                )
            )
        }
        return entries.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
    }
}
