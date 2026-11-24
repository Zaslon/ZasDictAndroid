package com.zaslon.zasdict.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 角を超楕円（|x|⁴ + |y|⁴ = 1 の 4 分の 1）で描く形。値は角の曲線が辺に沿って占める長さ（RoundedCornerShape と同じ）。
 *
 * デザインシステムは円以外の角丸をすべて超楕円で描くと定めている。RoundedCornerShape の円弧は
 * 直線との継ぎ目で曲率が急に変わる。CornerBasedShape を継承すると、角ごとの値・右から左へ書く言語での
 * 左右の入れ替え・copy() が標準の Shape と同じに扱え、SegmentedButtonDefaults.itemShape の baseShape にも渡せる。
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp) : this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius))

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ) = SquircleShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        // Modifier.border は Generic の輪郭を外接枠の大きさの Bitmap に描き、幅か高さが 0 だと例外で落ちる。
        // RoundedCornerShape はこの経路を通らず大きさ 0 でも落ちないため、同じに扱えるよう矩形で返す。
        if (size.width <= 0f || size.height <= 0f || topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd
        val w = size.width
        val h = size.height
        val path = Path()
        // 各角を、辺から離れる点から次の辺に着く点まで時計回りに辿る。(cx, cy) は角の頂点、(dx, dy) は内側への向き。
        corner(path, 0f, 0f, 1f, 1f, topLeft, first = true)
        corner(path, w, 0f, -1f, 1f, topRight, first = false)
        corner(path, w, h, -1f, -1f, bottomRight, first = false)
        corner(path, 0f, h, 1f, -1f, bottomLeft, first = false)
        path.close()
        return Outline.Generic(path)
    }

    private fun corner(path: Path, cx: Float, cy: Float, dx: Float, dy: Float, r: Float, first: Boolean) {
        if (r <= 0f) {
            if (first) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            return
        }
        // 時計回りに辿るため、左上と右下は縦の辺から横の辺へ、右上と左下は横の辺から縦の辺へ進む
        val verticalFirst = dx * dy > 0
        for (i in 0..SEGMENTS) {
            val t = (PI / 2 * i / SEGMENTS).toFloat()
            // cos(π/2) は浮動小数で僅かに負になり、sqrt が NaN を返して輪郭が壊れるため 0 で止める
            val a = sqrt(cos(t).coerceAtLeast(0f))
            val b = sqrt(sin(t).coerceAtLeast(0f))
            val (ex, ey) = if (verticalFirst) a to b else b to a
            val x = cx + dx * r * (1 - ex)
            val y = cy + dy * r * (1 - ey)
            if (first && i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return result
    }

    override fun toString() = "SquircleShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"

    private companion object {
        const val SEGMENTS = 24
    }
}
