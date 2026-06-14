package com.zaslon.zasdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.DraftContent
import com.zaslon.zasdict.DraftRelation
import com.zaslon.zasdict.DraftTranslation
import com.zaslon.zasdict.DraftVariation
import com.zaslon.zasdict.EditorDraft
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.domain.Const

/**
 * 単語編集画面（editor.py の EntryEditorDialog に相当）。
 * 新規登録・編集・複製を1画面で扱う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    // 画面表示時のドラフトを一度だけ取得する。
    // commitDraft() / discardDraft() で vm.editorDraft が null になっても、
    // 再コンポーズ時にここで popBackStack が再実行されないようにする
    // （二重popで検索画面まで消えて白画面になるのを防ぐ）。
    val draft = remember { vm.editorDraft }
    if (draft == null) {
        // ドラフトがない状態で開かれた場合のみ、一度だけ戻る
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    var form by remember { mutableStateOf(draft.form) }
    var pronunciation by remember { mutableStateOf(draft.pronunciation) }
    val translations = remember { draft.translations.toMutableStateList() }
    val contents = remember {
        // 語法→文化→用例→語源→（その他）の順で初期表示する
        draft.contents.sortedBy { c ->
            Const.CONTENT_TYPES.indexOf(c.title).let { if (it < 0) Const.CONTENT_TYPES.size else it }
        }.toMutableStateList()
    }
    val relations = remember { draft.relations.toMutableStateList() }
    var tags by remember { mutableStateOf(draft.tags) }
    val variations = remember { draft.variations.toMutableStateList() }

    var showRelationPicker by remember { mutableStateOf<Int?>(null) } // 編集対象 relation index

    // 音量キーで編集画面をスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val isNew = draft.originalId == null

    fun buildDraft() = EditorDraft(
        originalId = draft.originalId,
        form = form,
        pronunciation = pronunciation,
        translations = translations.toList(),
        contents = contents.toList(),
        relations = relations.toList(),
        tags = tags,
        variations = variations.toList()
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新規登録" else "編集") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.discardDraft()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "キャンセル") }
                },
                actions = {
                    IconButton(onClick = {
                        if (vm.commitDraft(buildDraft())) {
                            navController.popBackStack()
                        }
                    }) { Icon(Icons.Default.Check, contentDescription = "保存") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 見出し語
            OutlinedTextField(
                value = form,
                onValueChange = { form = it },
                label = { Text("見出し語") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontFamily = vm.headwordFontFamily)
            )

            // 発音記号
            OutlinedTextField(
                value = pronunciation,
                onValueChange = { pronunciation = it },
                label = { Text("発音記号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ----------------------------------------------------------
            SectionHeader("訳語")
            translations.forEachIndexed { index, t ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PosDropdown(
                        value = t.title,
                        onValueChange = { translations[index] = t.copy(title = it) },
                        modifier = Modifier.width(140.dp)
                    )
                    OutlinedTextField(
                        value = t.forms,
                        onValueChange = { translations[index] = t.copy(forms = it) },
                        label = { Text("訳語（,区切り）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { translations.removeAt(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            }
            OutlinedButton(onClick = { translations.add(DraftTranslation()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("訳語を追加")
            }

            // ----------------------------------------------------------
            SectionHeader("内容")
            // 表示・保存順を 語法→文化→用例→語源→（その他の既存タイトル）に揃える
            fun contentRank(title: String): Int =
                Const.CONTENT_TYPES.indexOf(title).let { if (it < 0) Const.CONTENT_TYPES.size else it }

            contents.forEachIndexed { index, c ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = c.title.ifEmpty { "（無題）" },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { contents.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
                    OutlinedTextField(
                        value = c.text,
                        onValueChange = { contents[index] = c.copy(text = it) },
                        label = { Text("本文") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Const.CONTENT_TYPES.forEach { title ->
                    val exists = contents.any { it.title == title }
                    OutlinedButton(
                        onClick = {
                            if (!exists) {
                                contents.add(DraftContent(title = title))
                                val sorted = contents.sortedBy { contentRank(it.title) }
                                contents.clear()
                                contents.addAll(sorted)
                            }
                        },
                        enabled = !exists
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(if (exists) "${title}（追加済み）" else "${title}を追加")
                    }
                }
            }

            // ----------------------------------------------------------
            SectionHeader("関係（対照関係は相手側にも自動登録されます）")
            relations.forEachIndexed { index, r ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RelationDropdown(
                        value = r.title,
                        onValueChange = { relations[index] = r.copy(title = it) },
                        modifier = Modifier.width(130.dp)
                    )
                    OutlinedTextField(
                        value = r.targetForm.ifEmpty { "（未選択）" },
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("対象語") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showRelationPicker = index },
                        enabled = false,
                        textStyle = TextStyle(fontFamily = vm.headwordFontFamily)
                    )
                    TextButton(onClick = { showRelationPicker = index }) { Text("選択") }
                    IconButton(onClick = { relations.removeAt(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            }
            OutlinedButton(onClick = { relations.add(DraftRelation()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("関係を追加")
            }

            // ----------------------------------------------------------
            SectionHeader("変化形")
            variations.forEachIndexed { index, v ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = v.title,
                        onValueChange = { variations[index] = v.copy(title = it) },
                        label = { Text("名称") },
                        modifier = Modifier.width(140.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = v.form,
                        onValueChange = { variations[index] = v.copy(form = it) },
                        label = { Text("形") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = vm.headwordFontFamily)
                    )
                    IconButton(onClick = { variations.removeAt(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            }
            OutlinedButton(onClick = { variations.add(DraftVariation()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("変化形を追加")
            }

            // ----------------------------------------------------------
            SectionHeader("タグ")
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("タグ（,区切り）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // 関係先の単語選択ダイアログ
    showRelationPicker?.let { index ->
        RelationTargetPickerDialog(
            vm = vm,
            onSelected = { id, formStr ->
                relations[index] = relations[index].copy(targetId = id, targetForm = formStr)
                showRelationPicker = null
            },
            onDismiss = { showRelationPicker = null }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosDropdown(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            label = { Text("品詞") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Const.VALID_POS.forEach { pos ->
                DropdownMenuItem(
                    text = { Text(pos) },
                    onClick = { onValueChange(pos); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationDropdown(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            label = { Text("関係") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Const.VALID_RELATIONS.forEach { rel ->
                DropdownMenuItem(
                    text = { Text(rel) },
                    onClick = { onValueChange(rel); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun RelationTargetPickerDialog(
    vm: MainViewModel,
    onSelected: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val candidates = remember(query) { vm.searchFormsForPicker(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("対象語を選択") },
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
                        val f = DictionaryStore.formOf(word)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(id, f) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(f, fontFamily = vm.headwordFontFamily)
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
