package com.zaslon.zasdict.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel

/**
 * 更新履歴画面（changelog.py の ChangelogViewerWidget に相当）。
 * 履歴はアプリ内部に辞書ごとのCSVとして保持され、エクスポート可能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(vm: MainViewModel, navController: NavController) {

    // 音量キーで履歴リストをスクロール
    val listState = rememberLazyListState()
    VolumeScrollEffect(listState)

    // 連携・保存のたびに再読込する
    val entries = remember(vm.changelogVersion) { vm.changelog.readAll().reversed() } // 新しい順
    val pending = vm.changelog.pendingEntries
    val linkedName = remember(vm.changelogVersion) { vm.changelogLinkedName() }
    val isExternalLinked = remember(vm.changelogVersion) { vm.changelog.isExternalLinked() }

    var showClearDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportChangelog(it) } }

    // 手動配置した 辞書名_changelog.csv を選択して連携する
    val linkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.linkChangelogCsv(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新履歴") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if ((!isExternalLinked && (entries.isNotEmpty() || pending.isNotEmpty())) ||
                        (isExternalLinked && !vm.autoSave && pending.isNotEmpty())) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "履歴をクリア")
                        }
                    }
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = {
                            val base = vm.fileName?.removeSuffix(".json") ?: "dictionary"
                            exportLauncher.launch("${base}_changelog.csv")
                        }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "CSVエクスポート")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // 外部CSV連携カード
            ZasCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (linkedName != null) {
                        Text(
                            text = "連携中: $linkedName",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "辞書の保存（自動上書き保存を含む）と連動して、このCSVへ自動で追記されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { vm.unlinkChangelogCsv() }) {
                            Text("連携を解除（アプリ内部保存に戻す）")
                        }
                    } else {
                        Text(
                            text = "外部CSVとの連携",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "辞書ファイルと同じ場所に「辞書名_changelog.csv」を手動で配置した場合、" +
                                "下のボタンから選択すると読み込めます。連携後は辞書の保存" +
                                "（自動上書き保存を含む）と連動してCSVへ自動で追記されます。" +
                                "アプリ内部の既存履歴は連携時にCSVへ移行されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = {
                            linkLauncher.launch(arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "*/*"
                            ))
                        }) {
                            Text("CSVファイルを選択して連携")
                        }
                    }
                }
            }
            Divider()

            if (pending.isNotEmpty()) {
                Text(
                    text = "未保存の変更が ${pending.size} 件あります（辞書を保存すると履歴に記録されます）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(12.dp)
                )
                Divider()
            }
            if (entries.isEmpty() && pending.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "更新履歴がありません。\n辞書を編集して保存すると更新履歴が作成されます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(state = listState) {
                    items(pending.reversed()) { e ->
                        ChangelogRow(e.timestamp, e.type, e.form, e.details, pendingMark = true)
                        Divider()
                    }
                    items(entries) { e ->
                        ChangelogRow(e.timestamp, e.type, e.form, e.details, pendingMark = false)
                        Divider()
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("履歴を削除") },
            text = { Text("アプリ内部に保存された更新履歴をすべて削除します。\nこの操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearChangelogHistory()
                    showClearDialog = false
                }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun ChangelogRow(
    timestamp: String,
    type: String,
    form: String,
    details: String,
    pendingMark: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = timestamp + if (pendingMark) " *" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = type,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(text = form, style = MaterialTheme.typography.bodyLarge)
        if (details.isNotEmpty()) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
