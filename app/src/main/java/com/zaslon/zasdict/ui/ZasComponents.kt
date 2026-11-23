package com.zaslon.zasdict.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaslon.zasdict.R
import com.zaslon.zasdict.ui.theme.ZasShape
import com.zaslon.zasdict.ui.theme.ZasTheme

// Material の既定のボタンは長丸（高さの半分を丸める形）で、デザインシステムが使わない形のため、
// ボタンはここの部品を使う。高さはタッチ操作の標準（size-control-md）を最小とし、文字が大きくなれば伸ばす。

private val ControlMinHeight = 48.dp

/** 塗りのボタン（主ボタン） */
@Composable
fun ZasButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ControlMinHeight),
        enabled = enabled,
        shape = ZasShape.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = ZasTheme.colors.disabled,
            disabledContentColor = ZasTheme.colors.onDisabled
        ),
        content = content
    )
}

/** 枠のボタン */
@Composable
fun ZasOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ControlMinHeight),
        enabled = enabled,
        shape = ZasShape.control,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = ZasTheme.colors.buttonText,
            disabledContentColor = ZasTheme.colors.disabledText
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) ZasTheme.colors.buttonBorder else ZasTheme.colors.disabledText
        ),
        content = content
    )
}

/** テキストのボタン */
@Composable
fun ZasTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ControlMinHeight),
        enabled = enabled,
        shape = ZasShape.control,
        colors = ButtonDefaults.textButtonColors(
            contentColor = ZasTheme.colors.buttonText,
            disabledContentColor = ZasTheme.colors.disabledText
        ),
        content = content
    )
}

/** 警告ボタン。取り消せない操作（削除等）を実行する側に使う。 */
@Composable
fun ZasDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ControlMinHeight),
        shape = ZasShape.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = ZasTheme.colors.destructive,
            contentColor = ZasTheme.colors.onDestructive
        ),
        content = content
    )
}

/**
 * 入力欄の色。Material の既定はフォーカス時のラベルを塗り用のアクセント（primary）で、
 * エラーの文字を警告ボタンの色（error）で描き、ダークでは文字として 4.5:1 に届かないため、文字用のトークンに替える。
 */
@Composable
fun zasTextFieldColors(): TextFieldColors {
    val z = ZasTheme.colors
    val text = MaterialTheme.colorScheme.onSurface
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = z.inputBackground,
        unfocusedContainerColor = z.inputBackground,
        errorContainerColor = z.inputBackground,
        focusedBorderColor = z.accent,
        unfocusedBorderColor = z.inputBorder,
        errorBorderColor = z.errorText,
        focusedLabelColor = z.accent,
        unfocusedLabelColor = z.textSecondary,
        errorLabelColor = z.errorText,
        focusedSupportingTextColor = z.textSecondary,
        unfocusedSupportingTextColor = z.textSecondary,
        errorSupportingTextColor = z.errorText,
        cursorColor = text,
        errorCursorColor = z.errorText,
        focusedLeadingIconColor = text,
        unfocusedLeadingIconColor = text,
        focusedTrailingIconColor = text,
        unfocusedTrailingIconColor = text,
        errorTrailingIconColor = z.errorText,
        disabledTextColor = z.disabledText,
        disabledBorderColor = z.disabledText,
        disabledLabelColor = z.disabledText,
    )
}

/** 確認ダイアログの見出し。支援技術に確認文の見出しとして伝える。 */
@Composable
fun DialogTitle(text: String) {
    Text(text, modifier = Modifier.semantics { heading() })
}

/** 上部バー左端の「戻る」。OS の戻る操作と同じく、直前の画面へ移動する。 */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.arrow_back), contentDescription = "戻る")
    }
}

/**
 * 固定配置ボタン。アクセントカラー（塗り用）の丸いボタンとする。
 * FloatingActionButton には enabled が無いため、無効の見た目・読み上げ・Tab での選択可否を自前で付ける。
 * 影は使わない（デザインシステムは影を重なる要素にだけ許可しており、固定配置ボタンは含まれない）。
 */
@Composable
fun ZasFab(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String,
    enabled: Boolean = true
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .semantics { if (!enabled) disabled() }
            .focusProperties { canFocus = enabled },
        shape = CircleShape,
        containerColor = if (enabled) MaterialTheme.colorScheme.primary else ZasTheme.colors.disabled,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else ZasTheme.colors.onDisabled,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * タグ等の、押せない印。押せるチップ（AssistChip 等）は押せるように見えるため使わない。
 */
@Composable
fun ZasTag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .border(1.dp, ZasTheme.colors.border, ZasShape.chip)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * 画面に置く面（段階 1）。影を使わず、面の明暗と枠線で示す。
 */
@Composable
fun ZasCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = ZasShape.card,
        border = BorderStroke(1.dp, ZasTheme.colors.border),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 0.dp),
        content = content
    )
}
