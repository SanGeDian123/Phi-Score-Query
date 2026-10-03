package xyz.plcliangpicup.phigrosscore.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import xyz.plcliangpicup.phigrosscore.data.PracticeCharts
import java.io.File

class PracticePlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestFastestDisplayMode()
        val isDarkTheme = getSharedPreferences("app_preferences", MODE_PRIVATE).getBoolean("dark_theme", false)
        val pezFile = intent.getStringExtra(EXTRA_CHART_PATH)?.let(::File)
        val chartSource = intent.getStringExtra(EXTRA_CHART_SOURCE)?.let { PracticeCharts.decodeSource(it) }
            ?: pezFile?.let { file -> PracticeCharts.storedCharts(this).firstOrNull { it.fileName == file.name } }
        if (pezFile == null || !pezFile.isFile || chartSource == null) {
            finish()
            return
        }
        setContent {
            PhigrosScoreTheme(darkTheme = isDarkTheme) {
                PracticePlayerScreen(pezFile = pezFile, chartSource = chartSource, segmentMode = intent.getBooleanExtra(EXTRA_SEGMENT_MODE, false), onExit = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestFastestDisplayMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) requestFastestDisplayMode()
    }

    private fun requestFastestDisplayMode() {
        // Request the fastest mode at the current resolution; the system may
        // still reduce refresh rate for battery or thermal limits.
        val screen = windowManager.defaultDisplay
        val current = screen.mode
        val fastest = screen.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }.maxByOrNull { it.refreshRate }
        if (fastest != null) window.attributes = window.attributes.apply {
            preferredDisplayModeId = fastest.modeId
            preferredRefreshRate = fastest.refreshRate
        }
        if (android.os.Build.VERSION.SDK_INT >= 35 && fastest != null) {
            window.decorView.setRequestedFrameRate(fastest.refreshRate)
        }
    }

    companion object {
        const val EXTRA_CHART_SOURCE = "practice_chart_source"
        const val EXTRA_SEGMENT_MODE = "practice_segment_mode"
        const val EXTRA_CHART_PATH = "practice_chart_path"
    }
}
