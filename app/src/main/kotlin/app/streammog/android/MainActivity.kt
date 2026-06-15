package app.streammog.android

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.streammog.android.ui.RootScreen
import app.streammog.android.ui.theme.StreamMogTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as StreamMogApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.setCurrentActivity(this)
        enableEdgeToEdge()

        val env = app.environment

        intent?.data?.let { uri ->
            lifecycleScope.launch { env.glassesClient.handleDeepLink(uri) }
        }

        setContent {
            StreamMogTheme {
                RootScreen(
                    coordinator = env.coordinator,
                    diagnosticsStore = env.diagnosticsStore,
                    glassesClient = env.glassesClient,
                    runtimeMode = env.runtimeMode,
                    entitlements = env.entitlements,
                    onKeepScreenOn = { keepOn ->
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            lifecycleScope.launch { app.environment.glassesClient.handleDeepLink(uri) }
        }
    }

    override fun onResume() {
        super.onResume()
        app.setCurrentActivity(this)
        app.environment.coordinator.handleAppForeground()
    }

    override fun onStop() {
        super.onStop()
        app.environment.coordinator.handleAppBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (app.currentActivity === this) app.setCurrentActivity(null)
    }
}
