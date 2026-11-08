package com.zaslon.zasdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 編集画面の左上に置く閉じるボタン。OS の戻る操作もここで受ける。
 *
 * 変更が無ければ ← のまま、そのまま閉じる。変更があるときは、閉じると入力が失われるので
 * アイコンを × に替え、閉じる前に確認を挟む。「戻る」と同じ見た目のまま入力を捨てると、
 * 押した結果を予測できないため。
 *
 * [subject] は確認文に出す編集対象の名前（見出し語など）。
 */
@Composable
fun EditorCloseButton(hasChanges: Boolean, subject: String, onClose: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val requestClose = { if (hasChanges) confirming = true else onClose() }

    BackHandler(onBack = requestClose)

    IconButton(onClick = requestClose) {
        if (hasChanges) {
            Icon(Icons.Default.Close, contentDescription = "キャンセル")
        } else {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("保存されていない変更があります") },
            text = {
                Text(
                    if (subject.isBlank()) "編集内容を保存せずに閉じますか？"
                    else "「$subject」の編集内容を保存せずに閉じますか？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("保存せずに閉じる") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("編集を続ける") }
            }
        )
    }
}
