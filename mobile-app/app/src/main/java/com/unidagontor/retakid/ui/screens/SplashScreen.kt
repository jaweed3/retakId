package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // Tampil 2 detik
        onSplashFinished() // Memanggil logika pengecekan di AppNavigation
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF4CAF50)), // Ganti dengan GreenPrimary jika error
        contentAlignment = Alignment.Center
    ) {
        Text("Retak.id", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    }
}