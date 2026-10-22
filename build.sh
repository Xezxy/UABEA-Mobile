#!/usr/bin/env bash
# Script build untuk Termux (aarch64) tanpa Android Studio dan tanpa GUI SDK.
# Hasil akhir: ./output/app-debug.apk

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "[build] Project   : $PROJECT_DIR"
echo "[build] ANDROID_HOME: $ANDROID_HOME"

if [ ! -d "$ANDROID_HOME" ]; then
    echo "[build] Folder SDK belum ada. Jalankan langkah instalasi SDK di README.md dulu." >&2
    exit 1
fi

if [ ! -f gradlew ]; then
    echo "[build] gradlew tidak ditemukan di $PROJECT_DIR" >&2
    exit 1
fi

chmod +x gradlew

# local.properties dibuat otomatis kalau belum ada, isinya menunjuk ke SDK Termux.
if [ ! -f local.properties ]; then
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
    echo "[build] local.properties dibuat: sdk.dir=$ANDROID_HOME"
fi

# Di aarch64, biner aapt2 bawaan Google Maven berarsitektur x86_64.
# Kalau Termux punya aapt2 native, pakai itu lewat override resmi AGP.
GRADLE_ARGS=(assembleDebug --no-daemon --stacktrace)

if [ "$(uname -m)" = "aarch64" ] && command -v aapt2 >/dev/null 2>&1; then
    AAPT2_PATH="$(command -v aapt2)"
    GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2_PATH")
    echo "[build] aapt2 Termux dipakai: $AAPT2_PATH"
fi

echo "[build] Menjalankan: ./gradlew ${GRADLE_ARGS[*]}"
./gradlew "${GRADLE_ARGS[@]}"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "[build] APK tidak ditemukan di $APK_PATH" >&2
    exit 1
fi

mkdir -p output
cp -f "$APK_PATH" output/app-debug.apk

echo
echo "[build] Selesai."
echo "[build] APK: $PROJECT_DIR/output/app-debug.apk"
ls -lh "$PROJECT_DIR/output/app-debug.apk"
