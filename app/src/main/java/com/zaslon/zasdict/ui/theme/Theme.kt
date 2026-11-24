package com.zaslon.zasdict.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// 値は ZasDesignSystem の design-system/tokens.md から写す。tokens.md は生成物なので、
// 値を変えるときは ZasDesignSystem 側を直して書き出し直し、ここへ写し直す。

/** E-inkモード（デザインシステムの高コントラストモード）が有効かどうかを各画面へ伝える */
val LocalEinkMode = staticCompositionLocalOf { false }

/** アプリの配色の選び方。ライト・ダークを端末の設定と別に切り替えられるようにする。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Material の ColorScheme に割り当てられないトークン */
@Immutable
data class ZasColors(
    val textSecondary: Color,
    val border: Color,
    val divider: Color,
    val accent: Color,
    val buttonText: Color,
    val buttonBorder: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val disabled: Color,
    val onDisabled: Color,
    val disabledText: Color,
    val destructive: Color,
    val onDestructive: Color,
    val errorText: Color,
    val selectedBackground: Color,
    /** 選択中の背景の上の文字。高コントラストモードでは背景を図の色で塗るため、文字を地の色に替える。 */
    val onSelectedBackground: Color,
    val inverseBackground: Color,
    val inverseText: Color,
    val inverseAction: Color,
    val statusbarText: Color,
    val statusbarHighlight: Color,
    val statusbarSuccess: Color,
    val statusbarWarning: Color,
    val statusbarError: Color,
    val notificationInfo: Color,
    val notificationSuccess: Color,
    val notificationWarning: Color,
    val notificationError: Color,
)

private object Palette {
    val white = Color(0xFFFFFFFF)
    val black = Color(0xFF000000)
    val gray50 = Color(0xFFF2F2F2)
    val gray200 = Color(0xFFCCCCCC)
    val gray300 = Color(0xFFB3B3B3)
    val gray500 = Color(0xFF7F7F7F)
    val gray536 = Color(0xFF767676)
    val gray600 = Color(0xFF666666)
    val gray700 = Color(0xFF4D4D4D)
    val gray900 = Color(0xFF1A1A1A)
    val key100 = Color(0xFFD8E3FF)
    val key300 = Color(0xFF9AB6FF)
    val key600 = Color(0xFF4061DB)
    val key700 = Color(0xFF2E49C1)
    val key900 = Color(0xFF1C2EA6)
    val key1000 = Color(0xFF141D94)
    val red100 = Color(0xFFFFDADA)
    val red300 = Color(0xFFFF9696)
    val red800 = Color(0xFFEC0000)
    val red900 = Color(0xFFCE0000)
    val red1100 = Color(0xFF850000)
    val green100 = Color(0xFFC2E5D1)
    val green300 = Color(0xFF71C598)
    val green900 = Color(0xFF115A36)
    val green1100 = Color(0xFF08351F)
    val yellow100 = Color(0xFFFFF0B3)
    val yellow300 = Color(0xFFFFD43D)
    val yellow1000 = Color(0xFF806300)
    val yellow1200 = Color(0xFF604B00)
    val cyan100 = Color(0xFFC8F8FF)
    val cyan300 = Color(0xFF79E2F2)
    val cyan900 = Color(0xFF006F83)
    val cyan1100 = Color(0xFF004C59)
    val bgLight0 = white
    val bgLight1 = gray50
    val bgDark0 = Color(0xFF181818)
    val bgDark1 = Color(0xFF212121)
    val bgDark2 = Color(0xFF2A2A2A)
    val bgDark3 = Color(0xFF333333)
}

// Material の色付きの面（トーンエレベーション）は、トークンに無い色を作るので使わない。
// surfaceTint を透明にして無効にする。
// surfaceContainerLow は段階 2（ドロワー・メニュー）、surfaceContainerHigh は段階 3（AlertDialog）に当たる。
private val LightColors = with(Palette) {
    lightColorScheme(
        primary = key900, onPrimary = white,
        primaryContainer = key100, onPrimaryContainer = black,
        secondary = key900, onSecondary = white,
        secondaryContainer = key100, onSecondaryContainer = black,
        tertiary = key700, onTertiary = white,
        tertiaryContainer = key100, onTertiaryContainer = black,
        background = bgLight0, onBackground = black,
        surface = bgLight1, onSurface = black,
        surfaceVariant = bgLight1, onSurfaceVariant = gray600,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = bgLight0,
        surfaceContainerLow = bgLight1,
        surfaceContainer = bgLight1,
        surfaceContainerHigh = bgLight1,
        surfaceContainerHighest = bgLight1,
        inverseSurface = gray900, inverseOnSurface = white, inversePrimary = key300,
        error = red900, onError = white,
        errorContainer = red100, onErrorContainer = black,
        outline = gray500, outlineVariant = gray200,
        scrim = black
    )
}

