package com.unidagontor.retakid.ui.screens

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.data.notification.NotifStore
import com.unidagontor.retakid.ui.theme.GreenPrimary
import com.unidagontor.retakid.ui.theme.StatusBahaya
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import io.github.jan.supabase.auth.auth


sealed class BottomNav(val route: String, val title: String, val icon: ImageVector) {
    object Beranda : BottomNav("beranda", "Beranda", Icons.Default.Home)
    object Deteksi : BottomNav("deteksi", "Deteksi", Icons.Default.AddCircle)
    object Peta    : BottomNav("peta",    "Peta",    Icons.Default.LocationOn)
    object Profil  : BottomNav("profil",  "Profil",  Icons.Default.Person)
}

@Composable
fun RetakIdApp() {
    val navController     = rememberNavController()
    val context           = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("RetakIdPrefs", Context.MODE_PRIVATE)

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    val isOnboardingFinished = sharedPreferences.getBoolean("ONBOARDING_FINISHED", false)

                    // Ganti FirebaseAuth.getInstance().currentUser
                    // dengan Supabase currentSessionOrNull() — synchronous, baca dari cache memory
                    val hasSession = SupabaseClient.client.auth.currentSessionOrNull() != null

                    when {
                        !isOnboardingFinished -> {
                            navController.navigate("onboarding") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                        hasSession -> {
                            navController.navigate("main") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                        else -> {
                            navController.navigate("login") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable("onboarding") {
            OnboardingScreen(
                onFinishOnboarding = {
                    sharedPreferences.edit().putBoolean("ONBOARDING_FINISHED", true).apply()
                    navController.navigate("login") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main") { popUpTo("login") { inclusive = true } }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("main") {
                        popUpTo("login")    { inclusive = true }
                        popUpTo("register") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable("main") {
            MainContainerScreen(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("notifications") {
            NotificationScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun MainContainerScreen(onLogout: () -> Unit) {
    val bottomNavController = rememberNavController()
    val items               = listOf(BottomNav.Beranda, BottomNav.Deteksi, BottomNav.Peta, BottomNav.Profil)
    val navBackStackEntry   by bottomNavController.currentBackStackEntryAsState()
    val currentRoute        = navBackStackEntry?.destination?.route
    val context             = LocalContext.current
    val lifecycleOwner      = LocalLifecycleOwner.current

    // ── Notifikasi icon route ─────────────────────────────────────────────────
    val notifNavController = rememberNavController()

    // ── GPS enforcement ──────────────────────────────────────────────────────
    var isGpsEnabled by remember {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mutableStateOf(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    // Re-check tiap kali app kembali ke foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isGpsEnabled) {
        GpsRequiredDialog {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    // ── Popup bahaya dekat saat app dibuka ───────────────────────────────────
    var showDangerDialog by remember { mutableStateOf(false) }
    val dangerNotifs = remember { NotifStore.getAll(context) }
    LaunchedEffect(Unit) {
        if (dangerNotifs.isNotEmpty()) showDangerDialog = true
    }
    if (showDangerDialog) {
        DangerNearbyDialog(
            count     = dangerNotifs.size,
            onDismiss = { showDangerDialog = false }
        )
    }
    // ─────────────────────────────────────────────────────────────────────────

    Scaffold(
        bottomBar = {
            // Sembunyikan bottom nav di semua sub-screen (bukan tab utama)
            val subScreens = setOf("notifikasi", "riwayat", "panduan", "pengaturan")
            if (currentRoute !in subScreens) {
                NavigationBar(containerColor = Color.White) {
                    items.forEach { item ->
                        NavigationBarItem(
                            icon     = { Icon(item.icon, contentDescription = item.title) },
                            label    = { Text(item.title) },
                            selected = currentRoute == item.route,
                            onClick  = {
                                bottomNavController.navigate(item.route) {
                                    popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                indicatorColor    = Color(0xFFE8F5E9)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = bottomNavController,
            startDestination = BottomNav.Beranda.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(BottomNav.Beranda.route) {
                BerandaTab(
                    onNotifClick = {
                        bottomNavController.navigate("notifikasi") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("notifikasi") {
                NotificationScreen(onBack = { bottomNavController.popBackStack() })
            }
            composable("riwayat") {
                RiwayatLaporanScreen(onBack = { bottomNavController.popBackStack() })
            }
            composable("panduan") {
                PanduanKearifanLokalScreen(onBack = { bottomNavController.popBackStack() })
            }
            composable("pengaturan") {
                PengaturanScreen(onBack = { bottomNavController.popBackStack() })
            }
            composable(BottomNav.Deteksi.route) { DeteksiTab() }
            composable(BottomNav.Peta.route)    { PetaTab() }
            composable(BottomNav.Profil.route) {
                ProfilScreen(
                    onLogout        = onLogout,
                    onRiwayat       = { bottomNavController.navigate("riwayat") { launchSingleTop = true } },
                    onPanduan       = { bottomNavController.navigate("panduan") { launchSingleTop = true } },
                    onPengaturan    = { bottomNavController.navigate("pengaturan") { launchSingleTop = true } }
                )
            }
        }
    }
}

// ── Dialog GPS wajib aktif ────────────────────────────────────────────────────
@Composable
fun GpsRequiredDialog(onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* tidak bisa ditutup */ },
        icon             = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary) },
        title            = { Text("GPS Diperlukan", style = MaterialTheme.typography.titleMedium) },
        text             = {
            Text(
                "Retak.id membutuhkan GPS aktif untuk mendeteksi lokasi retakan secara akurat. " +
                "Silakan aktifkan GPS terlebih dahulu."
            )
        },
        confirmButton    = {
            Button(
                onClick = onOpenSettings,
                colors  = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Aktifkan GPS")
            }
        },
        dismissButton    = null   // tidak ada tombol batal
    )
}

// ── Dialog peringatan bahaya dekat saat app dibuka ────────────────────────────
@Composable
fun DangerNearbyDialog(count: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = StatusBahaya
            )
        },
        title = {
            Text(
                "⚠️ Peringatan Bahaya!",
                style = MaterialTheme.typography.titleMedium,
                color = StatusBahaya
            )
        },
        text = {
            Text(
                "Terdeteksi $count laporan BAHAYA dalam radius 100 meter dari lokasi Anda.\n\n" +
                "Berhati-hatilah dan segera cari tempat yang lebih aman!"
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = StatusBahaya)
            ) { Text("Mengerti", color = androidx.compose.ui.graphics.Color.White) }
        },
        dismissButton = null
    )
}
