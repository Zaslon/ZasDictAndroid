package com.zaslon.zasdict.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.Routes
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.data.StorageMode
import com.zaslon.zasdict.domain.Const
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    vm: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var contextMenuFor by remember { mutableStateOf<Int?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<JSONObject?>(null) }

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

    val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.fontScale

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
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
                    // クラウドモード：未同期インジケータ
                    if (isDropbox && vm.dropboxHasPendingUpload) {
                        IconButton(onClick = { vm.uploadToDropbox() }) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Dropboxに保存",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (isGitHub && vm.githubHasPendingUpload) {
                        IconButton(onClick = { vm.uploadToGitHub() }) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "GitHubにコミット",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (isBox && vm.boxHasPendingUpload) {
                        IconButton(onClick = { vm.uploadToBox() }) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Boxに保存",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (isDropbox) {
                            // Dropboxモードのメニュー
                            DropdownMenuItem(text = { Text("Dropboxから開く") }, onClick = {
                                menuExpanded = false
                                vm.openDropboxBrowser()
                            })
                            DropdownMenuItem(text = { Text("Dropboxに保存") }, onClick = {
                                menuExpanded = false
                                vm.uploadToDropbox()
                            })
                            DropdownMenuItem(text = { Text("Dropboxから再読込") }, onClick = {
                                menuExpanded = false
                                vm.reloadFromDropbox()
                            })
                        } else if (isGitHub) {
                            // GitHubモードのメニュー
                            DropdownMenuItem(text = { Text("GitHubから開く") }, onClick = {
                                menuExpanded = false
                                vm.openGitHubBrowser()
                            })
                            DropdownMenuItem(text = { Text("GitHubにコミット") }, onClick = {
                                menuExpanded = false
                                vm.uploadToGitHub()
                            })
                            DropdownMenuItem(text = { Text("GitHubから再読込") }, onClick = {
                                menuExpanded = false
                                vm.reloadFromGitHub()
                            })
                        } else if (isBox) {
                            // Boxモードのメニュー
                            DropdownMenuItem(text = { Text("Boxから開く") }, onClick = {
                                menuExpanded = false
                                vm.openBoxBrowser()
                            })
                            DropdownMenuItem(text = { Text("Boxに保存") }, onClick = {
                                menuExpanded = false
                                vm.uploadToBox()
                            })
                            DropdownMenuItem(text = { Text("Boxから再読込") }, onClick = {
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
                        DropdownMenuItem(text = { Text("IPA") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.tools(1))
                        })
                        DropdownMenuItem(text = { Text("凡例") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.LEGEND)
                        })
                        Divider()
                        DropdownMenuItem(text = { Text("環境設定") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.SETTINGS)
                        })
                        DropdownMenuItem(text = { Text("辞書依存設定") }, onClick = {
                            menuExpanded = false
                            navController.navigate(Routes.DICT_SETTINGS)
                        })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.startNewEntry(vm.searchText)
                    if (vm.editorDraft != null) navController.navigate(Routes.EDITOR)
                },
                elevation = if (einkMode) {
                    FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                } else {
                    FloatingActionButtonDefaults.elevation()
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新規登録")
            }
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
                value = vm.searchText,
                onValueChange = { vm.onSearchTextChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .heightIn(min = minSearchFieldHeight),
                placeholder = { Text("検索語を入力...", style = searchTextStyle) },
                singleLine = true,
                textStyle = searchTextStyle,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    val hasText = vm.searchText.isNotEmpty()
                    IconButton(
                        onClick = { vm.onSearchTextChanged("") },
                        enabled = hasText,
                        modifier = Modifier.alpha(if (hasText) 1f else 0f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "クリア")
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
                        label = { Text(mode) }
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
                        label = { Text(scope) }
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

            if (vm.searchText.isNotEmpty() && displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("該当する語がありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                state = resultListState,
                modifier = if (einkMode) Modifier.fillMaxSize().nestedScroll(einkScrollConnection) else Modifier.fillMaxSize()
            ) {
                items(displayItems) { (display, word) ->
                    val id = DictionaryStore.idOf(word)
                    Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { navController.navigate(Routes.detail(id)) },
                                    onLongClick = { contextMenuFor = id }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = display,
                                fontSize = baseFontSize,
                                fontWeight = FontWeight.Medium,
                                fontFamily = vm.headwordFontFamily
                            )
                            val firstTranslation = DictionaryStore.translationsOf(word)
                                .firstOrNull()
                                ?.let { (title, forms) -> "$title：${forms.joinToString(", ")}" }
                            if (firstTranslation != null) {
                                Text(
                                    text = firstTranslation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = contextMenuFor == id,
                            onDismissRequest = { contextMenuFor = null }
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
                                deleteConfirmFor = word
                            })
                        }
                    }
                    Divider()
                }
            }
        }
    }

    deleteConfirmFor?.let { word ->
        DeleteConfirmDialog(
            form = DictionaryStore.formOf(word),
            onConfirm = {
                vm.deleteWord(DictionaryStore.idOf(word))
                deleteConfirmFor = null
            },
            onDismiss = { deleteConfirmFor = null }
        )
    }
}

@Composable
fun DeleteConfirmDialog(form: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除の確認") },
        text = { Text("「$form」を削除しますか？\n他の単語からの参照も削除されます。") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) { Text("削除") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
