#!/bin/bash
set -e

# ==============================================================================
# ARTRAW Files — Single Production APK Build Pipeline
#
# Generates ONE verified production artifact:
#   Artfiles.apk  (Production Release: Signed, v1+v2+v3)
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

ANDROID_JAR="/data/data/com.termux/files/home/android-sdk/platforms/android-35/android.jar"
KOTLIN_STDLIB="/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar"
JUNIT_JAR="/data/data/com.termux/files/home/android-sdk/libs/junit-4.13.2.jar"
HAMCREST_JAR="/data/data/com.termux/files/home/android-sdk/libs/hamcrest-core-1.3.jar"
ADMOB_LIBS_DIR="/data/data/com.termux/files/home/android-sdk/libs/admob"
ADMOB_CLASSPATH="$(find "$ADMOB_LIBS_DIR" -name "*.jar" | tr '\n' ':')"

# Release Keystore Configuration
RELEASE_KEYSTORE="${RELEASE_KEYSTORE:-$PROJECT_DIR/keystore/artraw-release.keystore}"
RELEASE_KEY_ALIAS="${RELEASE_KEY_ALIAS:-artraw}"
RELEASE_STORE_PASS="${RELEASE_STORE_PASS:-artraw123}"
RELEASE_KEY_PASS="${RELEASE_KEY_PASS:-artraw123}"

DOWNLOAD_DIR="/sdcard/Download"
mkdir -p "$DOWNLOAD_DIR"

echo "========================================================"
echo "  ArtFiles — Production APK Build Pipeline"
echo "========================================================"
echo "  Package ID:    com.artraw.files"
echo "  Version:       1.0.0 (versionCode: 1)"
echo "  Target SDK:    API 35 (Android 15)"
echo "  Min SDK:       21"
echo "  Keystore:      $RELEASE_KEYSTORE"
echo "  Output:        $DOWNLOAD_DIR/Artfiles.apk"
echo "========================================================"

mkdir -p build/gen build/classes build/test-classes build/dex_d8 build/artifacts

# ------------------------------------------------------------------------------
# STEP 1: Generate R.java & Compile Application Sources
# ------------------------------------------------------------------------------
echo "[1/5] Compiling resources & generating R.java..."
aapt2 compile --dir res -o build/base_res.zip
aapt2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 1 \
    --version-name "1.0.0" \
    --java build/gen \
    -o build/base.apk \
    build/base_res.zip

echo "[2/5] Compiling Kotlin application sources..."
kotlinc -classpath "$ANDROID_JAR:$ADMOB_CLASSPATH" \
    -d build/classes \
    build/gen/com/artraw/files/R.java \
    $(find src -name "*.kt")

# ------------------------------------------------------------------------------
# STEP 2: Run Unit Tests
# ------------------------------------------------------------------------------
echo "[3/5] Running unit tests..."
kotlinc -classpath "$ANDROID_JAR:build/classes:$JUNIT_JAR:$HAMCREST_JAR:$ADMOB_CLASSPATH" \
    -d build/test-classes \
    $(find test -name "*.kt")

java -cp "build/classes:build/test-classes:$JUNIT_JAR:$HAMCREST_JAR:$KOTLIN_STDLIB:$ADMOB_CLASSPATH" \
    org.junit.runner.JUnitCore \
    com.artraw.files.FileClassificationTest \
    com.artraw.files.PathValidationTest \
    com.artraw.files.ConflictResolutionTest \
    com.artraw.files.SortingRulesTest \
    com.artraw.files.AdMobIntegrationTest \
    com.artraw.files.DeviceMonitorTest

# ------------------------------------------------------------------------------
# STEP 3: Generate DEX Bytecodes (D8)
# ------------------------------------------------------------------------------
echo "[4/5] Generating DEX bytecodes (D8)..."
d8 $(find build/classes -name "*.class") \
    "$KOTLIN_STDLIB" \
    $(find "$ADMOB_LIBS_DIR" -name "*.jar") \
    --min-api 26 \
    --output build/dex_d8/

# ------------------------------------------------------------------------------
# STEP 4: Build Production APK
# ------------------------------------------------------------------------------
echo "[5/5] Building production APK: Artfiles.apk..."

# Create a clean APK with resources + DEX
python3 -c "
import zipfile, shutil, os

# Start from the linked base APK (has resources.arsc, manifest, etc.)
shutil.copy('build/base.apk', 'build/artifacts/Artfiles.apk')

# Add classes.dex (stored uncompressed for ART direct mmap and ~7.1 MB target size)
with zipfile.ZipFile('build/artifacts/Artfiles.apk', 'a') as apk:
    apk.write('build/dex_d8/classes.dex', 'classes.dex')
"

# Sign the APK
apksigner sign --ks "$RELEASE_KEYSTORE" \
    --ks-pass "pass:$RELEASE_STORE_PASS" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --out build/artifacts/Artfiles.apk \
    build/artifacts/Artfiles.apk 2>/dev/null || true

# If in-place signing failed, sign to a temp file and move
if [ ! -f "build/artifacts/Artfiles.apk" ] || ! apksigner verify -v "build/artifacts/Artfiles.apk" >/dev/null 2>&1; then
    apksigner sign --ks "$RELEASE_KEYSTORE" \
        --ks-pass "pass:$RELEASE_STORE_PASS" \
        --ks-key-alias "$RELEASE_KEY_ALIAS" \
        --key-pass "pass:$RELEASE_KEY_PASS" \
        --out build/artifacts/Artfiles.apk.signed \
        build/artifacts/Artfiles.apk
    mv build/artifacts/Artfiles.apk.signed build/artifacts/Artfiles.apk
fi

# Copy to Download directory
cp build/artifacts/Artfiles.apk "$DOWNLOAD_DIR/Artfiles.apk"

# Also copy source ZIP
python3 -c "
import os, zipfile
target = '$DOWNLOAD_DIR/Artfiles-v1.0.0-SourceCode.zip'
if os.path.exists(target):
    os.remove(target)
with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk('.'):
        dirs[:] = [d for d in dirs if d not in ('build', '.git')]
        for f in files:
            if f.endswith('.idsig') or f.endswith('.class'):
                continue
            path = os.path.join(root, f)
            arcname = os.path.relpath(path, '.')
            zf.write(path, arcname)
"

# ------------------------------------------------------------------------------
# STEP 5: Verification
# ------------------------------------------------------------------------------
echo ""
echo "========================================================"
echo "  VERIFICATION SUMMARY"
echo "========================================================"

echo "  APK Path:    $DOWNLOAD_DIR/Artfiles.apk"
echo "  APK Size:    $(stat -c%s "$DOWNLOAD_DIR/Artfiles.apk") bytes"
echo -n "  APK SHA-256: "
sha256sum "$DOWNLOAD_DIR/Artfiles.apk" | cut -d' ' -f1

echo ""
echo "  Signature Verification:"
apksigner verify -v "$DOWNLOAD_DIR/Artfiles.apk" 2>&1 | grep -E "Verifies|v1 scheme|v2 scheme|v3 scheme"

echo ""
echo "  Source ZIP:    $DOWNLOAD_DIR/Artfiles-v1.0.0-SourceCode.zip"
echo "  Source SHA-256:"
sha256sum "$DOWNLOAD_DIR/Artfiles-v1.0.0-SourceCode.zip" | cut -d' ' -f1

echo ""
echo "========================================================"
echo "  BUILD COMPLETE — Artfiles.apk ready for release"
echo "========================================================"
