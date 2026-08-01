package com.mil.ballistics.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mil.ballistics.app.ui.CalcViewModel
import com.mil.ballistics.app.ui.HistoryDetailScreen
import com.mil.ballistics.app.ui.HistoryScreen
import com.mil.ballistics.app.ui.InputScreen
import com.mil.ballistics.app.ui.ResultScreen
import com.mil.ballistics.app.ui.theme.BallisticsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BallisticsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    val calcViewModel: CalcViewModel = viewModel()
                    NavHost(navController = nav, startDestination = "input") {
                        composable("input") {
                            InputScreen(
                                onNavigateResult = { nav.navigate("result") },
                                onNavigateHistory = { nav.navigate("history") },
                                viewModel = calcViewModel
                            )
                        }
                        composable("result") {
                            ResultScreen(
                                onBack = { nav.popBackStack() },
                                viewModel = calcViewModel
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onBack = { nav.popBackStack() },
                                onOpenDetail = { id -> nav.navigate("history/$id") }
                            )
                        }
                        composable("history/{recordId}") { backStackEntry ->
                            val recordId = backStackEntry.arguments?.getString("recordId")?.toLongOrNull() ?: 0L
                            HistoryDetailScreen(
                                recordId = recordId,
                                onBack = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
