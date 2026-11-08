package com.zaslon.zasdict.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.BuildConfig
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.R
import com.zaslon.zasdict.Routes
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.data.StorageMode
import com.zaslon.zasdict.domain.Const
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import com.zaslon.zasdict.ui.theme.ZasTheme
import com.zaslon.zasdict.ui.theme.einkFocusFrame

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    vm: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    onWordClick: ((Int) -> Unit)? = null,
    selectedWordId: Int? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var contextMenuFor by remember { mutableStateOf<Int?>(null) }
    var showVersionDialog by remember { mutableStateOf(false) }

    val einkMode = LocalEinkMode.current
    val isDropbox = vm.storageMode == StorageMode.DROPBOX
    val isGitHub = vm.storageMode == StorageMode.GITHUB
    val isBox = vm.storageMode == StorageMode.BOX

    val resultListState = rememberLazyListState()
    VolumeScrollEffect(resultListState)
    val einkScrollConnection = rememberEinkNestedScrollConnection(resultListState)

    // ローカルモード用ランチャー
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.openDictionary(it) } }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.saveAs(it) } }

    val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize

    // TopAppBar の既定高さ(64dp)は固定で、タイトル+語数の2行を拡大文字で入れると下の行が潰れる。
    // 行高さ(sp)を dp に換算して、端末の文字サイズ設定に応じて高さを広げる。
    val appBarHeight = with(LocalDensity.current) {
        (MaterialTheme.typography.titleMedium.lineHeight.toDp() +
            MaterialTheme.typography.labelSmall.lineHeight.toDp() + 16.dp)
    }.coerceAtLeast(TopAppBarDefaults.TopAppBarExpandedHeight)

    // Dropboxブラウザダイアログ
    if (vm.dropboxBrowserTarget == "dict") {
        DropboxFileBrowserDialog(
            entries = vm.dropboxBrowserEntries,
            currentPath = vm.dropboxBrowserPath,
            isLoading = vm.dropboxBrowserLoading,
            error = vm.dropboxBrowserError,
            fileFilter = { it.endsWith(".json", ignoreCase = true) },
            onSelect = { path, name -> vm.openFromDropbox(path, name) },
            onDismiss = { vm.dismissDropboxBrowser() },
            onNavigate = { path -> vm.dropboxNavigateTo(path) },
            onNavigateUp = { vm.dropboxNavigateUp() }
        )
    }

    // GitHubブラウザダイアログ
    if (vm.githubBrowserTarget == "dict") {
        GitHubFileBrowserDialog(
            entries = vm.githubBrowserEntries,
            currentPath = vm.githubBrowserPath,
            repoLabel = "${vm.prefs.githubOwner}/${vm.prefs.githubRepo}",
            isLoading = vm.githubBrowserLoading,
            error = vm.githubBrowserError,
            fileFilter = { it.endsWith(".json", ignoreCase = true) },
            onSelect = { path, name -> vm.openFromGitHub(path, name) },
            onDismiss = { vm.dismissGitHubBrowser() },
            onNavigate = { path -> vm.gitHubNavigateTo(path) },
            onNavigateUp = { vm.gitHubNavigateUp() }
        )
    }

    // Boxブラウザダイアログ
    if (vm.boxBrowserTarget == "dict") {
        BoxFileBrowserDialog(
            entries = vm.boxBrowserEntries,
            folderName = vm.boxBrowserFolderName,
            isRoot = vm.boxBrowserFolderStack.isEmpty() && vm.boxBrowserFolderId == "0",
            isLoading = vm.boxBrowserLoading,
            error = vm.boxBrowserError,
            fileFilter = { it.endsWith(".json", ignoreCase = true) },
            onSelect = { id, name -> vm.openFromBox(id, name) },
            onDismiss = { vm.dismissBoxBrowser() },
            onNavigate = { id, name -> vm.boxNavigateTo(id, name) },
            onNavigateUp = { vm.boxNavigateUp() },
            currentFolderId = vm.boxBrowserFolderId
        )
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                expandedHeight = appBarHeight,
                title = {
                    Column {
                        Text(
                            text = buildString {
                                append(Const.APP_TITLE)
                                vm.fileName?.let { append("：$it") }
                                if (vm.hasUnsavedChanges) append(" *")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "${vm.wordCount} 語",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // 通信中の表示はスナックバーに出す（MainActivity）。ここでは保存操作を止めるだけ。
                    // クラウドモード：未同期インジケータ
                    if (isDropbox && vm.dropboxHasPendingUpload) {
                        IconButton(onClick = { vm.uploadToDropbox() }, enabled = !vm.isSyncing) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Dropboxに保存"
                            )
                        }
                    }
                    if (isGitHub && vm.githubHasPendingUpload) {
                        IconButton(onClick = { vm.uploadToGitHub() }, enabled = !vm.isSyncing) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "GitHubにコミット"
                            )
                        }
                    }
                    if (isBox && vm.boxHasPendingUpload) {
                        IconButton(onClick = { vm.uploadToBox() }, enabled = !vm.isSyncing) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Boxに保存"
                            )
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.einkFocusFrame()
                    ) {
                        if (isDropbox) {
                            // Dropboxモードのメニュー
                            DropdownMenuItem(text = { Text("Dropboxから開く") }, onClick = {
                                menuExpanded = false
                                vm.openDropboxBrowser()
                            })
                            DropdownMenuItem(text = { Text("Dropboxに保存") }, enabled = !vm.isSyncing, onClick = {
                                menuExpanded = false
                                vm.uploadToDropbox()
                            })
                            DropdownMenuItem(text = { Text("Dropboxから読み込む") }, enabled = !vm.isSyncing, onClick = {
                                menuExpanded = false
                                vm.reloadFromDropbox()
                            })
                        } else if (isGitHub) {
                            // GitHubモードのメニュー
                            DropdownMenuItem(text = { Text("GitHubから開く") }, onClick = {
                                menuExpanded = false
                                vm.openGitHubBrowser()
                            })
                            DropdownMenuItem(text = { Text("GitHubにコミット") }, enabled = !vm.isSyncing, onClick = {
                                menuExpanded = false
                                vm.uploadToGitHub()
                            })
                            DropdownMenuItem(text = { Text("GitHubから読み込む") }, enabled = !vm.isSyncing, onClick = {
                                menuExpanded = false
                                vm.reloadFromGitHub()
                            })
                        } else if (isBox) {
                            // Boxモードのメニュー
                            DropdownMenuItem(text = { Text("Boxから開く") }, onClick = {
                                menuExpanded = false
                                vm.openBoxBrowser()
                            })
                            DropdownMenuItem(text = { Text("Boxに保存") }, enabled = !vm.isSyncing, onClick = {
                                menuExpanded = false
                                vm.uploadToBox()
                            })
                            DropdownMenuItem(text = { Text("Boxから読み込む") }, enabled = !vm.isSyncing, onClick = {
                                menuExpanded = false
                                vm.reloadFromBox()
                            })
                        } else {
                            // ローカルモードのメニュー
                            DropdownMenuItem(text = { Text("開く") }, onClick = {
                                menuExpanded = false
                                openLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                            })
                            DropdownMenuItem(text = { Text("上書き保存") }, onClick = {
                                menuExpanded = false
                                if (!vm.saveFile()) {
                                    saveAsLauncher.launch(vm.fileName ?: "dictionary.json")
                                }
                            })
                            DropdownMenuItem(text = { Text("名前を付けて保存") }, onClick = {
                                menuExpanded = false
                                saveAsLauncher.launch(vm.fileName ?: "dictionary.json")
                            })
                        }
                        DropdownMenuItem(text = { Text("例文") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.EXAMPLES)
                        })
                        DropdownMenuItem(text = { Text("更新履歴") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.CHANGELOG)
                        })
                        Divider()
                        DropdownMenuItem(text = { Text("変換") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.tools(0))
                        })
                        DropdownMenuItem(text = { Text("IPA → 綴り") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.tools(1))
                        })
                        DropdownMenuItem(text = { Text("凡例") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.LEGEND)
                        })
                        Divider()
                        DropdownMenuItem(text = { Text("設定") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.SETTINGS)
                        })
                        DropdownMenuItem(text = { Text("辞書依存設定") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.DICT_SETTINGS)
                        })
                        Divider()
                        DropdownMenuItem(text = { Text("バージョン情報") }, onClick = {
                            menuExpanded = false
                            showVersionDialog = true
                        })
                    }
                }
            )
        },
        floatingActionButton = {
            ZasFab(
                onClick = {
                    vm.startNewEntry(vm.searchText)
                    if (vm.editorDraft != null) navController.navigate(Routes.EDITOR)
                },
                icon = rememberVectorPainter(Icons.Default.Add),
                contentDescription = "単語を追加",
                enabled = vm.isDictionaryLoaded
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val searchTextStyle = TextStyle(
                fontSize = baseFontSize,
                lineHeight = baseFontSize * 1.4f,
                fontFamily = vm.headwordFontFamily
            )
            val density = LocalDensity.current
            val minSearchFieldHeight = remember(baseFontSize, density) {
                val lineHeightDp = with(density) { (baseFontSize * 1.4f).toDp() }
                maxOf(56.dp, lineHeightDp + 36.dp)
            }
            OutlinedTextField(
                colors = zasTextFieldColors(),
                value = vm.searchText,
                onValueChange = { vm.onSearchTextChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .heightIn(min = minSearchFieldHeight),
                placeholder = { Text("検索語を入力...", style = searchTextStyle) },
                singleLine = true,
                textStyle = searchTextStyle,
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                trailingIcon = {
                    val hasText = vm.searchText.isNotEmpty()
                    IconButton(
                        onClick = { vm.onSearchTextChanged("") },
                        enabled = hasText,
                        modifier = Modifier.alpha(if (hasText) 1f else 0f)
                    ) {
                        Icon(painterResource(R.drawable.close), contentDescription = "検索語を消去")
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Const.SEARCH_MODES.forEach { mode ->
                    FilterChip(
                        selected = vm.searchMode == mode,
                        onClick = { vm.onSearchModeChanged(mode) },
                        label = { Text(mode) },
                        leadingIcon = { SelectedMark(vm.searchMode == mode) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Const.SEARCH_SCOPES.forEach { scope ->
                    FilterChip(
                        selected = vm.searchScope == scope,
                        onClick = { vm.onSearchScopeChanged(scope) },
                        label = { Text(scope) },
                        leadingIcon = { SelectedMark(vm.searchScope == scope) }
                    )
                }
            }

            val displayItems = remember(vm.results) {
                val seen = mutableMapOf<String, Int>()
                vm.results.map { word ->
                    val form = DictionaryStore.formOf(word)
                    val count = seen.getOrDefault(form, 0)
                    seen[form] = count + 1
                    val display = if (count > 0) "$form (${count + 1})" else form
                    Pair(display, word)
                }
            }

            // 一覧が空のときは、理由と次にすることを示す
            val emptyMessage = when {
                !vm.isDictionaryLoaded && (vm.isReadingDictionary || vm.isSyncing) -> "辞書を読み込んでいます。"
                !vm.isDictionaryLoaded -> "辞書が開かれていません。メニューから辞書を開いてください。"
                vm.searchText.isNotEmpty() && displayItems.isEmpty() ->
                    if (vm.searchMode != "部分") "該当する見出し語はありません。検索モードを「部分」にしてみてください。"
                    else "該当する見出し語はありません。"
                else -> null
            }
            if (emptyMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                state = resultListState,
                modifier = if (einkMode) Modifier.fillMaxSize().nestedScroll(einkScrollConnection) else Modifier.fillMaxSize()
            ) {
                items(displayItems) { (display, word) ->
                    val id = DictionaryStore.idOf(word)
                    val isSelected = id == selectedWordId
                    Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier.background(ZasTheme.colors.selectedBackground)
                                    else Modifier
                                )
                                .semantics { selected = isSelected }
                                .combinedClickable(
                                    onClick = {
                                        if (onWordClick != null) onWordClick(id)
                                        else navController.navigate(Routes.detail(id))
                                    },
                                    onLongClick = { contextMenuFor = id }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = display,
                                fontSize = baseFontSize,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = vm.headwordFontFamily,
                                color = if (isSelected) ZasTheme.colors.onSelectedBackground else Color.Unspecified
                            )
                            val firstTranslation = DictionaryStore.translationsOf(word)
                                .firstOrNull()
                                ?.let { (title, forms) -> "$title：${forms.joinToString(", ")}" }
                            if (firstTranslation != null) {
                                Text(
                                    text = firstTranslation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) ZasTheme.colors.onSelectedBackground
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = contextMenuFor == id,
                            onDismissRequest = { contextMenuFor = null },
                            modifier = Modifier.einkFocusFrame()
                        ) {
                            DropdownMenuItem(text = { Text("編集") }, onClick = {
                                contextMenuFor = null
                                vm.startEditEntry(id)
                                if (vm.editorDraft != null) navController.navigate(Routes.EDITOR)
                            })
                            DropdownMenuItem(text = { Text("複製") }, onClick = {
                                contextMenuFor = null
                                vm.startDuplicateEntry(id)
                                if (vm.editorDraft != null) navController.navigate(Routes.EDITOR)
                            })
                            DropdownMenuItem(text = { Text("削除") }, onClick = {
                                contextMenuFor = null
                                vm.deleteWord(id)
                            })
                        }
                    }
                    Divider()
                }
            }
        }
    }

    if (showVersionDialog) {
        VersionDialog(onDismiss = { showVersionDialog = false })
    }
}

/** 選択中のチップを、色だけでなく印でも示す */
@Composable
private fun SelectedMark(selected: Boolean) {
    if (selected) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun VersionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.einkFocusFrame(),
        title = { DialogTitle("バージョン情報") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ZasDict for Android", fontWeight = FontWeight.Bold)
                Text("バージョン: ${BuildConfig.VERSION_NAME}")
                // デジタル庁の素材の利用規約は、加工して使う場合に出典と加工内容の明示を求める
                Text(
                    "アイコンの一部は、「イラストレーション・アイコン素材」（デジタル庁）" +
                        "（https://www.digital.go.jp/policies/servicedesign/designsystem/Illustration_Icons）" +
                        "と「デザインシステム コード例（HTML版）」（デジタル庁、MIT License）を元に、" +
                        "色を変更できるよう加工して使用しています。" +
                        "その他のアイコンは Material Symbols（Google、Apache License 2.0）を元にしています。" +
                        "ライセンス文はアプリに同梱しています。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            ZasTextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}
