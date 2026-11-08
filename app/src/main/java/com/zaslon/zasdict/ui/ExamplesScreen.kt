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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.zaslon.zasdict.ui.theme.ZasTheme
import com.zaslon.zasdict.DraftExample
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.R
import com.zaslon.zasdict.Routes
import com.zaslon.zasdict.domain.Const
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import com.zaslon.zasdict.ui.theme.einkFocusFrame
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
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("例文") },
                navigationIcon = {
                    BackButton(onClick = { navController.popBackStack() })
                }
            )
        },
        floatingActionButton = {
            ZasFab(
                onClick = {
                    vm.startNewExample()
                    if (vm.exampleDraft != null) navController.navigate(Routes.EXAMPLE_EDITOR)
                },
                icon = rememberVectorPainter(Icons.Default.Add),
                contentDescription = "例文を追加"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                colors = zasTextFieldColors(),
                value = query,
                onValueChange = { query = it },
                label = { Text("例文・訳を検索") },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(painterResource(R.drawable.close), contentDescription = "検索語を消去")
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
                    text = if (allExamples.isEmpty()) "例文がありません。「例文を追加」ボタンで追加してください。"
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
        color = ZasTheme.colors.accent,
        modifier = Modifier.semantics { heading() }
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
    var catalogExpanded by remember { mutableStateOf(false) }

    val isSelfCatalog = offerCatalog == Const.EXAMPLE_CATALOG_SELF

    var showWordPicker by remember { mutableStateOf(false) }

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

    // 退避から戻した場合は、戻した内容がすでに編集済みなので、退避しておいた開いた時点の状態と比べる
    val initialDraft = remember { vm.exampleDraftBaseline ?: buildDraft() }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.stashExampleDraft(initialDraft, buildDraft())
    }

    // 保存を押すまでは空欄を咎めない
    var showSentenceError by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "例文を追加" else "例文を編集") },
                navigationIcon = {
                    EditorCloseButton(
                        hasChanges = buildDraft() != initialDraft,
                        subject = sentence.take(30) + if (sentence.length > 30) "…" else ""
                    ) {
                        vm.discardExampleDraft()
                        navController.popBackStack()
                    }
                },
                actions = {
                    if (!isNew) {
                        // 削除は通知の「元に戻す」で取り消せるので、確認を挟まない
                        IconButton(onClick = {
                            draft.originalId?.let { vm.deleteExample(it) }
                            vm.discardExampleDraft()
                            navController.popBackStack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "例文を削除")
                        }
                    }
                    IconButton(onClick = {
                        if (sentence.isBlank()) {
                            showSentenceError = true
                        } else if (vm.commitExampleDraft(buildDraft())) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "登録")
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

            // 補足文は常に出しておき、エラーの文と入れ替える（出し入れすると下の欄の位置が動く）
            val sentenceMissing = showSentenceError && sentence.isBlank()
            OutlinedTextField(
                colors = zasTextFieldColors(),
                value = sentence,
                onValueChange = { sentence = it },
                label = { Text("文") },
                isError = sentenceMissing,
                supportingText = { Text(if (sentenceMissing) "文を入力してください" else "必須") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = TextStyle(fontFamily = vm.headwordFontFamily)
            )

            OutlinedTextField(
                colors = zasTextFieldColors(),
                value = translation,
                onValueChange = { translation = it },
                label = { Text("訳") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                colors = zasTextFieldColors(),
                value = supplement,
                onValueChange = { supplement = it },
                label = { Text("補足") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1
            )

            OutlinedTextField(
                colors = zasTextFieldColors(),
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
                                    painterResource(R.drawable.close),
                                    contentDescription = "${form}を関連単語から外す",
                                    modifier = Modifier.clickable { linkedWords.removeAt(index) }
                                )
                            }
                        )
                    }
                }
            }
            ZasOutlinedButton(onClick = { showWordPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("単語を追加")
            }

            // ----------------------------------------------------------
            SectionHeader("出典")
            val catalogLabel = Const.EXAMPLE_CATALOG_OPTIONS
                .find { it.first == offerCatalog }?.second ?: offerCatalog
            ExposedDropdownMenuBox(
                expanded = catalogExpanded,
                onExpandedChange = { catalogExpanded = it }
            ) {
                OutlinedTextField(
                    colors = zasTextFieldColors(),
                    value = catalogLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("カタログ") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = catalogExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = catalogExpanded,
                    onDismissRequest = { catalogExpanded = false },
                    modifier = Modifier.einkFocusFrame()
                ) {
                    Const.EXAMPLE_CATALOG_OPTIONS.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                offerCatalog = key
                                offerNumber = if (key == Const.EXAMPLE_CATALOG_SELF && isNew)
                                    vm.nextSelfOfferNumber()
                                else
                                    0
                                vm.zpdicOfferStatus = ""
                                catalogExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
            if (isSelfCatalog) {
                Text(
                    text = "No. $offerNumber（自動採番）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        colors = zasTextFieldColors(),
                        value = if (offerNumber == 0) "" else offerNumber.toString(),
                        onValueChange = { v ->
                            if (v.all { it.isDigit() }) {
                                offerNumber = v.toIntOrNull() ?: 0
                                vm.zpdicOfferStatus = ""
                            }
                        },
                        label = { Text("No.") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    ZasTextButton(
                        onClick = { vm.fetchZpdicOffer(offerCatalog, offerNumber) },
                        enabled = !vm.zpdicOfferFetching
                    ) { Text("照会") }
                }
                if (vm.zpdicOfferStatus.isNotEmpty()) {
                    Text(
                        text = vm.zpdicOfferStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.zpdicOfferStatus.startsWith("照会成功"))
                            MaterialTheme.colorScheme.onSurface
                        else
                            ZasTheme.colors.errorText
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // 単語ピッカーダイアログ
    if (showWordPicker) {
        WordFormPickerDialog(
            vm = vm,
            title = "関連単語を選択",
            excludeIds = linkedWords.map { it.first }.toSet(),
            onSelected = { id, form ->
                linkedWords.add(Pair(id, form))
                showWordPicker = false
            },
            onDismiss = { showWordPicker = false }
        )
    }

}