private val DarkColors = with(Palette) {
    darkColorScheme(
        primary = key600, onPrimary = white,
        primaryContainer = key1000, onPrimaryContainer = white,
        secondary = key600, onSecondary = white,
        secondaryContainer = key1000, onSecondaryContainer = white,
        tertiary = key300, onTertiary = black,
        tertiaryContainer = key1000, onTertiaryContainer = white,
        background = bgDark0, onBackground = white,
        surface = bgDark1, onSurface = white,
        surfaceVariant = bgDark1, onSurfaceVariant = gray300,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = bgDark0,
        surfaceContainerLow = bgDark2,
        surfaceContainer = bgDark2,
        surfaceContainerHigh = bgDark3,
        surfaceContainerHighest = bgDark3,
        inverseSurface = gray50, inverseOnSurface = black, inversePrimary = key700,
        error = red800, onError = white,
        errorContainer = red1100, onErrorContainer = white,
        outline = gray536, outlineVariant = gray700,
        scrim = black
    )
}

private val LightZasColors = with(Palette) {
    ZasColors(
        textSecondary = gray600, border = gray500, divider = gray200,
        accent = key700, buttonText = key900, buttonBorder = key900,
        inputBackground = white, inputBorder = gray536,
        disabled = gray300, onDisabled = gray50, disabledText = gray300,
        destructive = red900, onDestructive = white, errorText = red900,
        selectedBackground = key100, onSelectedBackground = black,
        inverseBackground = gray900, inverseText = white, inverseAction = key300,
        statusbarText = black,
        statusbarHighlight = cyan100, statusbarSuccess = green100,
        statusbarWarning = yellow100, statusbarError = red100,
        notificationInfo = cyan900, notificationSuccess = green900,
        notificationWarning = yellow1000, notificationError = red900,
    )
}

private val DarkZasColors = with(Palette) {
    ZasColors(
        textSecondary = gray300, border = gray536, divider = gray700,
        accent = key300, buttonText = key300, buttonBorder = key300,
        inputBackground = bgDark0, inputBorder = gray500,
        disabled = gray700, onDisabled = gray300, disabledText = gray600,
        destructive = red800, onDestructive = white, errorText = red300,
        selectedBackground = key1000, onSelectedBackground = white,
        inverseBackground = gray50, inverseText = black, inverseAction = key700,
        statusbarText = white,
        statusbarHighlight = cyan1100, statusbarSuccess = green1100,
        statusbarWarning = yellow1200, statusbarError = red1100,
        notificationInfo = cyan300, notificationSuccess = green300,
        notificationWarning = yellow300, notificationError = red300,
    )
}

/**
 * 高コントラストモード（E-ink 端末用）。地を白、図を黒とし、中間色を使わない。
 * 選択中・強調は図と地の反転で示す。無効表示だけは通常どおりグレーで示す。
 */
private val EinkColors = with(Palette) {
    lightColorScheme(
        primary = black, onPrimary = white,
        primaryContainer = black, onPrimaryContainer = white,
        secondary = black, onSecondary = white,
        secondaryContainer = black, onSecondaryContainer = white,
        tertiary = black, onTertiary = white,
        tertiaryContainer = white, onTertiaryContainer = black,
        background = white, onBackground = black,
        surface = white, onSurface = black,
        surfaceVariant = white, onSurfaceVariant = black,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = white, surfaceContainerLow = white,
        surfaceContainer = white, surfaceContainerHigh = white, surfaceContainerHighest = white,
        inverseSurface = black, inverseOnSurface = white, inversePrimary = white,
        error = black, onError = white,
        errorContainer = white, onErrorContainer = black,
        outline = black, outlineVariant = black,
        scrim = black
    )
}

