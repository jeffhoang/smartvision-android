package app.streammog.android.app

import app.streammog.android.domain.model.StreamPreset

data class AppEntitlements(
    val tier: Tier,
    val canSaveMultipleDestinations: Boolean,
    val canUseLocalRecording: Boolean,
    val canUseCreatorDefaults: Boolean,
    val canExportDiagnostics: Boolean,
    val canImportFromQR: Boolean,
    val maxSessionHistoryEntries: Int,
    val maxSavedDestinations: Int,
    val canUsePremiumProtocols: Boolean,
    val maxStreamDurationSeconds: Int?,
) {
    enum class Tier(val displayName: String) {
        FREE("Freemium"),
        CREATOR("Creator"),
    }

    fun allows(streamingService: StreamPreset.StreamingService): Boolean =
        canUsePremiumProtocols || streamingService == StreamPreset.StreamingService.YOUTUBE

    companion object {
        val free = AppEntitlements(
            tier = Tier.FREE,
            canSaveMultipleDestinations = false,
            canUseLocalRecording = true,
            canUseCreatorDefaults = false,
            canExportDiagnostics = false,
            canImportFromQR = false,
            maxSessionHistoryEntries = 3,
            maxSavedDestinations = 1,
            canUsePremiumProtocols = false,
            maxStreamDurationSeconds = 240,
        )

        val creator = AppEntitlements(
            tier = Tier.CREATOR,
            canSaveMultipleDestinations = true,
            canUseLocalRecording = true,
            canUseCreatorDefaults = true,
            canExportDiagnostics = true,
            canImportFromQR = true,
            maxSessionHistoryEntries = 50,
            maxSavedDestinations = 50,
            canUsePremiumProtocols = true,
            maxStreamDurationSeconds = null,
        )

        fun resolve(): AppEntitlements {
            return when (System.getenv("STREAMGLASSESAPP_TIER")?.lowercase()) {
                "free" -> free
                "creator" -> creator
                else -> free // Phase 5: query Google Play Billing
            }
        }
    }
}
