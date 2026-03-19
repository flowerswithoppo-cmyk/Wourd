package com.wourd.app.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.wourd.app.ui.screens.CameraCaptureScreen
import com.wourd.app.ui.screens.HomeScreen
import com.wourd.app.ui.screens.OnboardingScreen
import com.wourd.app.ui.screens.SettingsScreen
import com.wourd.app.ui.util.CaptureBus

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WourdNavHost(hasApiKey: Boolean) {
    val navController = rememberAnimatedNavController()
    val start = if (hasApiKey) Routes.Home else Routes.Onboarding

    AnimatedNavHost(
        navController = navController,
        startDestination = start,
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenCamera = { navController.navigate(Routes.Camera) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Camera) {
            CameraCaptureScreen(
                onBack = { navController.popBackStack() },
                onCaptured = { uri ->
                    CaptureBus.lastCapturedUri.value = uri
                    navController.popBackStack()
                },
            )
        }
    }
}

