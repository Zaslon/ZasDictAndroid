package com.zaslon.zasdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.DraftExample
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.Routes
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import org.json.JSONObject

// ------------------------------------------------------------------
// 例文一覧画面
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamplesScreen(
    vm: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    var query by remember { mutableStateOf("") }

    val allExamples = remember(vm.examplesVersion) { vm.exampleList() }
    val filtered = remember(allExamples, query) {
        if (query.isEmpty()) allExamples
        else allExamples.filter { ex ->
            val q = query.lowercase()
            ex.optString("sentence", "").lowercase().contains(q) ||
            ex.optString("translation", "").lowercase().contains(q) ||
            ex.optString("supplement", "").lowercase().contains(q)
        }
    }

    val einkMode = LocalEinkMode.current
    val listState = rememberLazyListState()
    VolumeScrollEffect(listState)
    val einkScrollConnection = rememberEinkNestedScrollConnection(listState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("例文") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.startNewExample()
                    if (vm.exampleDraft != null) navController.navigate(Routes.EXAMPLE_EDITOR)
                },
                elevation = if (einkMode) {
                    FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                } else {
                    FloatingActionButtonDefaults.elevation()
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "例文を追加")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("例文・訳を検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Text(
                    text = if (allExamples.isEmpty()) "例文がありません。＋ボタンで追加してください。"
                           else "一致する例文がありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (einkMode) it.nestedScroll(einkScrollConnection) else it }
                ) {
                    items(filtered, key = { it.optInt("id", -1) }) { ex ->
                        ExampleListItem(
                            example = ex,
                            headwordFont = vm.headwordFontFamily,
                            onClick = {
                                val id = ex.optInt("id", -1)
                                if (id >= 0) {
                                    vm.startEditExample(id)
                                    navController.navigate(Routes.EXAMPLE_EDITOR)
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
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

@Composable
private fun ExampleListItem(
    example: JSONObject,
    headwordFont: FontFamily?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = example.optString("sentence", ""),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = headwordFont),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val translation = example.optString("translation", "")
        if (translation.isNotEmpty()) {
            Text(
                text = translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ------------------------------------------------------------------
// 例文編集画面
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExampleEditorScreen(
    vm: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val draft = remember { vm.exampleDraft }
    if (draft == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val isNew = draft.originalId == null

    var sentence by remember { mutableStateOf(draft.sentence) }
    var translation by remember { mutableStateOf(draft.translation) }
    var supplement by remember { mutableStateOf(draft.supplement) }
    var tags by remember { mutableStateOf(draft.tags) }
    val linkedWords = remember { draft.linkedWords.toMutableStateList() }
    var offerCatalog by remember { mutableStateOf(draft.offerCatalog) }
    var offerNumber by remember { mutableIntStateOf(draft.offerNumber) }

    var showWordPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showZpdicBrowse by remember { mutableStateOf(false) }

    // 照会結果を受け取ってフォームに反映する
    LaunchedEffect(vm.zpdicOfferResult) {
        vm.zpdicOfferResult?.let { result ->
            if (result.translation.isNotEmpty()) translation = result.translation
            if (result.supplement.isNotEmpty()) supplement = result.supplement
            vm.consumeZpdicOfferResult()
        }
    }

    val einkMode = LocalEinkMode.current
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)
    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)

    fun buildDraft() = DraftExample(
        originalId = draft.originalId,
        sentence = sentence,
        translation = translation,
        supplement = supplement,
        tags = tags,
        linkedWords = linkedWords.toList(),
        offerCatalog = offerCatalog,
        offerNumber = offerNumber
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "例文を追加" else "例文を編集") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.discardExampleDraft()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "キャンセル")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
                    IconButton(onClick = {
                        if (vm.commitExampleDraft(buildDraft())) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .let { if (einkMode) it.nestedScroll(einkScrollConnection).verticalScroll(scrollState) else it.verticalScroll(scrollState) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isNew) {
                Text(
                    text = "ID: ${draft.originalId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = sentence,
                onValueChange = { sentence = it },
                label = { Text("文 *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = TextStyle(fontFamily = vm.headwordFontFamily)
            )

            OutlinedTextField(
                value = translation,
                onValueChange = { translation = it },
                label = { Text("訳") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = supplement,
                onValueChange = { supplement = it },
                label = { Text("補足") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("タグ（,区切り）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ----------------------------------------------------------
            SectionHeader("関連単語")
            if (linkedWords.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    linkedWords.forEachIndexed { index, (_, form) ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(form, fontFamily = vm.headwordFontFamily) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "削除",
                                    modifier = Modifier.clickable { linkedWords.removeAt(index) }
                                )
                            }
                        )
                    }
                }
            }
            OutlinedButton(onClick = { showWordPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("単語を追加")
            }

            // ----------------------------------------------------------
            SectionHeader("出典")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = offerCatalog,
                    onValueChange = { offerCatalog = it; vm.zpdicOfferStatus = "" },
                    label = { Text("カタログ") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = if (offerNumber == 0) "" else offerNumber.toString(),
                    onValueChange = { v ->
                        offerNumber = v.toIntOrNull() ?: 0
                        vm.zpdicOfferStatus = ""
                    },
                    label = { Text("No.") },
                    modifier = Modifier.weight(0.35f),
                    singleLine = true
                )
                TextButton(
                    onClick = { vm.fetchZpdicOffer(offerCatalog, offerNumber) },
                    enabled = !vm.zpdicOfferFetching
                ) { Text("照会") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vm.zpdicOfferStatus.isNotEmpty()) {
                    Text(
                        text = vm.zpdicOfferStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.zpdicOfferStatus.startsWith("照会成功"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = "出典なし = \"self\" / 0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(onClick = {
                    vm.clearZpdicList()
                    showZpdicBrowse = true
                }) { Text("一覧") }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // 単語ピッカーダイアログ
    if (showWordPicker) {
        WordPickerDialog(
            vm = vm,
            excludeIds = linkedWords.map { it.first }.toSet(),
            onSelected = { id, form ->
                linkedWords.add(Pair(id, form))
                showWordPicker = false
            },
            onDismiss = { showWordPicker = false }
        )
    }

    // ZpDIC 出典一覧ダイアログ
    if (showZpdicBrowse) {
        ZpdicOfferListDialog(
            vm = vm,
            initialCatalog = offerCatalog,
            onSelected = { number, trans, suppl ->
                offerNumber = number
                if (trans.isNotEmpty()) translation = trans
                if (suppl.isNotEmpty()) supplement = suppl
                vm.zpdicOfferStatus = "No. $number を選択しました"
                showZpdicBrowse = false
            },
            onDismiss = { showZpdicBrowse = false }
        )
    }

    // 削除確認ダイアログ
    if (showDeleteConfirm) {
        val preview = sentence.take(30) + if (sentence.length > 30) "…" else ""
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("削除確認") },
            text = { Text("「$preview」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    draft.originalId?.let { vm.deleteExample(it) }
                    navController.popBackStack()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

// ------------------------------------------------------------------
// 単語ピッカーダイアログ（関連単語選択用）
// ------------------------------------------------------------------

@Composable
private fun WordPickerDialog(
    vm: MainViewModel,
    excludeIds: Set<Int>,
    onSelected: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val candidates = remember(query) {
        vm.searchFormsForPicker(query).filter {
            DictionaryStore.idOf(it) !in excludeIds
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("関連単語を選択") },
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

// ------------------------------------------------------------------
// ZpDIC 出典一覧ダイアログ
// ------------------------------------------------------------------

@Composable
private fun ZpdicOfferListDialog(
    vm: MainViewModel,
    initialCatalog: String,
    onSelected: (number: Int, translation: String, supplement: String) -> Unit,
    onDismiss: () -> Unit
) {
    var catalog by remember {
        mutableStateOf(
            if (initialCatalog.isNotEmpty() && initialCatalog != "self") initialCatalog else ""
        )
    }
    val PAGE = 50

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ZpDIC 出典一覧") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = catalog,
                        onValueChange = { catalog = it },
                        label = { Text("カタログ名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { vm.loadZpdicOfferList(catalog) },
                        enabled = !vm.zpdicListLoading && catalog.isNotBlank()
                    ) { Text("取得") }
                }

                if (!vm.zpdicApiKeySet) {
                    Text(
                        "APIキーが設定されていません。環境設定から登録してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (vm.zpdicListLoading) {
                    Text(
                        "取得中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                vm.zpdicListError?.let { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (vm.zpdicListItems.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(vm.zpdicListItems) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelected(item.number, item.translation, item.supplement)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "No. ${item.number}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (item.author.isNotEmpty()) {
                                        Text(
                                            item.author,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (item.translation.isNotEmpty()) {
                                    Text(
                                        item.translation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (item.supplement.isNotEmpty()) {
                                    Text(
                                        item.supplement,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Divider()
                        }
                        if (vm.zpdicListItems.size % PAGE == 0 && vm.zpdicListItems.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = { vm.loadZpdicOfferList(catalog, vm.zpdicListItems.size, PAGE) },
                                    enabled = !vm.zpdicListLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("さらに読み込む") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}
