package com.zaslon.zasdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.data.DictionaryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 前方一致検索で単語を選ぶ共通ダイアログ（関連語・関連単語選択などで共用）。
 *
 * 入力のたびに同期検索するとコンポーズ処理（UIスレッド）をブロックするため、
 * LaunchedEffect で入力を軽くデバウンスしたうえで Dispatchers.Default 上で検索する。
 * LaunchedEffect は key（query）が変わると前回のコルーチンを自動キャンセルするので、
 * 古い入力の検索結果が後から上書きしてしまうこともない。
 */
@Composable
fun WordFormPickerDialog(
    vm: MainViewModel,
    title: String,
    excludeIds: Set<Int> = emptySet(),
    onSelected: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    LaunchedEffect(query, excludeIds) {
        if (query.isNotEmpty()) delay(80)
        val result = withContext(Dispatchers.Default) {
            vm.searchFormsForPicker(query).filter { DictionaryStore.idOf(it) !in excludeIds }
        }
        candidates = result
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("前方一致で検索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = vm.headwordFontFamily)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates) { word ->
                        val id = DictionaryStore.idOf(word)
                        val form = DictionaryStore.formOf(word)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(id, form) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(form, fontFamily = vm.headwordFontFamily)
                            val tr = DictionaryStore.translationsOf(word).firstOrNull()
                            if (tr != null) {
                                Text(
                                    "${tr.first}：${tr.second.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
