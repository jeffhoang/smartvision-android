package app.streammog.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.streammog.android.app.AppBrand
import app.streammog.android.app.AppRuntimeMode
import app.streammog.android.coordinator.StreamingCoordinator
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.shared.diagnostics.DiagnosticsStore
import app.streammog.android.ui.control.ControlScreen
import app.streammog.android.ui.diagnostics.DiagnosticsScreen
import app.streammog.android.ui.history.SessionHistoryScreen
import app.streammog.android.ui.settings.SettingsScreen
import app.streammog.android.ui.theme.AvaLensBackground
import app.streammog.android.ui.theme.AvaLensTeal
import kotlinx.coroutines.delay

private enum class AppTab(val label: String) {
    CONTROL("Control"),
    SETTINGS("Settings"),
    HISTORY("History"),
    LOGS("Logs"),
}

@Composable
fun RootScreen(
    coordinator: StreamingCoordinator,
    diagnosticsStore: DiagnosticsStore,
    glassesClient: GlassesSessionClient,
    runtimeMode: AppRuntimeMode,
    onLaunchUpgrade: () -> Unit,
    onManageSubscription: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val keepScreenOn by coordinator.keepScreenOn.collectAsState()
    LaunchedEffect(keepScreenOn) { onKeepScreenOn(keepScreenOn) }
    val entitlements by coordinator.entitlementsFlow.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = AppTab.entries

    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AppTab.CONTROL -> Icons.Outlined.Cast
                                        AppTab.SETTINGS -> Icons.Outlined.Settings
                                        AppTab.HISTORY -> Icons.Outlined.History
                                        AppTab.LOGS -> Icons.AutoMirrored.Outlined.List
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AvaLensTeal,
                                selectedTextColor = AvaLensTeal,
                                indicatorColor = AvaLensTeal.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (tabs[selectedTab]) {
                    AppTab.CONTROL -> ControlScreen(coordinator = coordinator)
                    AppTab.SETTINGS -> SettingsScreen(
                        coordinator = coordinator,
                        entitlements = entitlements,
                        onLaunchUpgrade = onLaunchUpgrade,
                        onManageSubscription = onManageSubscription,
                    )
                    AppTab.HISTORY -> SessionHistoryScreen(coordinator = coordinator)
                    AppTab.LOGS -> DiagnosticsScreen(
                        diagnosticsStore = diagnosticsStore,
                        glassesClient = glassesClient,
                        runtimeMode = runtimeMode,
                        entitlements = entitlements,
                        onLaunchUpgrade = onLaunchUpgrade,
                    )
                }
            }
        }

        // Splash overlay
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(tween(280)) + scaleOut(tween(280), targetScale = 0.98f),
            modifier = Modifier.zIndex(1f),
        ) {
            SplashScreen(
                runtimeMode = runtimeMode,
                onFinish = { showSplash = false },
            )
        }
    }
}

@Composable
private fun SplashScreen(runtimeMode: AppRuntimeMode, onFinish: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1_400),
        label = "splashProgress",
    )
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        progress = 1f
        delay(1_600)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A0D0E),
                        Color(0xFF141C1F),
                        Color(0xFF080B0C),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            val alpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(500),
                label = "splashAlpha",
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(alpha),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cast,
                    contentDescription = null,
                    tint = AvaLensTeal,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    AppBrand.DISPLAY_NAME,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    "Meta glasses streaming",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SplashChip(runtimeMode.displayName)
                    SplashChip("RTMP")
                    SplashChip("Recorder")
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp).alpha(alpha),
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .height(4.dp),
                    color = AvaLensTeal,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onFinish) {
                    Text("Skip", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SplashChip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.10f), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
