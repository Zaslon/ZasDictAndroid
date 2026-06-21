package com.zaslon.zasdict.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ChangelogEntry(
    val timestamp: String,
    val type: String,
    val form: String,
    val details: String
)

/**
 * 更新履歴（changelog）の管理。
 *
 * デスクトップ版は辞書JSONと同じ場所に <名前>_changelog.csv を保存するが、
 * Android の Storage Access Framework では隣接ファイルを自動検出・作成できない。
 * そこで本クラスは2つの保存先をサポートする:
 *
 * 1. 内部モード（既定）: アプリ内部ストレージに辞書URIごとのCSVを保持
 * 2. 外部連携モード: ユーザーが手動配置した「辞書名_changelog.csv」を
 *    一度選択して連携すると、以後そのCSVを直接読み書きする。
 *    連携時に内部履歴は外部CSVへ移行される。
 *    辞書保存（自動上書き保存を含む）のたびに flush() で追記される。
 */
class ChangelogStore(private val context: Context) {

    /** 未保存（メモリ上）のエントリ。辞書保存時にCSVへ追記される */
    val pendingEntries = mutableListOf<ChangelogEntry>()

    private var currentKey: String? = null

    /** 辞書URIハッシュ → 連携中の外部CSV URI のマッピング */
    private val links = context.getSharedPreferences("zasdict_changelog_links", Context.MODE_PRIVATE)

    fun setDictionary(uriString: String?, clearPending: Boolean = true) {
        currentKey = uriString?.let { sha256(it) }
        if (clearPending) pendingEntries.clear()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun internalFile(): File? {
        val key = currentKey ?: return null
        return File(context.filesDir, "changelog_$key.csv")
    }

    // ------------------------------------------------------------------
    // 外部CSV連携
    // ------------------------------------------------------------------

    fun linkedUri(): Uri? {
        val key = currentKey ?: return null
        return links.getString("uri_$key", null)?.let(Uri::parse)
    }

    fun isExternalLinked(): Boolean = linkedUri() != null

    /**
     * 手動配置されたCSVファイルを連携する。
     * 内部ストレージに既存の履歴があれば外部CSVの末尾へ移行し、内部側は削除する。
     */
    fun linkExternalCsv(uri: Uri) {
        val key = currentKey ?: throw IllegalStateException("辞書が読み込まれていません")

        // 内部履歴の移行
        val internal = internalFile()
        if (internal != null && internal.exists()) {
            val internalLines = internal.readLines()
            val dataRows = if (internalLines.size > 1) internalLines.drop(1).filter { it.isNotBlank() } else emptyList()
            if (dataRows.isNotEmpty()) {
                var text = readUriText(uri) ?: ""
                if (text.isBlank()) text = HEADER + "\n"
                if (!text.endsWith("\n")) text += "\n"
                text += dataRows.joinToString("\n") + "\n"
                writeUriText(uri, text)
            }
            internal.delete()
        } else {
            // 外部CSVが空ならヘッダだけ用意しておく
            val text = readUriText(uri)
            if (text.isNullOrBlank()) {
                writeUriText(uri, HEADER + "\n")
            }
        }

        links.edit().putString("uri_$key", uri.toString()).apply()
    }

    /** 連携を解除する（以後は内部ストレージに保存） */
    fun unlinkExternalCsv() {
        val key = currentKey ?: return
        links.edit().remove("uri_$key").apply()
    }

    private fun readUriText(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

    private fun writeUriText(uri: Uri, text: String) {
        // クラウドプロバイダ（Box/OneDrive等）の "wt" 非対応に備えたフォールバック付き書き込み
        SafIo.writeText(context.contentResolver, uri, text)
    }

    // ------------------------------------------------------------------
    // 追記・読み出し
    // ------------------------------------------------------------------

    fun addEntry(type: String, form: String, details: String = "") {
        val timestamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        pendingEntries.add(ChangelogEntry(timestamp, type, form, details))
    }

    /**
     * 辞書保存時に呼び出し、保留中エントリをCSVへ追記する。
     * 外部CSV連携中は外部ファイルへ、未連携なら内部ストレージへ書き込む。
     * @return 書き込みに成功したか（失敗時は保留エントリを保持したまま false）
     */
    fun flush(): Boolean {
        if (pendingEntries.isEmpty()) return true

        val rows = pendingEntries.joinToString("\n") { e ->
            csvRow(listOf(e.timestamp, e.type, e.form, e.details))
        }

        val uri = linkedUri()
        if (uri != null) {
            // 外部CSVへの追記（read-modify-write。SAFプロバイダの append 非対応に備える）
            return try {
                var text = readUriText(uri) ?: ""
                if (text.isBlank()) text = HEADER + "\n"
                if (!text.endsWith("\n")) text += "\n"
                writeUriText(uri, text + rows + "\n")
                pendingEntries.clear()
                true
            } catch (e: Exception) {
                false
            }
        }

        val f = internalFile() ?: return false
        return try {
            val writeHeader = !f.exists()
            f.appendText(buildString {
                if (writeHeader) append(HEADER + "\n")
                append(rows)
                append('\n')
            })
            pendingEntries.clear()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Dropboxからダウンロードしたテキストを内部ファイルに書き込む（pendingEntriesは保持） */
    fun loadFromText(csvText: String) {
        val f = internalFile() ?: return
        if (csvText.isNotBlank()) f.writeText(csvText)
    }

    fun exists(): Boolean {
        if (isExternalLinked()) return true
        return internalFile()?.exists() == true
    }

    /** アプリ内部の履歴ファイルと未保存エントリをすべて削除する */
    fun clearInternal() {
        pendingEntries.clear()
        internalFile()?.delete()
    }

    fun readAll(): List<ChangelogEntry> {
        val text = currentCsvText() ?: return emptyList()
        val lines = text.split("\n")
        if (lines.isEmpty()) return emptyList()
        return lines.drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val cols = parseCsvLine(line)
            if (cols.size >= 3) {
                ChangelogEntry(
                    cols[0],
                    cols.getOrElse(1) { "" },
                    cols.getOrElse(2) { "" },
                    cols.getOrElse(3) { "" }
                )
            } else null
        }
    }

    /** 現在の保存先（外部優先）のCSV全文。読めない場合は内部へフォールバック */
    private fun currentCsvText(): String? {
        linkedUri()?.let { uri ->
            try {
                return readUriText(uri)
            } catch (e: Exception) {
                // 外部CSVが移動・削除された場合は内部へフォールバック
            }
        }
        val f = internalFile() ?: return null
        return if (f.exists()) f.readText() else null
    }

    fun exportCsvText(): String = currentCsvText() ?: ""

    // ------------------------------------------------------------------
    // CSVユーティリティ
    // ------------------------------------------------------------------

    private fun csvRow(cols: List<String>): String =
        cols.joinToString(",") { col ->
            if (col.contains(',') || col.contains('"') || col.contains('\n')) {
                "\"" + col.replace("\"", "\"\"") + "\""
            } else col
        }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString()); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    companion object {
        const val HEADER = "timestamp,type,form,details"
    }
}
