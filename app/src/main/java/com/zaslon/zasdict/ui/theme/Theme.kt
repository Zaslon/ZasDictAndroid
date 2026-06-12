package com.zaslon.zasdict.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** E-inkモードが有効かどうかを各画面へ伝える CompositionLocal */
val LocalEinkMode = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BA9),
    secondary = Color(0xFF5A6B8C),
    tertiary = Color(0xFF7D5260)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    secondary = Color(0xFFBFC8DC),
    tertiary = Color(0xFFEFB8C8)
)

/**
 * E-ink端末用の白黒高コントラストカラースキーム。
 * 中間色（グレー・トーン）を避け、白地に黒、選択状態は黒地に白で表現する。
 */
private val EinkColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.Black,        // FAB など → 黒地に白アイコン
    onPrimaryContainer = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.Black,      // 選択中の FilterChip → 黒地に白文字
    onSecondaryContainer = Color.White,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color.White,
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    surfaceTint = Color.White,             // トーンエレベーションによる色付きを無効化
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    inverseSurface = Color.Black,          // Snackbar → 黒地に白文字
    inverseOnSurface = Color.White,
    inversePrimary = Color.White,
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color.White,
    onErrorContainer = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,          // Divider もくっきり黒に
    scrim = Color.Black
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZasDictTheme(
    einkMode: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    if (einkMode) {
        // E-inkモード: 白黒固定 + リップルアニメーション無効
        CompositionLocalProvider(
            LocalEinkMode provides true,
            LocalRippleConfiguration provides null
        ) {
            MaterialTheme(colorScheme = EinkColors, content = content)
        }
    } else {
        val colorScheme = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }
        CompositionLocalProvider(LocalEinkMode provides false) {
            MaterialTheme(colorScheme = colorScheme, content = content)
        }
    }
}
