package com.unidagontor.retakid.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.google.firebase.auth.FirebaseAuth

sealed class BottomNav(val route: String, val title: String, val icon: ImageVector) {
    object Beranda : BottomNav("beranda", "Beranda", Icons.Default.Home)
    object Deteksi : BottomNav("deteksi", "Deteksi", Icons.Default.AddCircle)
    object Peta : BottomNav("peta", "Peta", Icons.Default.LocationOn)
    object Profil : BottomNav("profil", "Profil", Icons.Default.Person)
}

@Composable
fun RetakIdApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("RetakIdPrefs", Context.MODE_PRIVATE)

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    val isOnboardingFinished = sharedPreferences.getBoolean("ONBOARDING_FINISHED", false)
                    val currentUser = FirebaseAuth.getInstance().currentUser

                    when {
                        !isOnboardingFinished -> {
                            navController.navigate("onboarding") { popUpTo("splash") { inclusive = true } }
                        }
                        currentUser != null -> {
                            navController.navigate("main") { popUpTo("splash") { inclusive = true } }
                        }
                        else -> {
                            navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                        }
                    }
                }
            )
        }

        composable("onboarding") {
            OnboardingScreen(
                onFinishOnboarding = {
                    sharedPreferences.edit().putBoolean("ONBOARDING_FINISHED", true).apply()
                    navController.navigate("login") { popUpTo("onboarding") { inclusive = true } }
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
                        popUpTo("login") { inclusive = true }
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
    }
}

@Composable
fun MainContainerScreen(onLogout: () -> Unit) {
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
                            indicatorColor = Color(0xFFE8F5E9)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = bottomNavController,
            startDestination = BottomNav.Beranda.route,
            modifier = Modifier.padding(innerPadding)
        ) {

            composable(BottomNav.Beranda.route) { BerandaTab() }
            composable(BottomNav.Deteksi.route) { DeteksiTab() }
            composable(BottomNav.Peta.route) { PetaTab() }


            composable(BottomNav.Profil.route) {
                ProfilScreen(onLogout = onLogout)
            }
        }
    }
}