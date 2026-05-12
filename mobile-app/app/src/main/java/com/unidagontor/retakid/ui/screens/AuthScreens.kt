package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.unidagontor.retakid.ui.theme.GreenPrimary // Sesuaikan package Anda

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // Tampil 2 detik lalu pindah
        onSplashFinished()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(GreenPrimary),
        contentAlignment = Alignment.Center
    ) {
        Text("Retak.id", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OnboardingScreen(onFinishOnboarding: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sensor Pertama", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
        Text(
            text = "Laporkan retakan tanah di sekitar Anda untuk mencegah longsor berulang.",
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            textAlign = TextAlign.Center
        )
        Button(onClick = onFinishOnboarding, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
            Text("Mulai", color = Color.White)
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Masuk ke Retak.id", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onLoginSuccess, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Lanjutkan dengan Google", color = Color.Black)
        }
    }
}