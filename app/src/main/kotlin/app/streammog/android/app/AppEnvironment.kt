package app.streammog.android.app

import android.app.Activity
import android.content.Context
import app.streammog.android.coordinator.StreamingCoordinator
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.integrations.meta.MetaDATGlassesSessionClient
import app.streammog.android.integrations.meta.MockGlassesSessionClient
import app.streammog.android.integrations.streaming.MockStreamTransport
import app.streammog.android.shared.diagnostics.DiagnosticsStore
import app.streammog.android.shared.persistence.SessionHistoryStore
import app.streammog.android.shared.persistence.StreamPresetStore
import java.lang.ref.WeakReference

// Single holder for all application-scoped singletons (mirrors iOS AppEnvironment).
// Held on the Application class so it outlives any individual Activity.
class AppEnvironment private constructor(
    val coordinator: StreamingCoordinator,
    val diagnosticsStore: DiagnosticsStore,
    val glassesClient: GlassesSessionClient,
    val runtimeMode: AppRuntimeMode,
    val entitlements: AppEntitlements,
) {
    companion object {
        fun bootstrap(context: Context, activityRef: () -> Activity?): AppEnvironment {
            val runtimeMode = AppRuntimeMode.resolve()
            val entitlements = AppEntitlements.resolve()
            val diagnosticsStore = DiagnosticsStore(context)
            val presetStore = StreamPresetStore(context)
            val sessionHistoryStore = SessionHistoryStore(context)

            diagnosticsStore.log("Starting ${AppBrand.DISPLAY_NAME} in ${runtimeMode.displayName} mode")

            val glassesClient: GlassesSessionClient = when (runtimeMode) {
                AppRuntimeMode.MOCK -> MockGlassesSessionClient(diagnosticsStore)
                AppRuntimeMode.REAL -> MetaDATGlassesSessionClient(
                    diagnosticsStore = diagnosticsStore,
                    activityProvider = activityRef,
                )
            }

            // Phase 2 replaces MockStreamTransport with RtmpStreamTransport / RoutingStreamTransport
            val streamTransport = MockStreamTransport()

            val coordinator = StreamingCoordinator(
                glassesClient = glassesClient,
                streamTransport = streamTransport,
                diagnosticsStore = diagnosticsStore,
                presetStore = presetStore,
                sessionHistoryStore = sessionHistoryStore,
                entitlements = entitlements,
            )

            return AppEnvironment(
                coordinator = coordinator,
                diagnosticsStore = diagnosticsStore,
                glassesClient = glassesClient,
                runtimeMode = runtimeMode,
                entitlements = entitlements,
            )
        }
    }
}
