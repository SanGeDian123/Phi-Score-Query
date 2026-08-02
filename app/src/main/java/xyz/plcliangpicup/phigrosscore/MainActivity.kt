package xyz.plcliangpicup.phigrosscore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.plcliangpicup.phigrosscore.data.AppRepository
import xyz.plcliangpicup.phigrosscore.ui.AppViewModel
import xyz.plcliangpicup.phigrosscore.ui.AppViewModelFactory
import xyz.plcliangpicup.phigrosscore.ui.PhigrosScoreApp
import xyz.plcliangpicup.phigrosscore.ui.PhigrosScoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AppRepository(applicationContext, BuildConfig.API_BASE_URL)
        setContent {
            val appViewModel: AppViewModel = viewModel(factory = AppViewModelFactory(repository))
            val state by appViewModel.state.collectAsState()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !state.isDarkTheme
                    isAppearanceLightNavigationBars = !state.isDarkTheme
                }
            }
            PhigrosScoreTheme(darkTheme = state.isDarkTheme) {
                PhigrosScoreApp(appViewModel)
            }
        }
    }
}
