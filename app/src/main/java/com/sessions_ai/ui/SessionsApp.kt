package com.sessions_ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun SessionsApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                state = uiState,
                onSendMessage = { viewModel.sendMessage(it) },
                onNavigateSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                currentModel = uiState.currentModel,
                downloadStates = uiState.downloadStates,
                onModelSelected = {
                    viewModel.selectModel(it)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
