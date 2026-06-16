#!/usr/bin/env bash
set -euo pipefail

APP_ID="app.streammog.android"
MAIN_ACTIVITY="app.streammog.android.MainActivity"
CONFIGURATION="debug"
DEVICE_SERIAL=""
LAUNCH_APP=1
SKIP_BUILD=0

usage() {
  cat <<'EOF'
Build AvaLens for a connected Android device or emulator, install it, and optionally launch it.

Usage:
  scripts/install-android-build.sh [options]

Options:
  --device <serial>              Install to a specific device (adb serial). Default: first available.
  --configuration <debug|release>  Gradle build variant. Default: debug
  --skip-build                   Install the existing APK without rebuilding.
  --no-launch                    Install only; do not launch the app.
  --help                         Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --configuration)
      CONFIGURATION="${2:-}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --no-launch)
      LAUNCH_APP=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# ── Locate adb ────────────────────────────────────────────────────────────────

ADB=""
for candidate in \
    "$(command -v adb 2>/dev/null)" \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb"; do
  if [[ -x "$candidate" ]]; then
    ADB="$candidate"
    break
  fi
done

if [[ -z "$ADB" ]]; then
  echo "adb not found. Install Android SDK platform-tools or add it to PATH." >&2
  exit 1
fi

# ── Resolve target device ─────────────────────────────────────────────────────

if [[ -n "$DEVICE_SERIAL" ]]; then
  ADB_ARGS=(-s "$DEVICE_SERIAL")
else
  # Pick the first online device/emulator
  DEVICE_SERIAL="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [[ -z "$DEVICE_SERIAL" ]]; then
    echo "No Android devices/emulators found. Connect a device or start an emulator." >&2
    exit 1
  fi
  ADB_ARGS=(-s "$DEVICE_SERIAL")
fi

DEVICE_MODEL="$("$ADB" "${ADB_ARGS[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "Using device: ${DEVICE_MODEL:-$DEVICE_SERIAL} ($DEVICE_SERIAL)"

# ── Resolve APK path ──────────────────────────────────────────────────────────

# Gradle task name uses lowercase variant: assembleDebug / assembleRelease
GRADLE_TASK="assemble$(tr '[:lower:]' '[:upper:]' <<< "${CONFIGURATION:0:1}")${CONFIGURATION:1}"

if [[ "$CONFIGURATION" == "release" ]]; then
  APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
else
  APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
fi

# ── Build ─────────────────────────────────────────────────────────────────────

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "Building ($CONFIGURATION)..."

  # Resolve JAVA_HOME — always prefer the macOS java_home helper so stale
  # environment values (e.g. /usr/bin/java) don't break the Gradle wrapper.
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    resolved_java="$(/usr/libexec/java_home 2>/dev/null)"
    [[ -d "$resolved_java" ]] && export JAVA_HOME="$resolved_java"
  fi

  "$ROOT_DIR/gradlew" ":app:$GRADLE_TASK"
else
  echo "Skipping build; using existing APK..."
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found at $APK_PATH" >&2
  [[ "$SKIP_BUILD" -eq 1 ]] && echo "Run without --skip-build first." >&2
  exit 1
fi

# ── Install ───────────────────────────────────────────────────────────────────

echo "Installing $APK_PATH..."
"$ADB" "${ADB_ARGS[@]}" install -r "$APK_PATH"
echo "Installed $APP_ID on ${DEVICE_MODEL:-$DEVICE_SERIAL}"

# ── Launch ────────────────────────────────────────────────────────────────────

if [[ "$LAUNCH_APP" -eq 1 ]]; then
  echo "Launching $APP_ID..."
  "$ADB" "${ADB_ARGS[@]}" shell am start -S -n "$APP_ID/$MAIN_ACTIVITY"
fi
