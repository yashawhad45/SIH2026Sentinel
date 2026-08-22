package com.example.sentinel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sentinel.module1.DocumentViewModel
import com.example.sentinel.ui.screens.AnalysisScreen
import com.example.sentinel.ui.screens.DocumentCaptureScreen
import com.example.sentinel.ui.screens.HomeScreen
import com.example.sentinel.ui.screens.ReportScreen
import com.example.sentinel.ui.screens.SplashScreen
import com.example.sentinel.ui.screens.UpiCheckScreen

@Composable
fun SentinelNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val documentViewModel: DocumentViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = SentinelRoutes.SPLASH
    ) {
        composable(SentinelRoutes.SPLASH) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(SentinelRoutes.HOME) {
                        popUpTo(SentinelRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(SentinelRoutes.HOME) {
            HomeScreen(
                onModule1Click = {
                    navController.navigate(SentinelRoutes.DOCUMENT_CAPTURE)
                },
                onModule3Click = {
                    navController.navigate(SentinelRoutes.UPI_CHECK)
                }
            )
        }

        composable(SentinelRoutes.DOCUMENT_CAPTURE) {
            DocumentCaptureScreen(
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() },
                onAnalyse = {
                    navController.navigate(SentinelRoutes.ANALYSIS)
                }
            )
        }

        composable(SentinelRoutes.ANALYSIS) {
            AnalysisScreen(
                viewModel = documentViewModel,
                onAnalysisComplete = {
                    navController.navigate(SentinelRoutes.REPORT) {
                        popUpTo(SentinelRoutes.ANALYSIS) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SentinelRoutes.REPORT) {
            ReportScreen(
                viewModel = documentViewModel,
                onNewScan = {
                    documentViewModel.resetState()
                    navController.navigate(SentinelRoutes.DOCUMENT_CAPTURE) {
                        popUpTo(SentinelRoutes.HOME) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SentinelRoutes.UPI_CHECK) {
            UpiCheckScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
