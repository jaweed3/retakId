package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.theme.GreenPrimary
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var errorMessage   by remember { mutableStateOf<String?>(null) }
    var isLoading      by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GreenPrimary,
        focusedLabelColor  = GreenPrimary,
        cursorColor        = GreenPrimary
    )

    Column(
        modifier              = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Text(
            text       = "Masuk ke Retak.id",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = GreenPrimary
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email") },
            colors        = textFieldColors,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value                   = password,
            onValueChange           = { password = it },
            label                   = { Text("Password") },
            visualTransformation    = PasswordVisualTransformation(),
            colors                  = textFieldColors,
            modifier                = Modifier.fillMaxWidth(),
            singleLine              = true
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    isLoading    = true
                    errorMessage = null
                    scope.launch {
                        try {
                            // Supabase Auth — sign in dengan email & password
                            SupabaseClient.client.auth.signInWith(Email) {
                                this.email    = email.trim()
                                this.password = password
                            }
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = petakanPesanError(e.message)
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled  = !isLoading,
            colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Login", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text     = "Belum punya akun? Daftar di sini",
            modifier = Modifier.clickable { onNavigateToRegister() }.padding(8.dp),
            color    = GreenPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Terjemahkan error Supabase ke pesan yang ramah pengguna. */
private fun petakanPesanError(raw: String?): String = when {
    raw == null                                        -> "Terjadi kesalahan, coba lagi."
    raw.contains("Invalid login credentials", true)   -> "Email atau password salah."
    raw.contains("Email not confirmed", true)          -> "Cek email kamu dan konfirmasi akun terlebih dahulu."
    raw.contains("network", true)                      -> "Tidak ada koneksi internet."
    else                                               -> raw
}