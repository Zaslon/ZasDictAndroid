package com.zaslon.zasdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.Routes
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.domain.Const
import com.zaslon.zasdict.ui.theme.LocalEinkMode

/**
 * 単語詳細画面（デスクトップ版の詳細ペイン＋detail.css に相当）。
 * モバイルでは分割表示ではなく独立した画面として遷移する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    vm: MainViewModel,
    navController: NavController,
    wordId: Int,
    snackbarHostState: SnackbarHostState,
    isPane: Boolean = false,
    onSelectWord: ((Int?) -> Unit)? = null
) {
    val word = vm.wordById(wordId)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val einkMode = LocalEinkMode.current

    // 音量キーで詳細をスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)

    val scale = vm.fontScale

    Scaffold(
        snackbarHost = { if (!isPane) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("詳細") },
                navigationIcon = {
                    if (!isPane) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.startEditEntry(wordId)
                        if (vm.editorDraft != null) navController.navigate(Routes.EDITOR)
                    }) { Icon(Icons.Default.Edit, contentDescription = "編集") }
                    IconButton(onClick = {
                        vm.startDuplicateEntry(wordId)
                        if (vm.editorDraft != null) navController.navigate(Routes.EDITOR)
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "複製") }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        }
    ) { padding ->
        if (word == null) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("この単語は存在しません（削除された可能性があります）。")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .let { if (einkMode) it.nestedScroll(einkScrollConnection).verticalScroll(scrollState) else it.verticalScroll(scrollState) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- form ---
            Text(
                text = DictionaryStore.formOf(word),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * scale
                ),
                fontWeight = FontWeight.Bold,
                fontFamily = vm.headwordFontFamily
            )

            // --- 発音記号 ---
            DictionaryStore.pronunciationOf(word)?.let { pron ->
                Text(
                    text = "/$pron/",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale
                    ),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- translations ---
            val translations = DictionaryStore.translationsOf(word)
            if (translations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    translations.forEachIndexed { i, (title, forms) ->
                        Text(
                            text = "${i + 1}. $title：${forms.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale
                            )
                        )
                    }
                }
            }

            // --- contents（発音記号以外） ---
            val contents = DictionaryStore.contentsOf(word)
                .filter { it.first != Const.PRONUNCIATION_TITLE }
            contents.forEach { (title, text) ->
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * scale
                        )
                    )
                }
            }

            // --- variations ---
            val variations = DictionaryStore.variationsOf(word)
            if (variations.isNotEmpty()) {
                Column {
                    Text(
                        text = "変化形",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    variations.forEach { (title, form) ->
                        Text(
                            text = "$title：$form",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * scale
                            ),
                            fontFamily = vm.headwordFontFamily
                        )
                    }
                }
            }

            // --- relations（title ごとにまとめ、タップで該当語へ遷移） ---
            val relations = DictionaryStore.relationsOf(word)
            if (relations.isNotEmpty()) {
                val grouped = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
                for ((title, tid, tform) in relations) {
                    grouped.getOrPut(title) { mutableListOf() }.add(Pair(tid, tform))
                }
                grouped.forEach { (title, targets) ->
                    Column {
                        Text(
                            text = "【$title】",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            targets.forEach { (tid, tform) ->
                                SuggestionChip(
                                    onClick = {
                                        if (vm.wordById(tid) != null) {
                                            if (isPane) onSelectWord?.invoke(tid)
                                            else navController.navigate(Routes.detail(tid))
                                        }
                                    },
                                    label = {
                                        Text(tform, fontFamily = vm.headwordFontFamily)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- tags ---
            val tags = DictionaryStore.tagsOf(word)
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        AssistChip(onClick = { }, label = { Text(tag) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            form = word?.let { DictionaryStore.formOf(it) } ?: "",
            onConfirm = {
                showDeleteConfirm = false
                vm.deleteWord(wordId)
                if (isPane) onSelectWord?.invoke(null)
                else navController.popBackStack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
