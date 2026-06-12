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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import kotlin.math.roundToInt

/**
 * 環境設定画面（PreferencesDialog に相当）。
 * モバイルではウィンドウサイズ・UIフォント選択は不要なため、
 * フォントサイズ倍率・自動保存・Heksaフォントの設定を提供する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, navController: NavController) {

    // 音量キーでスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val fontPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importIdyerFont(it) } }

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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自動上書き保存を有効にする", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "編集のたびに辞書ファイルへ自動保存します",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vm.autoSave, onCheckedChange = { vm.updateAutoSave(it) })
                }
            }

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
