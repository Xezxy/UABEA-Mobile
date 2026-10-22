#!/usr/bin/env sh
#
# gradlew untuk Termux (aarch64), tanpa gradle-wrapper.jar.
#
# Cara kerja:
#   1. Kalau distribusi Gradle 8.1.2 sudah ada di $GRADLE_USER_HOME, langsung pakai.
#   2. Kalau belum ada, unduh gradle-8.1.2-bin.zip dari services.gradle.org lalu ekstrak.
#   3. Kalau unduhan gagal dan ada gradle sistem, pakai gradle sistem sebagai cadangan.
#
# Semua argumen diteruskan apa adanya ke Gradle.

set -eu

GRADLE_VERSION="8.1.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_ROOT="$GRADLE_USER_HOME/toolbox-dist"
GRADLE_BIN="$DIST_ROOT/gradle-$GRADLE_VERSION/bin/gradle"
DIST_URL="${GRADLE_DIST_URL:-https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip}"
ZIP_PATH="$DIST_ROOT/gradle-$GRADLE_VERSION-bin.zip"

if [ -x "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
fi

mkdir -p "$DIST_ROOT"

echo "[gradlew] Gradle $GRADLE_VERSION belum tersedia."
echo "[gradlew] Mengunduh dari: $DIST_URL"

DOWNLOAD_OK=0
if command -v curl >/dev/null 2>&1; then
    if curl -fL --retry 3 --connect-timeout 20 -o "$ZIP_PATH" "$DIST_URL"; then
        DOWNLOAD_OK=1
    fi
elif command -v wget >/dev/null 2>&1; then
    if wget -O "$ZIP_PATH" "$DIST_URL"; then
        DOWNLOAD_OK=1
    fi
fi

if [ "$DOWNLOAD_OK" -eq 1 ]; then
    if ! command -v unzip >/dev/null 2>&1; then
        echo "[gradlew] unzip tidak ditemukan. Jalankan: pkg install unzip" >&2
        exit 1
    fi
    unzip -q -o "$ZIP_PATH" -d "$DIST_ROOT"
    rm -f "$ZIP_PATH"
    chmod +x "$GRADLE_BIN"
    exec "$GRADLE_BIN" "$@"
fi

echo "[gradlew] Unduhan gagal." >&2
if command -v gradle >/dev/null 2>&1; then
    echo "[gradlew] Memakai Gradle sistem sebagai cadangan." >&2
    exec gradle "$@"
fi

echo "[gradlew] Tidak ada Gradle yang bisa dipakai." >&2
echo "[gradlew] Pasang curl dan unzip: pkg install curl unzip" >&2
echo "[gradlew] Atau set GRADLE_DIST_URL ke mirror distribusi Gradle." >&2
exit 1