private val EinkZasColors = with(Palette) {
    ZasColors(
        textSecondary = black, border = black, divider = black,
        accent = black, buttonText = black, buttonBorder = black,
        inputBackground = white, inputBorder = black,
        disabled = gray536, onDisabled = white, disabledText = gray536,
        destructive = black, onDestructive = white, errorText = black,
        selectedBackground = black, onSelectedBackground = white,
        inverseBackground = black, inverseText = white, inverseAction = white,
        // 下部アプリ通知はもともと反転面（黒地に白）。失敗の通知だけ地に戻して（白地に黒）区別し、他は強調しない。
        statusbarText = black,
        statusbarHighlight = black, statusbarSuccess = black,
        statusbarWarning = black, statusbarError = white,
        notificationInfo = white, notificationSuccess = white,
        notificationWarning = white, notificationError = black,
    )
}

val LocalZasColors = staticCompositionLocalOf { LightZasColors }

object ZasTheme {
    val colors: ZasColors
        @Composable get() = LocalZasColors.current
}

// 14 未満は使わない（tokens.md「文字スタイル」）。Material の既定は 11〜12 の段階を含むため、全段階を置き換える。
private fun style(size: Int, lineHeight: Float, letterSpacing: Float, bold: Boolean) = TextStyle(
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing.em,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
)

private val display = style(48, 1.4f, 0f, bold = false)
private val heading1 = style(28, 1.5f, 0.01f, bold = true)
private val heading2 = style(20, 1.5f, 0.02f, bold = true)
private val heading3 = style(18, 1.6f, 0.02f, bold = true)
private val body = style(16, 1.7f, 0.02f, bold = false)
private val small = style(14, 1.3f, 0f, bold = false)

private val ZasTypography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = heading1,
    headlineMedium = heading1,
    headlineSmall = heading2,
    titleLarge = heading2,
    titleMedium = heading3,
    titleSmall = style(16, 1.5f, 0.02f, bold = true),
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = small,
    // text-oneline（ボタン等）。行高 1.0 では日本語の上下が詰まり、欄の高さの計算にも余裕が無いため 1.3 とする。
    labelLarge = style(16, 1.3f, 0.02f, bold = false),
    labelMedium = small,
    labelSmall = small,
)

/** 角の丸みのトークン。Material の部品には既定の形をテーマの Shapes から取らないものがあるため、shape 引数でも渡す。 */
object ZasShape {
    val card = SquircleShape(12.dp)
    val control = SquircleShape(8.dp)
    val chip = SquircleShape(6.dp)
}

private val MaterialShapes = Shapes(
    extraSmall = ZasShape.control, // 入力欄、メニュー、スナックバー
    small = ZasShape.chip,
    medium = ZasShape.card,
    large = ZasShape.card,
    extraLarge = ZasShape.card     // ダイアログ
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZasDictTheme(
    einkMode: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 文字の倍率（ui-scale）。sp で指定した全ての文字に掛かる。 */
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme
    val zasColors: ZasColors
    when {
        einkMode -> { colorScheme = EinkColors; zasColors = EinkZasColors }
        isDarkTheme(einkMode, themeMode) -> { colorScheme = DarkColors; zasColors = DarkZasColors }
        else -> { colorScheme = LightColors; zasColors = LightZasColors }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalEinkMode provides einkMode,
        LocalZasColors provides zasColors,
        // OS の文字サイズの設定に、アプリの倍率を重ねる
        LocalDensity provides Density(density.density, density.fontScale * fontScale)
    ) {
        val themed: @Composable () -> Unit = {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = ZasTypography,
                shapes = MaterialShapes,
                content = content
            )
        }
        if (einkMode) {
            // 高コントラストモードではアニメーションを使わない。波紋と一緒に消えるフォーカス表示は einkFocusFrame で描く。
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Box(Modifier.fillMaxSize().einkFocusFrame(onlyWhenWindowFocused = true)) { themed() }
            }
        } else {
            themed()
        }
    }
}

@Composable
fun isDarkTheme(einkMode: Boolean, themeMode: ThemeMode): Boolean = when {
    einkMode -> false
    themeMode == ThemeMode.LIGHT -> false
    themeMode == ThemeMode.DARK -> true
    else -> isSystemInDarkTheme()
}
