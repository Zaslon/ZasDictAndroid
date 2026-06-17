package com.zaslon.zasdict.ui

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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zaslon.zasdict.MainViewModel
import com.zaslon.zasdict.ui.theme.LocalEinkMode
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.domain.Const

/**
 * 凡例画面（legend.py の LegendViewerWidget に相当）。
 * 品詞・関係の一覧と、現在の辞書の統計（品詞別語数・タグ別語数）を表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendScreen(vm: MainViewModel, navController: NavController) {

    val einkMode = LocalEinkMode.current

    // 音量キーでスクロール
    val scrollState = rememberScrollState()
    VolumeScrollEffect(scrollState)

    val einkScrollConnection = rememberEinkNestedScrollConnection(scrollState)

    // 辞書統計の集計
    val stats = remember(vm.wordCount, vm.hasUnsavedChanges) {
        val posCount = LinkedHashMap<String, Int>()
        val tagCount = LinkedHashMap<String, Int>()
        for (word in vm.store.wordList()) {
            for ((title, _) in DictionaryStore.translationsOf(word)) {
                if (title.isNotEmpty()) posCount[title] = (posCount[title] ?: 0) + 1
            }
            for (tag in DictionaryStore.tagsOf(word)) {
                tagCount[tag] = (tagCount[tag] ?: 0) + 1
            }
        }
        Pair(posCount, tagCount)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("凡例") },
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
                .let { if (einkMode) it.nestedScroll(einkScrollConnection).verticalScroll(scrollState) else it.verticalScroll(scrollState) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("品詞", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Const.VALID_POS.forEach { pos ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(pos, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                Const.POS_DESCRIPTIONS[pos] ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("関係と対照関係", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Const.VALID_RELATIONS.forEach { rel ->
                        val reciprocal = Const.RECIPROCAL_MAP[rel] ?: rel
                        Text(
                            if (rel == reciprocal) rel else "$rel ↔ $reciprocal",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            ZasCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("辞書統計", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("総語数: ${vm.wordCount}", fontWeight = FontWeight.Medium)
                    if (stats.first.isNotEmpty()) {
                        Divider()
                        Text("品詞別", style = MaterialTheme.typography.labelMedium)
                        stats.first.entries.sortedByDescending { it.value }.forEach { (pos, count) ->
                            Text("$pos：$count", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (stats.second.isNotEmpty()) {
                        Divider()
                        Text("タグ別", style = MaterialTheme.typography.labelMedium)
                        stats.second.entries.sortedByDescending { it.value }.forEach { (tag, count) ->
                            Text("$tag：$count", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
