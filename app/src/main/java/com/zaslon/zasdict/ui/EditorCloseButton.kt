package com.zaslon.zasdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.zaslon.zasdict.R
import com.zaslon.zasdict.ui.theme.einkFocusFrame
import androidx.compose.ui.Modifier

/**
 * 編集画面の左上に置く閉じるボタン。OS の戻る操作もここで受ける。
 *
 * 変更が無ければ「戻る」のまま、そのまま閉じる。変更があるときは、閉じると入力が失われるので
 * 「キャンセル」に替え、閉じる前に確認を挟む。「戻る」と同じ見た目のまま入力を捨てると、
 * 押した結果を予測できないため。
 *
 * [subject] は確認文に出す編集対象の名前（見出し語など）。
 * [commitLabel] は、この画面で変更を反映する操作の名前（「登録」「適用」）。確認文で、何をしていない変更が失われるのかを示す。
 */
@Composable
fun EditorCloseButton(
    hasChanges: Boolean,
    subject: String,
    commitLabel: String = "登録",
    onClose: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    val requestClose = { if (hasChanges) confirming = true else onClose() }

    BackHandler(onBack = requestClose)

    IconButton(onClick = requestClose) {
        if (hasChanges) {
            Icon(painterResource(R.drawable.close), contentDescription = "キャンセル")
        } else {
            Icon(painterResource(R.drawable.arrow_left_line), contentDescription = "戻る")
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            modifier = Modifier.einkFocusFrame(),
            title = { DialogTitle("${commitLabel}していない変更があります") },
            text = {
                Text(
                    if (subject.isBlank()) "閉じると、編集内容は失われます。"
                    else "閉じると、「$subject」の編集内容は失われます。"
                )
            },
            confirmButton = {
                ZasDestructiveButton(
                    onClick = {
                        confirming = false
                        onClose()
                    }
                ) { Text("${commitLabel}せずに閉じる") }
            },
            dismissButton = {
                ZasTextButton(onClick = { confirming = false }) { Text("編集を続ける") }
            }
        )
    }
}
