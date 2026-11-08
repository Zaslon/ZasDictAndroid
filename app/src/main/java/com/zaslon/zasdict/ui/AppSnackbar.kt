package com.zaslon.zasdict.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zaslon.zasdict.AppMessage
import com.zaslon.zasdict.R
import com.zaslon.zasdict.Semantic
import com.zaslon.zasdict.ui.theme.ZasTheme

/**
 * スナックバーに載せる通知。標準の表示内容に、通知の種類（Semantic）を添えて運ぶ。
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
 * 縦に狭い画面のステータスバーとして使う下部アプリ通知。どの画面でも通知が見えるよう、全画面の Scaffold に置く。
 *
 * 面は反転面とし、アイコンは文字と同じ色にする。反転面の上では意味色のアイコンが 3:1 に届かないため。
 * 失敗の通知だけは強調の色で面を塗り、アイコンを意味色にする。
 * 影は Material の標準のまま付けるが、面の境界は枠線でも示す（ダークでは影が見えにくい）。
 */
@Composable
fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val semantic = (data.visuals as? AppSnackbarVisuals)?.appMessage?.semantic ?: Semantic.INF
        val colors = ZasTheme.colors
        val highlighted = semantic == Semantic.ERR
        val shape = MaterialTheme.shapes.extraSmall

        Snackbar(
            modifier = Modifier
                .padding(12.dp)
                .border(1.dp, colors.border, shape),
            containerColor = if (highlighted) colors.statusbarError else colors.inverseBackground,
            contentColor = if (highlighted) colors.statusbarText else colors.inverseText,
            shape = shape,
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(label, color = if (highlighted) colors.buttonText else colors.inverseAction)
                    }
                }
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(semantic.iconRes()),
                    // 読み上げでは種類を文言で伝える
                    contentDescription = semantic.label(),
                    tint = if (highlighted) colors.notificationError else colors.inverseText,
                    modifier = Modifier.size(24.dp)
                )
                Text(data.visuals.message)
            }
        }
    }
}

private fun Semantic.iconRes(): Int = when (this) {
    Semantic.INF -> R.drawable.info
    Semantic.SUC -> R.drawable.success
    Semantic.WRN -> R.drawable.warning
    Semantic.ERR -> R.drawable.error
}

private fun Semantic.label(): String = when (this) {
    Semantic.INF -> "情報"
    Semantic.SUC -> "成功"
    Semantic.WRN -> "警告"
    Semantic.ERR -> "エラー"
}
