package com.zaslon.zasdict.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaslon.zasdict.AppMessage
import com.zaslon.zasdict.R
import com.zaslon.zasdict.Semantic
import com.zaslon.zasdict.ui.theme.ZasShape
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
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> key(data) { AppSnackbar(data) } }
}

/**
 * 固定配置ボタンのある画面。Scaffold の snackbarHost に渡すと通知がボタンの上に積まれて画面の中ほどまで上がるため、
 * 本文の上に重ねて下端に揃え、右側をボタンの幅と余白の分だけ空けて横に並べる。
 */
@Composable
fun FabSnackbarBox(state: SnackbarHostState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        AppSnackbarHost(
            state,
            Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(
                    ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                // Scaffold が固定配置ボタンに付ける右の余白 16dp と、ボタンの幅 56dp
                .padding(end = 16.dp + 56.dp)
        )
    }
}

/**
 * 横へのスワイプで消せる。Material の Snackbar はスワイプに対応しないため SwipeToDismissBox で包む。
 * TalkBack ではスワイプが別の操作に割り当てられているため、同じ動作を semantics の dismiss でも渡す。
 */
@Composable
private fun AppSnackbar(data: SnackbarData) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) data.dismiss()
            value != SwipeToDismissBoxValue.Settled
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = Modifier.semantics { dismiss { data.dismiss(); true } }
    ) {
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
                    TextButton(onClick = { data.performAction() }, shape = ZasShape.control) {
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
    Semantic.INF -> R.drawable.info_fill
    Semantic.SUC -> R.drawable.check_circle_fill
    Semantic.WRN -> R.drawable.warning_fill
    Semantic.ERR -> R.drawable.error_fill
}

private fun Semantic.label(): String = when (this) {
    Semantic.INF -> "情報"
    Semantic.SUC -> "成功"
    Semantic.WRN -> "警告"
    Semantic.ERR -> "エラー"
}
