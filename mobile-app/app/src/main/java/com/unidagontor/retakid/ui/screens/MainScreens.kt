package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt



@Composable
fun DeteksiTab() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", modifier = Modifier.size(80.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { /* Buka Kamera */ },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Text("Scan Retakan")
        }
    }
}



@Composable
fun ProfilTab() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Adam Toyib Nur Wahid", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Poin: 150 | Badge: Relawan")
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Riwayat Laporan") }
        TextButton(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Panduan Kearifan Lokal") }
    }
}