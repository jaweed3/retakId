package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingPage(val title: String, val description: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinishOnboarding: () -> Unit) {
    val pages = listOf(
        OnboardingPage("Sensor Pertama", "Laporkan retakan tanah di sekitar Anda untuk mencegah longsor berulang."),
        OnboardingPage("Pantau Real-time", "Dapatkan pembaruan langsung mengenai kondisi tanah di area rawan."),
        OnboardingPage("Selamatkan Nyawa", "Kontribusi kecil Anda dapat mencegah bencana besar.")
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { position ->
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Nanti Anda bisa tambah Image() di sini
                Text(pages[position].title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                Text(
                    text = pages[position].description,
                    modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) Color(0xFF4CAF50) else Color.LightGray
                    Box(
                        modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(10.dp)
                    )
                }
            }
            if (pagerState.currentPage == pages.size - 1) {
                Button(
                    onClick = onFinishOnboarding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Mulai", color = Color.White)
                }
            }
        }
    }
}