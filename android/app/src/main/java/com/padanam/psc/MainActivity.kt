package com.padanam.psc

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.PadanamRoot
import com.padanam.psc.ui.theme.PadanamTheme
import com.padanam.psc.util.CrashReporter
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val appVm: AppViewModel by viewModels {
        AppViewModel.factory((application as PadanamApp).openContainer())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        /* A crash from the previous run is more useful than the normal UI:
           show it before Compose or the database is touched, and stay in a
           plain Android view so a theme or composition bug cannot hide it. */
        val crashReport = CrashReporter.report(this)
        if (crashReport != null) {
            showCrashReport(crashReport)
            return
        }

        // Draw edge to edge with transparent system bars. The real system
        // status bar (clock, battery, notifications) stays visible, and the
        // app reserves its height in TopSafeArea so content never sits under
        // it. The navigation bar shows the screen behind it instead of drawing
        // an opaque strip.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // SystemBarStyle.auto enables Android's navigation-bar contrast scrim,
        // which paints a translucent light band behind the bar. The app draws
        // its own background edge to edge, so turn the scrim off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settings by appVm.settings.collectAsStateWithLifecycle()
            PadanamTheme(mode = settings.themeMode) {
                PadanamRoot(
                    container = (application as PadanamApp).openContainer(),
                    appVm = appVm
                )
            }
        }
    }

    private fun showCrashReport(report: String) {
        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        // This plain white screen is not composed inside PadanamTheme, so set
        // the system bar icons explicitly to stay readable on white.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
            setBackgroundColor(Color.WHITE)
        }

        content.addView(
            TextView(this).apply {
                text = "Padanam stopped last time"
                textSize = 23f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#12172B"))
            }
        )

        content.addView(
            TextView(this).apply {
                text = "The report below shows exactly what failed. Share or copy it " +
                    "and send it back so the crash can be fixed. It stays on this " +
                    "phone until you tap Try opening again."
                textSize = 14f
                setTextColor(Color.parseColor("#5D6478"))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(10), 0, dp(4))
            }
        )

        content.addView(
            TextView(this).apply {
                text = report
                textSize = 11.5f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#12172B"))
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(14), 0, dp(18))
            }
        )

        fun action(text: String, onClick: () -> Unit) {
            content.addView(
                Button(this).apply {
                    this.text = text
                    isAllCaps = false
                    setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(50)
                ).apply { topMargin = dp(8) }
            )
        }

        action("Share report") {
            CrashReporter.share(this, report)
        }
        action("Copy report") {
            CrashReporter.copy(this, report)
            Toast.makeText(this, "Crash report copied.", Toast.LENGTH_SHORT).show()
        }
        action("Try opening again") {
            CrashReporter.clear(this)
            recreate()
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(scroll)
    }
}
