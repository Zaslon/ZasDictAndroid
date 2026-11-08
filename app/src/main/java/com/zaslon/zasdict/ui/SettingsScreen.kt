package com.zaslon.zasdict.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.ui.theme.ZasTheme
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.data.Prefs
import com.zaslon.zasdict.data.StorageMode
import com.zaslon.zasdict.ui.theme.ThemeMode
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import kotlin.math.roundToInt

/**
 * 環境設定画面。ローカル/Dropboxのストレージモード選択と各種設定を提供する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, navController: NavController, snackbarHostState: SnackbarHostState) {

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
    var zpdicApiKeyInput by remember { mutableStateOf("") }
    var zpdicKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    BackButton(onClick = { navController.popBackStack() })
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
                    RadioGroup(
                        options = listOf(
                            StorageMode.LOCAL to "ローカル",
                            StorageMode.DROPBOX to "Dropbox",
                            StorageMode.GITHUB to "GitHub",
                            StorageMode.BOX to "Box"
                        ),
                        selected = vm.storageMode,
                        onSelect = { if (vm.storageMode != it) vm.updateStorageMode(it) }
                    )
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
                                style = MaterialTheme.typography.bodyMedium
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            ZasTextButton(onClick = { vm.disconnectDropbox() }) {
                                Text("接続を解除", color = ZasTheme.colors.errorText)
                            }
                        } else {
                            Text(
                                "Dropbox Developer Console でアプリを作成し、App Key を入力してください。" +
                                "Redirect URI には「zasdict://dropbox-auth」を登録してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = dropboxAppKeyInput,
                                onValueChange = { dropboxAppKeyInput = it },
                                label = { Text("App Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            ZasButton(
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
                                style = MaterialTheme.typography.bodyMedium
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            ZasTextButton(onClick = { vm.disconnectGitHub() }) {
                                Text("接続を解除", color = ZasTheme.colors.errorText)
                            }
                        } else {
                            Text(
                                "GitHubのPersonal Access Tokenを入力してください。Tokenにはリポジトリへの読み書き権限（Contents: Read and Write）が必要です。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = githubTokenInput,
                                onValueChange = { githubTokenInput = it },
                                label = { Text("Personal Access Token") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = githubOwnerInput,
                                onValueChange = { githubOwnerInput = it },
                                label = { Text("オーナー（ユーザー名 / Org名）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = githubRepoInput,
                                onValueChange = { githubRepoInput = it },
                                label = { Text("リポジトリ名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = githubBranchInput,
                                onValueChange = { githubBranchInput = it },
                                label = { Text("ブランチ（省略時: main）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            ZasButton(
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
                                style = MaterialTheme.typography.bodyMedium
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            ZasTextButton(onClick = { vm.disconnectBox() }) {
                                Text("接続を解除", color = ZasTheme.colors.errorText)
                            }
                        } else {
                            Text(
                                "Box Developer Console でアプリを作成し、Client ID と Client Secret を入力してください。" +
                                "Redirect URI には「zasdict://box-auth」を登録してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = boxClientIdInput,
                                onValueChange = { boxClientIdInput = it },
                                label = { Text("Client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                colors = zasTextFieldColors(),
                                value = boxClientSecretInput,
                                onValueChange = { boxClientSecretInput = it },
                                label = { Text("Client Secret") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            ZasButton(
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
            // 配色
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("配色", style = MaterialTheme.typography.titleSmall)
                    // E-inkモードの間は白黒に固定されるため選べない。隠すと選択肢の存在が分からなくなるので、無効にして理由を示す。
                    if (vm.einkMode) {
                        Text(
                            "E-inkモードの間は白黒で表示します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val themeOptions = listOf(
                        ThemeMode.SYSTEM to "端末に従う",
                        ThemeMode.LIGHT to "ライト",
                        ThemeMode.DARK to "ダーク"
                    )
                    RadioGroup(
                        options = themeOptions,
                        selected = vm.themeMode,
                        onSelect = { vm.updateThemeMode(it) },
                        enabled = !vm.einkMode
                    )
                }
            }

            // ----------------------------------------------------------
            // 文字の大きさ
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("文字の大きさ", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "倍率: ${(vm.fontScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = vm.fontScale,
                        onValueChange = { vm.updateFontScale(it) },
                        valueRange = Prefs.FONT_SCALE_MIN..Prefs.FONT_SCALE_MAX,
                        // 10% 刻み
                        steps = 9,
                        modifier = Modifier.semantics { contentDescription = "文字の倍率" }
                    )
                }
            }

            // ----------------------------------------------------------
            // E-inkモード
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                CheckboxRow(
                    title = "E-inkモードを有効にする",
                    description = "アニメーション・影・リップル効果を排除し、白黒の高コントラスト表示にします（電子ペーパー端末向け）",
                    checked = vm.einkMode,
                    onCheckedChange = { vm.updateEinkMode(it) },
                    contentPadding = PaddingValues(16.dp)
                )
            }

            // ----------------------------------------------------------
            // 自動上書き保存
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                CheckboxRow(
                    title = "自動上書き保存を有効にする",
                    description = when (vm.storageMode) {
                        StorageMode.DROPBOX -> "編集のたびにローカルキャッシュへ自動保存します（Dropboxへは「Dropboxに保存」で保存します）"
                        StorageMode.GITHUB -> "編集のたびにローカルキャッシュへ自動保存します（GitHubへのコミットは手動）"
                        StorageMode.BOX -> "編集のたびにローカルキャッシュへ自動保存します（Boxへは「Boxに保存」で保存します）"
                        else -> "編集のたびに辞書ファイルへ自動保存します"
                    },
                    checked = vm.autoSave,
                    onCheckedChange = { vm.updateAutoSave(it) },
                    contentPadding = PaddingValues(16.dp)
                )
            }

            // ----------------------------------------------------------
            // Heksa フォント
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckboxRow(
                        title = "Heksaを有効にする",
                        description = "見出し語・検索欄をイジェール文字フォントで表示します（.ttf形式のフォントファイルが必要です）",
                        checked = vm.useIdyerFont,
                        onCheckedChange = { vm.updateUseIdyerFont(it) }
                    )
                    ZasOutlinedButton(onClick = {
                        fontPickLauncher.launch(arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream", "*/*"))
                    }) {
                        Text(if (vm.hasIdyerFontFile()) "フォントファイル（.ttf）を再選択" else "フォントファイル（.ttf）を選択")
                    }
                    if (vm.useIdyerFont && !vm.hasIdyerFontFile()) {
                        Text(
                            "フォントファイルが未取込のため、標準フォントで表示されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZasTheme.colors.errorText
                        )
                    }
                    if (vm.useIdyerFont && vm.idyerFontFamily != null) {
                        Text(
                            "サンプル：eaoiu heksa",
                            fontFamily = vm.idyerFontFamily,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // ----------------------------------------------------------
            // ZpDIC Online API キー
            // ----------------------------------------------------------
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ZpDIC Online APIキー", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "ZpDIC Online のAPIキーを設定すると、例文編集画面で出典の照会・一覧取得ができます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (vm.zpdicApiKeySet) {
                        Text(
                            "APIキー設定済み",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ZasTextButton(onClick = { vm.clearZpdicApiKey() }) {
                            Text("APIキーを削除", color = ZasTheme.colors.errorText)
                        }
                    } else {
                        OutlinedTextField(
                            colors = zasTextFieldColors(),
                            value = zpdicApiKeyInput,
                            onValueChange = { zpdicApiKeyInput = it },
                            label = { Text("APIキー") },
                            singleLine = true,
                            visualTransformation = if (zpdicKeyVisible) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { zpdicKeyVisible = !zpdicKeyVisible }) {
                                    Icon(
                                        if (zpdicKeyVisible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = if (zpdicKeyVisible) "APIキーを隠す" else "APIキーを表示"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ZasButton(
                            onClick = {
                                vm.saveZpdicApiKey(zpdicApiKeyInput)
                                zpdicApiKeyInput = ""
                            },
                            enabled = zpdicApiKeyInput.isNotBlank()
                        ) {
                            Text("APIキーを設定")
                        }
                    }
                }
            }
        }
    }
}

/**
 * ラジオグループ。状態を選択肢の言葉で示す。
 * 選択肢を横に並べると、狭い画面や文字の倍率を上げたときに選択肢の文字が折り返して枠からはみ出すため、縦に並べる。
 * 行全体を押せるようにし、支援技術にはひとまとまりの選択肢として伝える。
 */
@Composable
private fun <T> RadioGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = value == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(value) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = value == selected, onClick = null, enabled = enabled)
                Text(
                    label,
                    modifier = Modifier.padding(start = 12.dp),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else ZasTheme.colors.disabledText
                )
            }
        }
    }
}

/**
 * 説明付きのチェックボックス。文字の部分を押しても切り替わるよう、行全体を押せるようにする。
 * スライドトグル（Switch）は、つまみの位置そのものが現在の状態を表してしまい、
 * 押した結果を示すボタンと見分けが付かなくなるため使わない。
 */
@Composable
private fun CheckboxRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}