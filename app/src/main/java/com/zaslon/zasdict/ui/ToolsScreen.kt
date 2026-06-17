package com.zaslon.zasdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import com.zaslon.zasdict.domain.Ipa
import com.zaslon.zasdict.domain.Kaiomom

/**
 * ツール画面（デスクトップ版 MultiToolsWidget のタブ「変換」「IPA」に相当）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(vm: MainViewModel, navController: NavController, initialTab: Int) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val tabs = listOf("変換", "IPA")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ツール") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> DialectConvertTab(vm)
                1 -> IpaConvertTab()
            }
        }
    }
}

@Composable
private fun DialectConvertTab(vm: MainViewModel) {
    val einkMode = LocalEinkMode.current

    // 音量キーでスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)
    var input by remember { mutableStateOf("") }
    val result = remember(input) {
        if (input.isBlank()) null else try {
            Kaiomom.convertIdyer(input.trim())
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "変換エラー"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { if (einkMode) it.nestedScroll(einkScrollConnection).verticalScroll(scrollState) else it.verticalScroll(scrollState) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("元単語（アクセントは大文字）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (result != null) {
            if (result.containsKey("error")) {
                Text("エラー: ${result["error"]}", color = MaterialTheme.colorScheme.error)
            } else {
                ConvertResultCard("旗艦方言 (Sekore)", result["sekore"] ?: "")
                ConvertResultCard("資源循環艦方言 (Titauini)", result["titauini"] ?: "")
                ConvertResultCard("探査艦方言 (Kaiko)", result["kaiko"] ?: "")
                ConvertResultCard("教団暗号 (Arzafire)", result["arzafire"] ?: "")
            }
        }
    }
}

@Composable
private fun ConvertResultCard(title: String, value: String) {
    ZasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun IpaConvertTab() {
    val einkMode = LocalEinkMode.current

    // 音量キーでスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)
    var input by remember { mutableStateOf("") }
    val converted = remember(input) {
        if (input.isBlank()) "" else Ipa.ipaToSpell(input)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { if (einkMode) it.nestedScroll(einkScrollConnection).verticalScroll(scrollState) else it.verticalScroll(scrollState) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("IPA を入力") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (converted.isNotEmpty()) {
            ConvertResultCard("変換後", converted)
        }
    }
}
