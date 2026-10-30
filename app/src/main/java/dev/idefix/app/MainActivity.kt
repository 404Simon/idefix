package dev.idefix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.idefix.app.ui.AppViewModel
import dev.idefix.app.ui.IdefixApp
import dev.idefix.app.ui.idefixViewModelFactory
import dev.idefix.app.ui.theme.IdefixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as IdefixApplication).container
        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = idefixViewModelFactory { AppViewModel(container) },
            )
            val appState by appViewModel.uiState.collectAsStateWithLifecycle()
            IdefixTheme(
                themeMode = appState.settings.themeMode,
                dynamicColor = appState.settings.dynamicColor,
            ) {
                IdefixApp(
                    appState = appState,
                    appViewModel = appViewModel,
                    container = container,
                )
            }
        }
    }
}
