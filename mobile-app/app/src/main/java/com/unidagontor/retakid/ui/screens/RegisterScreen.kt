package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.theme.GreenPrimary
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var namaLengkap    by remember { mutableStateOf("") }
    var noTelepon      by remember { mutableStateOf("") }
    var alamat         by remember { mutableStateOf("") }
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var konfirmasi     by remember { mutableStateOf("") }
    var errorMessage   by remember { mutableStateOf<String?>(null) }
    var isLoading      by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GreenPrimary,
        focusedLabelColor  = GreenPrimary,
        cursorColor        = GreenPrimary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text       = "Daftar Akun",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = GreenPrimary
        )
        Text(
            text     = "Isi data lengkap untuk verifikasi pelaporan",
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Data Diri ──────────────────────────────────────────
        SectionLabel("Data Diri")

        OutlinedTextField(
            value         = namaLengkap,
            onValueChange = { namaLengkap = it },
            label         = { Text("Nama Lengkap *") },
            colors        = textFieldColors,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value          = noTelepon,
            onValueChange  = { noTelepon = it },
            label          = { Text("No. Telepon (opsional)") },
            colors         = textFieldColors,
            modifier       = Modifier.fillMaxWidth(),
            singleLine     = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value         = alamat,
            onValueChange = { alamat = it },
            label         = { Text("Alamat / Dusun (opsional)") },
            colors        = textFieldColors,
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 2,
            maxLines      = 3
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Akun ──────────────────────────────────────────────
        SectionLabel("Akun")

        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email *") },
            colors        = textFieldColors,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value                   = password,
            onValueChange           = { password = it },
            label                   = { Text("Password (min. 6 karakter) *") },
            visualTransformation    = PasswordVisualTransformation(),
            colors                  = textFieldColors,
            modifier                = Modifier.fillMaxWidth(),
            singleLine              = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value                   = konfirmasi,
            onValueChange           = { konfirmasi = it },
            label                   = { Text("Konfirmasi Password *") },
            visualTransformation    = PasswordVisualTransformation(),
            colors                  = textFieldColors,
            modifier                = Modifier.fillMaxWidth(),
            isError                 = konfirmasi.isNotEmpty() && konfirmasi != password,
            supportingText          = if (konfirmasi.isNotEmpty() && konfirmasi != password) {
                { Text("Password tidak cocok") }
            } else null,
            singleLine              = true
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                errorMessage = validasi(namaLengkap, email, password, konfirmasi)
                if (errorMessage != null) return@Button

                isLoading = true
                scope.launch {
                    try {
                        // Kirim nama_lengkap sebagai user metadata → akan dibaca trigger SQL
                        SupabaseClient.client.auth.signUpWith(Email) {
                            this.email    = email.trim()
                            this.password = password
                            data = buildJsonObject {
                                put("nama_lengkap", namaLengkap.trim())
                            }
                        }

                        // Update no_telepon & alamat ke tabel profiles (jika diisi)
                        if (noTelepon.isNotEmpty() || alamat.isNotEmpty()) {
                            val uid = SupabaseClient.client.auth.currentUserOrNull()?.id
                            if (uid != null) {
                                SupabaseClient.client
                                    .from("profiles")
                                    .update(mapOf(
                                        "no_telepon" to noTelepon.trim().ifEmpty { null },
                                        "alamat"     to alamat.trim().ifEmpty { null }
                                    )) {
                                        filter { eq("id", uid) }
                                    }
                            }
                        }

                        onRegisterSuccess()
                    } catch (e: Exception) {
                        errorMessage = petakanPesanError(e.message)
                    } finally {
                        isLoading = false
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
                Text("Daftar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = "Sudah punya akun? Masuk di sini",
            modifier   = Modifier.clickable { onNavigateToLogin() }.padding(8.dp),
            color      = GreenPrimary,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text       = label,
        fontSize   = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color      = GreenPrimary,
        modifier   = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

private fun validasi(nama: String, email: String, password: String, konfirmasi: String): String? = when {
    nama.isBlank()              -> "Nama lengkap wajib diisi."
    email.isBlank()             -> "Email wajib diisi."
    !email.contains("@")        -> "Format email tidak valid."
    password.length < 6         -> "Password minimal 6 karakter."
    password != konfirmasi      -> "Password dan konfirmasi tidak cocok."
    else                        -> null
}

private fun petakanPesanError(raw: String?): String = when {
    raw == null                                         -> "Terjadi kesalahan, coba lagi."
    raw.contains("already registered", true)            -> "Email sudah terdaftar, silakan masuk."
    raw.contains("network", true)                       -> "Tidak ada koneksi internet."
    else                                                -> raw
}