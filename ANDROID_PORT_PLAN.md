# Android Port Plan

## SDK Comparison: iOS vs Android MWDAT 0.7.0

Both SDKs are **version 0.7.0, released the same day (2026-05-14)**. Feature parity confirmed.
Every API called in `MetaDATGlassesSessionClient.swift` has a direct Android equivalent.
Differences are platform idioms only.

| Concept | iOS (Swift) | Android (Kotlin) |
|---|---|---|
| SDK init | `Wearables.configure()` in code | `<meta-data>` in `AndroidManifest.xml` |
| Singleton | `Wearables.shared` | `Wearables` object |
| Registration state | `.registered` / `.registering` | `REGISTERED` / `REGISTERING` |
| Registration stream | `registrationStateStream()` → `AsyncStream` | `registrationState` → `StateFlow` |
| Devices list | `wearables.devices` | same |
| Device lookup | `deviceForIdentifier(id)` | same |
| Start registration | `wearables.startRegistration()` | same |
| Session creation | `Wearables.createSession(deviceSelector:)` | `Wearables.createSession(deviceSelector)` |
| Session state | `DeviceSessionState` (`.started`, `.stopped`) | `DeviceSessionState` (`STARTED`, `STOPPED`) |
| Session errors | `DeviceSessionError` enum | `DeviceSessionError` enum (SCREAMING_SNAKE_CASE) |
| Add camera stream | `session.addStream(config:)` | `session.addStream(config)` |
| Stream state/errors | `statePublisher` / `errorPublisher` (Combine) | `state` / `errors` (Flow / SharedFlow) |
| Video frames | `videoFramePublisher` → `VideoFrame` → `.sampleBuffer` (CMSampleBuffer) | Flow → `VideoFrame` → `.buffer` (ByteBuffer) |
| Camera permission | `wearables.checkPermissionStatus(.camera)` | `PermissionsSession` (same concept) |
| Open firmware update | `Wearables.shared.openFirmwareUpdate()` | `Wearables.openFirmwareUpdate(activity)` |
| Open DAT update | `Wearables.shared.openDATGlassesAppUpdate()` | `Wearables.openDATGlassesAppUpdate(activity)` |
| Deep link / URL callback | `Wearables.shared.handleUrl(url)` | Android Intent / deep link handler |
| Mock device | `MockDeviceKit` | `MockDeviceKit` (same) |

**Verdict:** Porting `MetaDATGlassesSessionClient.swift` to Kotlin is mechanical — the registration flow,
session lifecycle, error taxonomy, and video frame pipeline map 1-to-1.

---

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Navigation Compose
- **Async:** Kotlin Coroutines + Flow (replaces Combine / AsyncStream)
- **RTMP/SRT:** [RootEncoder](https://github.com/pedroSG94/RootEncoder) (replaces HaishinKit)
- **Video pipeline:** `MediaCodec` + `ByteBuffer` (replaces `CMSampleBuffer` / `CIColorControls`)
- **Storage:** Jetpack DataStore (replaces `UserDefaults`)
- **Database:** Room (replaces JSON file store for session history)
- **Billing:** Google Play Billing Library 7 (replaces StoreKit)
- **Biometrics:** `BiometricPrompt` (replaces `LocalAuthentication` / Face ID)

---

## Phases

### Phase 0 — Prerequisites (1–2 days)
- Register app on [wearables.developer.meta.com](https://wearables.developer.meta.com) to get an Android App ID
- Obtain `GITHUB_TOKEN` with `read:packages` scope for MWDAT Maven access
- Confirm RootEncoder as the RTMP library (evaluate license + feature fit)

### Phase 1 — Android project skeleton + Meta DAT (2–3 days)
- New Android Studio project: Kotlin + Jetpack Compose, `minSdk 26` (Android 8)
- Gradle: MWDAT Maven repo + `mwdat-core`, `mwdat-camera`, `mwdat-mockdevice` at `0.7.0`
- `AndroidManifest.xml`: App ID meta-data, deep link intent filter (replaces iOS URL scheme)
- `AppRuntimeMode` enum (mock vs real)
- `GlassesSessionClient` → Kotlin interface (direct translation of Swift protocol)
- `MetaDATGlassesSessionClient` → Kotlin class (Coroutines/Flow replacing Combine/AsyncStream)
- `MockGlassesSessionClient` → Kotlin class

### Phase 2 — RTMP streaming stack (3–5 days)
- `StreamingTransport` → Kotlin interface
- `RtmpStreamTransport` → Kotlin using RootEncoder `GenericStream`
- `VideoSampleBufferTransformer` → `MediaCodec` + `ByteBuffer` (brightness/contrast via `ColorMatrixColorFilter`)
- `MicrophoneAudioCapture` → `AudioRecord`
- `LocalRecordingTransport` → `MediaMuxer` + `MediaCodec` (replaces `AVAssetWriter`)
- `SRTStreamTransport` → RootEncoder SRT support

### Phase 3 — Streaming coordinator + domain (2–3 days)
- `StreamingCoordinator` → Kotlin `ViewModel` with `StateFlow` (replaces `@Published` / `ObservableObject`)
- Domain models (`StreamPreset`, `StreamHealth`, `DeviceStatus`, etc.) → Kotlin data classes
- `SessionHistoryStore` → Room database
- `StreamPresetStore` → `DataStore<Preferences>`

### Phase 4 — UI (1–2 weeks)
- `RootView` → `Scaffold` + `NavigationBar` (bottom tabs)
- `ControlDashboardView` → Compose with `AndroidView` wrapping `SurfaceView` for preview
- `SettingsView` → `HorizontalPager` + `PrimaryScrollableTabRow` (Stream / Quality / App tabs)
- `SessionHistoryView`, `DiagnosticsView` → `LazyColumn` lists
- Brightness/contrast sliders → same UX, `ColorMatrixColorFilter` in render path
- `PrivacyPolicyView` → Compose `WebView`

### Phase 5 — Billing + polish (3–5 days)
- `AppStorePurchaseManager` → Google Play Billing Library 7 (`BillingClient`)
- `AppEntitlements` → same tier model (free / creator)
- `LocalAuthentication` → `BiometricPrompt`
- App icon, splash, Material 3 theming

### Phase 6 — Testing + release (3–5 days)
- Port `StreamingCoordinatorTests` → JUnit + `kotlinx-coroutines-test`
- MockDevice integration tests (MWDAT provides `MockDeviceKit` on Android)
- Internal test track → production release

---

## Estimated Timeline

| Phase | Scope | Estimate |
|---|---|---|
| 0 | Prerequisites | 1–2 days |
| 1 | Skeleton + Meta DAT | 2–3 days |
| 2 | RTMP streaming stack | 3–5 days |
| 3 | Coordinator + domain | 2–3 days |
| 4 | UI | 1–2 weeks |
| 5 | Billing + polish | 3–5 days |
| 6 | Testing + release | 3–5 days |
| **Total** | | **~6–8 weeks solo** |

The Meta DAT port is low-risk — APIs match exactly. The biggest unknowns are the video pipeline
(`ByteBuffer` → `MediaCodec` → RTMP encoder) and Android runtime permissions (Bluetooth, camera, mic).
