package com.zaslon.zasdict.data

import android.content.ContentResolver
import android.net.Uri

/**
 * SAF（Storage Access Framework）経由の書き込みユーティリティ。
 *
 * ローカルストレージは "wt"（切り詰め書き込み）で問題ないが、
 * Box・OneDrive などのクラウド DocumentsProvider には "wt" モードを
 * サポートしないものがあるため、複数のモードを順に試す。
 * クラウドプロバイダはパイプ経由の書き込みでも、ストリームを閉じた時点で
 * 書き込んだ内容全体をファイルの新しい内容として扱うため、
 * 最終フォールバックの "w" でも実用上は全置換になる。
 */
object SafIo {

    private val WRITE_MODES = listOf("wt", "rwt", "w")

    fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
        var lastError: Exception? = null
        for (mode in WRITE_MODES) {
            try {
                val out = resolver.openOutputStream(uri, mode)
                    ?: throw IllegalStateException("ファイルへ書き込めません")
                out.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("ファイルへ書き込めません")
    }
}
