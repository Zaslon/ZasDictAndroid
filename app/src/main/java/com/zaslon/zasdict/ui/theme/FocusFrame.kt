package com.zaslon.zasdict.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * 高コントラストモードのフォーカス表示。子孫のうちフォーカス中の要素の外側に、白と黒の二重の枠を描く。
 *
 * このモードでは波紋を止めるため LocalRippleConfiguration に null を渡しており、Material の部品の
 * フォーカス表示（状態レイヤー）も一緒に消える。その代わりとして描く。
 * 白の背景では外側の黒が、反転した黒の面の上では内側の白が見える。
 *
 * ダイアログとポップアップ（DropdownMenu 等）は別のウィンドウに描かれ、画面の根に付けた枠が届かない。
 * それぞれの modifier にも付けること。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.einkFocusFrame(
    /**
     * メニューやダイアログを開いても、背後のウィンドウの中のフォーカスは残り、枠が 2 つ見える。
     * 画面の根では true にし、背後に回った間は描かない。ポップアップの中では isWindowFocused が
     * フォーカスを持っていても false になることがあるため、ダイアログとメニューでは false のままにする。
     */
    onlyWhenWindowFocused: Boolean = false
): Modifier {
    if (!LocalEinkMode.current) return this
    // 同じ要素が動いた場合も同じ LayoutCoordinates が届くため、等価でも再描画させる
    var focused by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    var self by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hidden = onlyWhenWindowFocused && !LocalWindowInfo.current.isWindowFocused
    return this
        .onGloballyPositioned { self = it }
        .onFocusedBoundsChanged { focused = it }
        .drawWithContent {
            drawContent()
            if (hidden) return@drawWithContent
            val target = focused ?: return@drawWithContent
            val root = self ?: return@drawWithContent
            if (!target.isAttached || !root.isAttached) return@drawWithContent
            val bounds = root.localBoundingBoxOf(target, clipBounds = true)
            if (bounds.isEmpty) return@drawWithContent
            drawFrame(bounds)
        }
}

private fun DrawScope.drawFrame(bounds: Rect) {
    val inner = 2.dp.toPx()
    val outer = 3.dp.toPx()
    val radius = CornerRadius(4.dp.toPx())
    ring(bounds, offset = inner / 2, width = inner, color = Color.White, radius = radius)
    ring(bounds, offset = inner + outer / 2, width = outer, color = Color.Black, radius = radius)
}

/** 要素から offset だけ外側に線を引く。画面の端の要素では外に出る分を内側へ寄せ、線が切れないようにする。 */
private fun DrawScope.ring(bounds: Rect, offset: Float, width: Float, color: Color, radius: CornerRadius) {
    val half = width / 2
    val left = (bounds.left - offset).coerceAtLeast(half)
    val top = (bounds.top - offset).coerceAtLeast(half)
    val right = (bounds.right + offset).coerceAtMost(size.width - half)
    val bottom = (bounds.bottom + offset).coerceAtMost(size.height - half)
    if (right <= left || bottom <= top) return
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = radius,
        style = Stroke(width)
    )
}
