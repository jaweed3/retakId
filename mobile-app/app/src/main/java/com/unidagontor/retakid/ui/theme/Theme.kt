package com.unidagontor.retakid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color



val GreenPrimary     = Color(0xFF2E7D32)   // Hijau hutan utama
val GreenLight       = Color(0xFF4CAF50)   // Hijau terang
val GreenSurface     = Color(0xFFE8F5E9)   // Background hijau pudar

val StatusAman       = Color(0xFF388E3C)   // Hijau
val StatusWaspada    = Color(0xFFF57C00)   // Oranye
val StatusBahaya     = Color(0xFFD32F2F)   // Merah
val StatusBahayaBg   = Color(0xFFFFEBEE)   // Merah pudar (background card)
val StatusWaspadaBg  = Color(0xFFFFF3E0)   // Oranye pudar
val StatusAmanBg     = Color(0xFFE8F5E9)   // Hijau pudar


val TextPrimary      = Color(0xFF1B1B1B)
val TextSecondary    = Color(0xFF6D6D6D)
val Surface          = Color(0xFFF5F5F5)
val CardBg           = Color(0xFFFFFFFF)
val Divider          = Color(0xFFE0E0E0)


private val LightColorScheme = lightColorScheme(
    primary          = GreenPrimary,
    onPrimary        = Color.White,
    primaryContainer = GreenSurface,
    secondary        = GreenLight,
    background       = Surface,
    surface          = CardBg,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
)

@Composable
fun RetakIdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}