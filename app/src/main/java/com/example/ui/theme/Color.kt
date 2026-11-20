package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// SpendWise Strict 3-Colour Brand System
// 1. Emerald — Growth, income, confirmed balances, primary actions
// 2. Blue    — Trust, clarity, bank accounts, internal transfers
// 3. Orange  — Spending, expenses, review attention, alerts
// Paired exclusively with clean neutral surfaces (light off-white / dark charcoal).
// ============================================================================

// Colour 1: Emerald
val EmeraldPrimary = Color(0xFF059669)
val EmeraldVibrant = Color(0xFF10B981)
val EmeraldDeep = Color(0xFF047857)
val EmeraldSoftLight = Color(0xFFD1FAE5)
val EmeraldTintLight = Color(0xFFECFDF5)
val EmeraldDarkContainer = Color(0xFF064E3B)

// Colour 2: Blue
val BlueSecondary = Color(0xFF2563EB)
val BlueVibrant = Color(0xFF3B82F6)
val BlueDeep = Color(0xFF1D4ED8)
val BlueSoftLight = Color(0xFFDBEAFE)
val BlueDarkContainer = Color(0xFF1E3A8A)

// Colour 3: Orange
val OrangeAccent = Color(0xFFEA580C)
val OrangeVibrant = Color(0xFFF97316)
val OrangeDeep = Color(0xFFC2410C)
val OrangeSoftLight = Color(0xFFFFEDD5)
val OrangeDarkContainer = Color(0xFF7C2D12)

// Semantic tokens mapped strictly to the 3 brand colours
val IncomeGreenLight = EmeraldPrimary
val IncomeGreenDark = Color(0xFF34D399)
val ExpenseOrangeLight = OrangeAccent
val ExpenseOrangeDark = Color(0xFFFB923C)
val TransferBlueLight = BlueSecondary
val TransferBlueDark = Color(0xFF60A5FA)
val AlertOrangeLight = OrangeDeep
val AlertOrangeDark = Color(0xFFFB923C)

// Light Theme Neutral Surfaces (Pure White default)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF4F6F8)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightOutline = Color(0xFFE2E8F0)
val LightOutlineSubtle = Color(0xFFF1F5F9)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF64748B)

// Dark Theme Neutral Surfaces
val DarkBackground = Color(0xFF0F1419)
val DarkSurface = Color(0xFF161D24)
val DarkSurfaceVariant = Color(0xFF1E2832)
val DarkSurfaceElevated = Color(0xFF222D38)
val DarkOutline = Color(0xFF2B3845)
val DarkOutlineSubtle = Color(0xFF202B36)
val DarkTextPrimary = Color(0xFFF1F5F9)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextMuted = Color(0xFF64748B)

// Chart palette strictly using tonal shades of Emerald, Blue, and Orange
val SpendWiseChartPalette = listOf(
    Color(0xFF059669), // Emerald Primary
    Color(0xFF2563EB), // Blue Primary
    Color(0xFFEA580C), // Orange Primary
    Color(0xFF10B981), // Emerald Light
    Color(0xFF3B82F6), // Blue Light
    Color(0xFFF97316), // Orange Light
    Color(0xFF047857), // Emerald Deep
    Color(0xFF1D4ED8), // Blue Deep
    Color(0xFFC2410C)  // Orange Deep
)
