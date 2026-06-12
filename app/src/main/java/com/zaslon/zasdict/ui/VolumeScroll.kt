package com.zaslon.zasdict.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 音量キーによるページスクロール。
 *
 * MainActivity が onKeyDown で音量キーを受け取り、現在表示中の画面が
 * 登録したハンドラへ転送する。E-ink端末ではアニメーションなしの
 * 即時スクロール（scrollBy）で1回押すごとに約3/4画面ぶん移動する。
 * 音量UP=上へ / 音量DOWN=下へ。
 */
object VolumeKeyScroller {
    /** isUp=true なら上スクロール。処理したら true を返す（音量変更を抑止） */
    @Volatile
    var handler: ((isUp: Boolean) -> Boolean)? = null
}

/**
 * 画面のスクロール対象（ScrollState / LazyListState いずれも可）を
 * 音量キーに紐付ける。画面が表示されている間だけ有効。
 */
@Composable
fun VolumeScrollEffect(scrollableState: ScrollableState) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // 1回のキー押下でスクロールする量（画面高の約75%）
    val pagePx = with(density) { (configuration.screenHeightDp.dp * 0.75f).toPx() }

    DisposableEffect(scrollableState, pagePx) {
        val myHandler: (Boolean) -> Boolean = { isUp ->
            scope.launch {
                scrollableState.scrollBy(if (isUp) -pagePx else pagePx)
            }
            true
        }
        VolumeKeyScroller.handler = myHandler
        onDispose {
            // 画面遷移時、新しい画面のハンドラを誤って消さないように
            // 自分が登録したハンドラのままである場合のみ解除する
            if (VolumeKeyScroller.handler === myHandler) {
                VolumeKeyScroller.handler = null
            }
        }
    }
}
