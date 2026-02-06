#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WATCH_DIR="$(dirname "$SCRIPT_DIR")/TAKWatch-IQ"

# Java 17 for ATAK plugin
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

# Connect IQ SDK — find the latest installed
CIQ_SDK_BASE="$HOME/Library/Application Support/Garmin/ConnectIQ/Sdks"
CIQ_SDK="$(ls -d "$CIQ_SDK_BASE"/connectiq-sdk-mac-* 2>/dev/null | sort -V | tail -1)"

echo "=== TAKWatch Build ==="
echo "Project:     $SCRIPT_DIR"
echo "Watch app:   $WATCH_DIR"
echo "Java:        $JAVA_HOME"
echo "CIQ SDK:     $CIQ_SDK"
echo ""

# ── ATAK Plugin ──────────────────────────────────────────────
echo "── Building ATAK Plugin ──"

cd "$SCRIPT_DIR"

# Keystore gets deleted by clean — copy it
mkdir -p app/build
if [ -f ATAKSDK/ATAK-CIV-5.6.0.12-SDK/android_keystore ]; then
    cp ATAKSDK/ATAK-CIV-5.6.0.12-SDK/android_keystore app/build/android_keystore
else
    echo "WARNING: android_keystore not found in ATAKSDK — build may fail"
fi

./gradlew assembleCivDebug

APK="$(ls -t app/build/outputs/apk/civ/debug/*.apk 2>/dev/null | head -1)"
if [ -n "$APK" ]; then
    echo "ATAK Plugin APK: $APK"
else
    echo "ERROR: APK not found after build"
    exit 1
fi

# ── Garmin Watch App ─────────────────────────────────────────
if [ -d "$WATCH_DIR" ]; then
    echo ""
    echo "── Building Garmin Watch App ──"

    MONKEYC="$CIQ_SDK/bin/monkeyc"
    if [ ! -f "$MONKEYC" ]; then
        echo "ERROR: monkeyc not found at $MONKEYC"
        echo "Install the Connect IQ SDK via Garmin SDK Manager"
        exit 1
    fi

    cd "$WATCH_DIR"

    if [ ! -f developer_key.der ]; then
        echo "Generating developer key..."
        openssl genrsa -out developer_key.pem 4096
        openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt
        rm developer_key.pem
    fi

    DEVICE="${CIQ_DEVICE:-fenix7spro}"
    "$MONKEYC" -o TAKWatch.prg -f monkey.jungle -y developer_key.der -d "$DEVICE"

    echo "Watch App PRG: $WATCH_DIR/TAKWatch.prg"
else
    echo ""
    echo "── Skipping Garmin Watch App (TAKWatch-IQ not found at $WATCH_DIR) ──"
fi

# ── Summary ──────────────────────────────────────────────────
echo ""
echo "=== Build Complete ==="
echo "ATAK Plugin: $APK"
[ -f "$WATCH_DIR/TAKWatch.prg" ] && echo "Watch App:   $WATCH_DIR/TAKWatch.prg"

# ── Optional: Deploy ─────────────────────────────────────────
if [ "$1" = "--deploy" ]; then
    echo ""
    echo "── Deploying ──"

    # Deploy ATAK plugin via ADB
    DEVICE_ID="$(adb devices | grep -v 'List\|^$' | head -1 | cut -f1)"
    if [ -n "$DEVICE_ID" ]; then
        echo "Installing APK to $DEVICE_ID..."
        adb -s "$DEVICE_ID" install -r "$APK"
    else
        echo "No ADB device connected — skipping APK install"
    fi

    # Deploy watch app via MTP
    if [ -f "$WATCH_DIR/TAKWatch.prg" ]; then
        if command -v mtp-folders &>/dev/null; then
            # Kill Garmin Express so we can claim USB
            killall "Garmin Express Service" 2>/dev/null || true
            killall "Garmin Express" 2>/dev/null || true
            sleep 1

            APPS_FOLDER="$(mtp-folders 2>&1 | grep -i '  Apps$' | awk '{print $1}')"
            if [ -n "$APPS_FOLDER" ]; then
                echo "Copying TAKWatch.prg to watch (folder ID $APPS_FOLDER)..."
                mtp-sendfile "$WATCH_DIR/TAKWatch.prg" "$APPS_FOLDER" 2>&1 | tail -3
            else
                echo "No Garmin watch detected via MTP — skipping watch deploy"
            fi
        else
            echo "mtp-tools not installed — skipping watch deploy (brew install libmtp)"
        fi
    fi
fi
