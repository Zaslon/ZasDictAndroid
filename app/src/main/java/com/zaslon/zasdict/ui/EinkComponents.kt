package com.zaslon.zasdict.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaslon.zasdict.ui.theme.LocalEinkMode

/**
 * E-inkモードでは影（elevation）のない枠線カード、
 * 通常モードでは標準の Material3 カードとして描画する共通コンポーネント。
 */
@Composable
fun ZasCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalEinkMode.current) {
        OutlinedCard(
            modifier = modifier,
            elevation = CardDefaults.outlinedCardElevation(
                defaultElevation = 0.dp
            ),
            content = content
        )
    } else {
        Card(modifier = modifier, content = content)
    }
}
