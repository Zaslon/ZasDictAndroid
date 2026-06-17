package com.zaslon.zasdict.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
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

/**
 * E-inkモード用のスクロール制御。
 * ドラッグ中はスクロール位置を更新せず、指を離したとき（onPreFling）に
 * 蓄積したY移動量をまとめてスクロールに適用する。
 *
 * nestedScroll を使うことで、タッチドラッグ・マウスホイール・
 * リスト内クリックとの競合をすべて標準の Compose 機構に任せられる。
 */
@Composable
fun rememberEinkNestedScrollConnection(state: ScrollableState): NestedScrollConnection {
    return remember(state) {
        object : NestedScrollConnection {
            var accumulatedY = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                accumulatedY += available.y
                return available // スクロールを全量消費して画面を動かさない
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val delta = accumulatedY
                accumulatedY = 0f
                state.scrollBy(-delta) // 指を離した瞬間に蓄積分を確定
                return available // フリング速度も消費してアニメーションなし
            }
        }
    }
}
