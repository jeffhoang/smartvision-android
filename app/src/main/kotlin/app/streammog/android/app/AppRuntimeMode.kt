package app.streammog.android.app

import android.os.Build

enum class AppRuntimeMode(val displayName: String) {
    MOCK("Mock"),
    REAL("Real Device");

    companion object {
        fun resolve(): AppRuntimeMode {
            System.getProperty("streammog.runtime")?.lowercase()?.let {
                when (it) {
                    "mock" -> return MOCK
                    "real" -> return REAL
                }
            }
            System.getenv("STREAMGLASSESAPP_RUNTIME")?.lowercase()?.let {
                when (it) {
                    "mock" -> return MOCK
                    "real" -> return REAL
                }
            }
            return if (isEmulator()) MOCK else REAL
        }

        private fun isEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK built for x86", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
                Build.PRODUCT.contains("emulator", ignoreCase = true) ||
                Build.PRODUCT.contains("simulator", ignoreCase = true)
    }
}
