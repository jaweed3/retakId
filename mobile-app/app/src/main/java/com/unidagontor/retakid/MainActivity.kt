package com.unidagontor.retakid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.unidagontor.retakid.ui.screens.RetakIdApp
import com.unidagontor.retakid.ui.theme.GreenPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar (jam, sinyal, baterai) mengikuti warna tema hijau
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                GreenPrimary.toArgb()
            )
        )

        setContent {
            RetakIdApp()
        }
    }
}
