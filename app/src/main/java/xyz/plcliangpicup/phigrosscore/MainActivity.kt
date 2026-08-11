package xyz.plcliangpicup.phigrosscore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import xyz.plcliangpicup.phigrosscore.data.AppRepository
import xyz.plcliangpicup.phigrosscore.data.SuggestionNotificationManager
import xyz.plcliangpicup.phigrosscore.ui.AppViewModel
import xyz.plcliangpicup.phigrosscore.ui.AppViewModelFactory
import xyz.plcliangpicup.phigrosscore.ui.PhigrosScoreApp
import xyz.plcliangpicup.phigrosscore.ui.PhigrosScoreTheme

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AppRepository(applicationContext, BuildConfig.API_BASE_URL)
        appViewModel = ViewModelProvider(this, AppViewModelFactory(repository))[AppViewModel::class.java]
        openSuggestionFromIntent(intent)
        setContent {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSuggestionFromIntent(intent)
    }

    private fun openSuggestionFromIntent(intent: Intent?) {
        intent?.getStringExtra(SuggestionNotificationManager.EXTRA_POST_ID)
            ?.takeIf(String::isNotBlank)
            ?.let(appViewModel::openSuggestionPost)
        intent?.removeExtra(SuggestionNotificationManager.EXTRA_POST_ID)
    }
}
