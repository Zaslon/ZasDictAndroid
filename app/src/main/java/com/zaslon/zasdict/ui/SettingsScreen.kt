package com.zaslon.zasdict.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.data.StorageMode
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import kotlin.math.roundToInt

/**
 * 環境設定画面。ローカル/Dropboxのストレージモード選択と各種設定を提供する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, navController: NavController) {

    val einkMode = LocalEinkMode.current
    val context = LocalContext.current

    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)
    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)

    val fontPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importIdyerFont(it) } }

    var dropboxAppKeyInput by remember { mutableStateOf(vm.prefs.dropboxAppKey) }
    var githubTokenInput by remember { mutableStateOf(vm.prefs.githubToken ?: "") }
    var githubOwnerInput by remember { mutableStateOf(vm.prefs.githubOwner ?: "") }
    var githubRepoInput by remember { mutableStateOf(vm.prefs.githubRepo ?: "") }
    var githubBranchInput by remember { mutableStateOf(vm.prefs.githubBranch) }
    var boxClientIdInput by remember { mutableStateOf(vm.prefs.boxClientId) }
    var boxClientSecretInput by remember { mutableStateOf(vm.prefs.boxClientSecret) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("環境設定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .let {
                    if (einkMode) it.nestedScroll(einkScrollConnection).verticalScroll(scrollState)
                    else it.verticalScroll(scrollState)
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ----------------------------------------------------------
            // ストレージモード
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ストレージモード", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "辞書ファイルの読み書き先を選択します。クラウドモードでは変更はローカルに保存され、手動でクラウドに同期します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = vm.storageMode == StorageMode.LOCAL,
                            onClick = {
                                if (vm.storageMode != StorageMode.LOCAL)
                                    vm.updateStorageMode(StorageMode.LOCAL)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                        ) { Text("ローカル") }
                        SegmentedButton(
                            selected = vm.storageMode == StorageMode.DROPBOX,
                            onClick = {
                                if (vm.storageMode != StorageMode.DROPBOX)
                                    vm.updateStorageMode(StorageMode.DROPBOX)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                        ) { Text("Dropbox") }
                        SegmentedButton(
                            selected = vm.storageMode == StorageMode.GITHUB,
                            onClick = {
                                if (vm.storageMode != StorageMode.GITHUB)
                                    vm.updateStorageMode(StorageMode.GITHUB)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                        ) { Text("GitHub") }
                        SegmentedButton(
                            selected = vm.storageMode == StorageMode.BOX,
                            onClick = {
                                if (vm.storageMode != StorageMode.BOX)
                                    vm.updateStorageMode(StorageMode.BOX)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                        ) { Text("Box") }
                    }
                }
            }

            // ----------------------------------------------------------
            // Dropbox 設定（Dropboxモード時のみ表示）
            // ----------------------------------------------------------
            if (vm.storageMode == StorageMode.DROPBOX) {
                ZasCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dropbox 連携設定", style = MaterialTheme.typography.titleSmall)

                        if (vm.dropboxConnected) {
                            Text(
                                "接続済み${if (!vm.dropboxDisplayName.isNullOrEmpty()) "：${vm.dropboxDisplayName}" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!vm.dropboxDictName.isNullOrEmpty()) {
                                Text(
                                    "辞書ファイル: ${vm.dropboxDictName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (vm.dropboxHasPendingUpload) {
                                Text(
                                    "ローカルキャッシュにDropboxへ未同期の変更があります",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            TextButton(onClick = { vm.disconnectDropbox() }) {
                                Text("接続を解除", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Text(
                                "Dropbox Developer Console でアプリを作成し、App Key を入力してください。" +
                                "Redirect URI には「zasdict://dropbox-auth」を登録してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = dropboxAppKeyInput,
                                onValueChange = { dropboxAppKeyInput = it },
                                label = { Text("App Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    vm.updateDropboxAppKey(dropboxAppKeyInput)
                                    vm.launchDropboxAuth(context)
                                },
                                enabled = dropboxAppKeyInput.isNotBlank()
                            ) {
                                Text("Dropboxに接続する")
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------------
            // GitHub 設定（GitHubモード時のみ表示）
            // ----------------------------------------------------------
            if (vm.storageMode == StorageMode.GITHUB) {
                ZasCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GitHub 連携設定", style = MaterialTheme.typography.titleSmall)

                        if (vm.githubConnected) {
                            Text(
                                "接続済み：${vm.githubDisplayName ?: ""}（${vm.prefs.githubOwner}/${vm.prefs.githubRepo}）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!vm.githubDictName.isNullOrEmpty()) {
                                Text(
                                    "辞書ファイル: ${vm.githubDictName}（ブランチ: ${vm.prefs.githubBranch}）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (vm.githubHasPendingUpload) {
                                Text(
                                    "ローカルキャッシュにGitHubへ未同期の変更があります",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            TextButton(onClick = { vm.disconnectGitHub() }) {
                                Text("接続を解除", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Text(
                                "GitHubのPersonal Access Tokenを入力してください。Tokenにはリポジトリへの読み書き権限（Contents: Read and Write）が必要です。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = githubTokenInput,
                                onValueChange = { githubTokenInput = it },
                                label = { Text("Personal Access Token") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = githubOwnerInput,
                                onValueChange = { githubOwnerInput = it },
                                label = { Text("オーナー（ユーザー名 / Org名）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = githubRepoInput,
                                onValueChange = { githubRepoInput = it },
                                label = { Text("リポジトリ名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = githubBranchInput,
                                onValueChange = { githubBranchInput = it },
                                label = { Text("ブランチ（省略時: main）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    vm.connectGitHub(
                                        githubTokenInput,
                                        githubOwnerInput,
                                        githubRepoInput,
                                        githubBranchInput
                                    )
                                },
                                enabled = githubTokenInput.isNotBlank() &&
                                          githubOwnerInput.isNotBlank() &&
                                          githubRepoInput.isNotBlank()
                            ) {
                                Text("GitHubに接続する")
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------------
            // Box 設定（Boxモード時のみ表示）
            // ----------------------------------------------------------
            if (vm.storageMode == StorageMode.BOX) {
                ZasCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Box 連携設定", style = MaterialTheme.typography.titleSmall)

                        if (vm.boxConnected) {
                            Text(
                                "接続済み${if (!vm.boxDisplayName.isNullOrEmpty()) "：${vm.boxDisplayName}" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!vm.boxDictName.isNullOrEmpty()) {
                                Text(
                                    "辞書ファイル: ${vm.boxDictName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (vm.boxHasPendingUpload) {
                                Text(
                                    "ローカルキャッシュにBoxへ未同期の変更があります",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            TextButton(onClick = { vm.disconnectBox() }) {
                                Text("接続を解除", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Text(
                                "Box Developer Console でアプリを作成し、Client ID と Client Secret を入力してください。" +
                                "Redirect URI には「zasdict://box-auth」を登録してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = boxClientIdInput,
                                onValueChange = { boxClientIdInput = it },
                                label = { Text("Client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = boxClientSecretInput,
                                onValueChange = { boxClientSecretInput = it },
                                label = { Text("Client Secret") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    vm.updateBoxClientId(boxClientIdInput)
                                    vm.updateBoxClientSecret(boxClientSecretInput)
                                    vm.launchBoxAuth(context)
                                },
                                enabled = boxClientIdInput.isNotBlank() && boxClientSecretInput.isNotBlank()
                            ) {
                                Text("Boxに接続する")
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------------
            // フォントサイズ
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("フォントサイズ", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "倍率: ${(vm.fontScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = vm.fontScale,
                        onValueChange = { vm.updateFontScale(it) },
                        valueRange = 0.8f..1.8f,
                        steps = 9
                    )
                    Text(
                        "サンプル：あいうえお abcde",
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.fontScale
                    )
                }
            }

            // ----------------------------------------------------------
            // E-inkモード
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("E-inkモードを有効にする", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "アニメーション・影・リップル効果を排除し、白黒の高コントラスト表示にします（電子ペーパー端末向け）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vm.einkMode, onCheckedChange = { vm.updateEinkMode(it) })
                }
            }

            // ----------------------------------------------------------
            // 自動上書き保存
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自動上書き保存を有効にする", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (vm.storageMode) {
                                StorageMode.DROPBOX -> "編集のたびにローカルキャッシュへ自動保存します（Dropboxへのアップロードは手動）"
                                StorageMode.GITHUB -> "編集のたびにローカルキャッシュへ自動保存します（GitHubへのコミットは手動）"
                                StorageMode.BOX -> "編集のたびにローカルキャッシュへ自動保存します（Boxへのアップロードは手動）"
                                else -> "編集のたびに辞書ファイルへ自動保存します"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vm.autoSave, onCheckedChange = { vm.updateAutoSave(it) })
                }
            }

            // ----------------------------------------------------------
            // Heksa フォント
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Heksaを有効にする", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "見出し語・検索欄をイジェール文字フォントで表示します（.ttf形式のフォントファイルが必要です）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = vm.useIdyerFont, onCheckedChange = { vm.updateUseIdyerFont(it) })
                    }
                    OutlinedButton(onClick = {
                        fontPickLauncher.launch(arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream", "*/*"))
                    }) {
                        Text(if (vm.hasIdyerFontFile()) "フォントファイル（.ttf）を再選択" else "フォントファイル（.ttf）を選択")
                    }
                    if (vm.useIdyerFont && !vm.hasIdyerFontFile()) {
                        Text(
                            "フォントファイルが未取込のため、標準フォントで表示されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (vm.useIdyerFont && vm.idyerFontFamily != null) {
                        Text(
                            "サンプル：eaoiu heksa",
                            fontFamily = vm.idyerFontFamily,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.fontScale
                        )
                    }
                }
            }
        }
    }
}
