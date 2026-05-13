package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unidagontor.retakid.ui.theme.GreenPrimary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person

sealed class BottomNav(val route: String, val title: String, val icon: ImageVector) {
    object Beranda : BottomNav("beranda", "Beranda", Icons.Default.Home)
    object Deteksi : BottomNav("deteksi", "Deteksi", Icons.Default.AddCircle)
    object Peta : BottomNav("peta", "Peta", Icons.Default.LocationOn)
    object Profil : BottomNav("profil", "Profil", Icons.Default.Person)
}

@Composable
fun RetakIdApp() {
    val navController = rememberNavController()

    // NavHost Utama mengatur perpindahan Splash -> Onboarding -> Login -> Main
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onSplashFinished = { navController.navigate("onboarding") { popUpTo("splash") { inclusive = true } } })
        }
        composable("onboarding") {
            OnboardingScreen(onFinishOnboarding = { navController.navigate("login") { popUpTo("onboarding") { inclusive = true } } })
        }
        composable("login") {
            LoginScreen(onLoginSuccess = { navController.navigate("main") { popUpTo("login") { inclusive = true } } })
        }
        composable("main") {
            MainContainerScreen(
                onNavigateToDetail = { laporanId -> navController.navigate("detail/$laporanId") }
            )
        }
        composable("detail/{laporanId}") { backStackEntry ->
            val laporanId = backStackEntry.arguments?.getString("laporanId") ?: return@composable
            DetailLaporanScreen(
                laporanId = laporanId,
                onBack = { navController.popBackStack() },
                onConfirm = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun MainContainerScreen(onNavigateToDetail: (String) -> Unit = {}) {
    val bottomNavController = rememberNavController()
    val items = listOf(BottomNav.Beranda, BottomNav.Deteksi, BottomNav.Peta, BottomNav.Profil)
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            bottomNavController.navigate(item.route) {
                                popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GreenPrimary,
                            indicatorColor = Color(0xFFE8F5E9) // Hijau sangat pudar
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // NavHost Khusus untuk 4 Tab di dalam MainContainer
        NavHost(
            navController = bottomNavController,
            startDestination = BottomNav.Beranda.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNav.Beranda.route) { BerandaTab(onLaporanClick = onNavigateToDetail) }
            composable(BottomNav.Deteksi.route) { DeteksiTab() }
            composable(BottomNav.Peta.route) { PetaTab() }
            composable(BottomNav.Profil.route) {
                ProfilTab(
                    onSignOut = {
                        navController.navigate("login") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    },
                    onRiwayatClick = { laporanId ->
                        onNavigateToDetail(laporanId)
                    }
                )
            }
        }
    }
}