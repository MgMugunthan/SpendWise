package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemePreference

@Immutable
data class SpendWiseExtendedColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val cardBorder: Color,
    val mutedText: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color
)

val LightExtendedColors = SpendWiseExtendedColors(
    income = IncomeGreenLight,
    incomeContainer = EmeraldSoftLight,
    expense = ExpenseOrangeLight,
    expenseContainer = OrangeSoftLight,
    transfer = TransferBlueLight,
    transferContainer = BlueSoftLight,
    attention = OrangeAccent,
    attentionContainer = OrangeSoftLight,
    cardBorder = LightOutline,
    mutedText = LightTextMuted,
    heroStart = EmeraldDeep,
    heroMid = EmeraldPrimary,
    heroEnd = BlueDeep
)

val DarkExtendedColors = SpendWiseExtendedColors(
    income = IncomeGreenDark,
    incomeContainer = EmeraldDarkContainer,
    expense = ExpenseOrangeDark,
    expenseContainer = OrangeDarkContainer,
    transfer = TransferBlueDark,
    transferContainer = BlueDarkContainer,
    attention = OrangeVibrant,
    attentionContainer = OrangeDarkContainer,
    cardBorder = DarkOutline,
    mutedText = DarkTextMuted,
    heroStart = EmeraldDarkContainer,
    heroMid = EmeraldDeep,
    heroEnd = BlueDarkContainer
)

val LocalSpendWiseColors = staticCompositionLocalOf { LightExtendedColors }

private val SpendWiseLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoftLight,
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlueSoftLight,
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = OrangeAccent,
    onTertiary = Color.White,
    tertiaryContainer = OrangeSoftLight,
    onTertiaryContainer = Color(0xFF7C2D12),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
    error = AlertOrangeLight,
    onError = Color.White,
    errorContainer = OrangeSoftLight,
    onErrorContainer = Color(0xFF7C2D12)
)

private val SpendWiseDarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldSoftLight,
    secondary = BlueVibrant,
    onSecondary = Color.White,
    secondaryContainer = BlueDarkContainer,
    onSecondaryContainer = BlueSoftLight,
    tertiary = OrangeVibrant,
    onTertiary = Color(0xFF431407),
    tertiaryContainer = OrangeDarkContainer,
    onTertiaryContainer = OrangeSoftLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineSubtle,
    error = AlertOrangeDark,
    onError = Color(0xFF431407),
    errorContainer = OrangeDarkContainer,
    onErrorContainer = OrangeSoftLight
)

val SpendWiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object SpendWiseSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
fun SpendWiseTheme(
    themePreference: ThemePreference = ThemePreference.LIGHT,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) SpendWiseDarkColorScheme else SpendWiseLightColorScheme
    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalSpendWiseColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpendWiseTypography,
            shapes = SpendWiseShapes,
            content = content
        )
    }
}
