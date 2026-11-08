package com.zaslon.zasdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.ui.theme.LocalEinkMode

/**
 * 辞書依存設定画面（DictionarySettingsDialog に相当）。
 * zpdicOnline セクションの punctuations / ignoredPattern を編集する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsScreen(vm: MainViewModel, navController: NavController, snackbarHostState: SnackbarHostState) {

    val einkMode = LocalEinkMode.current

    // 音量キーでスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)

    val initialPunctuations = remember { vm.store.getPunctuations().joinToString("") }
    val initialIgnoredPattern = remember { vm.store.getIgnoredPattern() }
    var punctuations by remember { mutableStateOf(initialPunctuations) }
    var ignoredPattern by remember { mutableStateOf(initialIgnoredPattern) }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("辞書依存設定") },
                navigationIcon = {
                    EditorCloseButton(
                        hasChanges = punctuations != initialPunctuations || ignoredPattern != initialIgnoredPattern,
                        subject = "辞書依存設定"
                    ) { navController.popBackStack() }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!vm.store.isLoaded) {
                Text(
                    "先に辞書ファイルを開いてください。",
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = punctuations,
                onValueChange = { punctuations = it },
                label = { Text("区切り文字（1文字ずつ続けて入力）") },
                supportingText = { Text("例: 、, → 「、」と「,」が区切り文字になります") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ignoredPattern,
                onValueChange = { ignoredPattern = it },
                label = { Text("無視パターン（正規表現）") },
                supportingText = { Text("前方・後方一致検索の際に見出し語から除去されるパターン") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    vm.updateDictionarySettings(
                        punctuations = punctuations.map { it.toString() },
                        ignoredPattern = ignoredPattern
                    )
                    navController.popBackStack()
                },
                enabled = vm.store.isLoaded,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("保存")
            }
        }
    }
}
