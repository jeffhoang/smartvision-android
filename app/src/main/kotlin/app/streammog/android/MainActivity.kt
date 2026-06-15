package app.streammog.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as StreamMogApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.setCurrentActivity(this)
        enableEdgeToEdge()

        // Trigger environment bootstrap on first launch
        val env = app.environment

        // Handle deep link if app was cold-started via Meta AI callback
        intent?.data?.let { uri ->
            lifecycleScope.launch {
                env.glassesClient.handleDeepLink(uri)
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Phase 4: replace with RootView (Scaffold + NavigationBar)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("StreamMog — Phase 1 skeleton (${env.runtimeMode.displayName} mode)")
                    }
                }
            }
        }
    }

    // Called when Meta AI deep-links back while the app is already running (singleTask)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            lifecycleScope.launch {
                app.environment.glassesClient.handleDeepLink(uri)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.setCurrentActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (app.currentActivity === this) {
            app.setCurrentActivity(null)
        }
    }
}
