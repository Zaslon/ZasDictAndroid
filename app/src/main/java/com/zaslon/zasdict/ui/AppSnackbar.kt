package com.zaslon.zasdict.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import com.zaslon.zasdict.AppMessage

/**
 * スナックバーに載せる通知。標準の表示内容に、失敗かどうかを添えて運ぶ。
 * 操作ボタン付きの通知は、標準では押すまで消えない（Indefinite）。それでは後の通知が出られないので、長めの時間で消す。
 */
class AppSnackbarVisuals(
    val appMessage: AppMessage,
    override val duration: SnackbarDuration =
        if (appMessage.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
) : SnackbarVisuals {
    override val message: String get() = appMessage.text
    override val actionLabel: String? get() = appMessage.actionLabel
    override val withDismissAction: Boolean get() = false
}

/**
 * 縦に狭い画面のステータスバーとして使うスナックバー。どの画面でも通知が見えるよう、全画面の Scaffold に置く。
 * 失敗の通知は色を変える。
 */
@Composable
fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val isFailure = (data.visuals as? AppSnackbarVisuals)?.appMessage?.isFailure == true
        if (isFailure) {
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                actionColor = MaterialTheme.colorScheme.error
            )
        } else {
            Snackbar(snackbarData = data)
        }
    }
}
