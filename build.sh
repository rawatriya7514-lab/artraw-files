#!/bin/bash
set -e

# ==============================================================================
# ARTRAW Files — Multi-Artifact Production Build Pipeline
#
# Generates 3 Distinct Verified Artifacts:
# 1. ARTRAW-Files-Offline.apk     (Personal Use: 0 Telemetry, 0 Ads, 0 Network)
# 2. ARTRAW-Files-Test-AdMob.apk  (Validation: Google Sample Banner Test Ads)
# 3. ARTRAW-Files-Production.aab  (Play Store: Real AdMob + R8/ProGuard Shrunk)
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

ANDROID_JAR="/data/data/com.termux/files/home/android-sdk/platforms/android-35/android.jar"
KOTLIN_STDLIB="/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar"
BUNDLETOOL_JAR="/data/data/com.termux/files/home/android-sdk/libs/bundletool.jar"
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
echo " Starting Art files (Artfiles) Multi-Artifact Pipeline"
echo "========================================================"
echo " Package ID:        com.artraw.files"
echo " Version:           1.1.0 (versionCode: 2)"
echo " Target SDK:        API 35 (Android 15)"
echo " Keystore:          $RELEASE_KEYSTORE"
echo " Output Directory:  $DOWNLOAD_DIR"
echo "========================================================"

mkdir -p build/gen build/classes build/test-classes build/dex_d8 build/dex_r8 build/artifacts

# ------------------------------------------------------------------------------
# STEP 1: Generate R.java & Compile Application Sources
# ------------------------------------------------------------------------------
echo "[1/6] Compiling resources & generating base R.java..."
aapt2 compile --dir res -o build/base_res.zip
aapt2 link -I "$ANDROID_JAR" \
    --manifest variants/production/AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 2 \
    --version-name "1.1.0" \
    --java build/gen \
    -o build/temp_base.apk \
    build/base_res.zip

echo "[2/6] Compiling Kotlin application sources..."
kotlinc -classpath "$ANDROID_JAR:$ADMOB_CLASSPATH" \
    -d build/classes \
    build/gen/com/artraw/files/R.java \
    $(find src -name "*.kt")

# ------------------------------------------------------------------------------
# STEP 2: Run Automated Unit Tests
# ------------------------------------------------------------------------------
echo "[3/6] Running unit tests..."
kotlinc -classpath "$ANDROID_JAR:build/classes:$JUNIT_JAR:$HAMCREST_JAR:$ADMOB_CLASSPATH" \
    -d build/test-classes \
    $(find test -name "*.kt")

java -cp "build/classes:build/test-classes:$JUNIT_JAR:$HAMCREST_JAR:$KOTLIN_STDLIB:$ADMOB_CLASSPATH" \
    org.junit.runner.JUnitCore \
    com.artraw.files.FileClassificationTest \
    com.artraw.files.PathValidationTest \
    com.artraw.files.ConflictResolutionTest \
    com.artraw.files.SortingRulesTest \
    com.artraw.files.AdMobIntegrationTest

# ------------------------------------------------------------------------------
# STEP 3: Generate DEX Bytecodes
# (D8 for zero-crash runtime retention; R8 for Play Store minified bundle)
# ------------------------------------------------------------------------------
echo "[4/6] Generating DEX bytecodes (D8 & R8)..."

# D8: Full runtime class retention
d8 $(find build/classes -name "*.class") \
   "$KOTLIN_STDLIB" \
   $(find "$ADMOB_LIBS_DIR" -name "*.jar") \
   --min-api 26 \
   --output build/dex_d8/

# R8: Release minification with AdMob keep rules for production AAB
r8 --release \
   --min-api 26 \
   --lib "$ANDROID_JAR" \
   --pg-conf proguard-rules.pro \
   --pg-map-output build/mapping.txt \
   --output build/dex_r8/ \
   $(find build/classes -name "*.class") \
   "$KOTLIN_STDLIB" \
   $(find "$ADMOB_LIBS_DIR" -name "*.jar")

# ------------------------------------------------------------------------------
# STEP 4: Build Artifact 1 — ArtFiles-Offline.apk
# ------------------------------------------------------------------------------
echo "[5/6] Building Artifact 1: ArtFiles-Offline.apk..."
rm -rf build/var_offline && mkdir -p build/var_offline/res
cp -r res/* build/var_offline/res/
cp variants/offline/res/values/admob.xml build/var_offline/res/values/admob.xml

aapt2 compile --dir build/var_offline/res -o build/var_offline/resources.zip
aapt2 link -I "$ANDROID_JAR" \
    --manifest variants/offline/AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 2 \
    --version-name "1.1.0" \
    -o build/var_offline/base.apk \
    build/var_offline/resources.zip

python3 -c "
import zipfile
with zipfile.ZipFile('build/var_offline/base.apk', 'a') as apk:
    apk.write('build/dex_r8/classes.dex', 'classes.dex')
"

apksigner sign --ks "$RELEASE_KEYSTORE" \
    --ks-pass "pass:$RELEASE_STORE_PASS" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --out build/artifacts/ArtFiles-Offline.apk \
    build/var_offline/base.apk

# ------------------------------------------------------------------------------
# STEP 5: Build Artifact 2 — ArtFiles-Test-AdMob.apk
# ------------------------------------------------------------------------------
echo "      Building Artifact 2: ArtFiles-Test-AdMob.apk..."
rm -rf build/var_test && mkdir -p build/var_test/res
cp -r res/* build/var_test/res/
cp variants/test/res/values/admob.xml build/var_test/res/values/admob.xml

aapt2 compile --dir build/var_test/res -o build/var_test/resources.zip
aapt2 link -I "$ANDROID_JAR" \
    --manifest variants/test/AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 2 \
    --version-name "1.1.0" \
    -o build/var_test/base.apk \
    build/var_test/resources.zip

python3 -c "
import zipfile
with zipfile.ZipFile('build/var_test/base.apk', 'a') as apk:
    apk.write('build/dex_r8/classes.dex', 'classes.dex')
"

apksigner sign --ks "$RELEASE_KEYSTORE" \
    --ks-pass "pass:$RELEASE_STORE_PASS" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --out build/artifacts/ArtFiles-Test-AdMob.apk \
    build/var_test/base.apk

# ------------------------------------------------------------------------------
# STEP 6: Build Artifact 3 — ArtFiles-Production.apk (Real AdMob Release)
# ------------------------------------------------------------------------------
echo "      Building Artifact 3: ArtFiles-Production.apk (Real AdMob APK)..."
rm -rf build/var_prod && mkdir -p build/var_prod/res
cp -r res/* build/var_prod/res/
cp variants/production/res/values/admob.xml build/var_prod/res/values/admob.xml

aapt2 compile --dir build/var_prod/res -o build/var_prod/resources.zip
aapt2 link -I "$ANDROID_JAR" \
    --manifest variants/production/AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 2 \
    --version-name "1.1.0" \
    -o build/var_prod/base.apk \
    build/var_prod/resources.zip

python3 -c "
import zipfile
with zipfile.ZipFile('build/var_prod/base.apk', 'a') as apk:
    apk.write('build/dex_r8/classes.dex', 'classes.dex')
"

apksigner sign --ks "$RELEASE_KEYSTORE" \
    --ks-pass "pass:$RELEASE_STORE_PASS" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --out build/artifacts/ArtFiles-Production.apk \
    build/var_prod/base.apk

# ------------------------------------------------------------------------------
# STEP 7: Create Full Project Source Code ZIP & Copy to /sdcard/Download/
# ------------------------------------------------------------------------------
echo "      Packaging complete project source code ZIP..."
python3 -c "
import os, zipfile
target = 'build/artifacts/ArtFiles-v1.1.0-SourceCode.zip'
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

echo "[6/6] Copying artifacts to $DOWNLOAD_DIR..."
cp build/artifacts/ArtFiles-Offline.apk "$DOWNLOAD_DIR/"
cp build/artifacts/ArtFiles-Test-AdMob.apk "$DOWNLOAD_DIR/"
cp build/artifacts/ArtFiles-Production.apk "$DOWNLOAD_DIR/"
cp build/artifacts/ArtFiles-v1.1.0-SourceCode.zip "$DOWNLOAD_DIR/"

# Also keep copy in storage emulated 0
cp build/artifacts/ArtFiles-* /storage/emulated/0/Download/ 2>/dev/null || true

# ------------------------------------------------------------------------------
# STEP 8: Verification Summary
# ------------------------------------------------------------------------------
echo "========================================================"
echo " MULTI-ARTIFACT VERIFICATION SUMMARY:"
echo "--------------------------------------------------------"

echo " 1. [Offline APK]:"
apksigner verify -v "$DOWNLOAD_DIR/ArtFiles-Offline.apk" | grep -E "Verifies|v1 scheme|v2 scheme|v3 scheme"
echo "    Path:    $DOWNLOAD_DIR/ArtFiles-Offline.apk"
echo "    Size:    $(du -h "$DOWNLOAD_DIR/ArtFiles-Offline.apk" | cut -f1)"
echo -n "    SHA-256: "
sha256sum "$DOWNLOAD_DIR/ArtFiles-Offline.apk" | cut -d' ' -f1

echo ""
echo " 2. [Test AdMob APK]:"
apksigner verify -v "$DOWNLOAD_DIR/ArtFiles-Test-AdMob.apk" | grep -E "Verifies|v1 scheme|v2 scheme|v3 scheme"
echo "    Path:    $DOWNLOAD_DIR/ArtFiles-Test-AdMob.apk"
echo "    Size:    $(du -h "$DOWNLOAD_DIR/ArtFiles-Test-AdMob.apk" | cut -f1)"
echo -n "    SHA-256: "
sha256sum "$DOWNLOAD_DIR/ArtFiles-Test-AdMob.apk" | cut -d' ' -f1

echo ""
echo " 3. [Production Real AdMob APK]:"
apksigner verify -v "$DOWNLOAD_DIR/ArtFiles-Production.apk" | grep -E "Verifies|v1 scheme|v2 scheme|v3 scheme"
echo "    Path:    $DOWNLOAD_DIR/ArtFiles-Production.apk"
echo "    Size:    $(du -h "$DOWNLOAD_DIR/ArtFiles-Production.apk" | cut -f1)"
echo -n "    SHA-256: "
sha256sum "$DOWNLOAD_DIR/ArtFiles-Production.apk" | cut -d' ' -f1

echo ""
echo " 4. [Full Source Code ZIP]:"
echo "    Path:    $DOWNLOAD_DIR/ArtFiles-v1.1.0-SourceCode.zip"
echo "    Size:    $(du -h "$DOWNLOAD_DIR/ArtFiles-v1.1.0-SourceCode.zip" | cut -f1)"
echo -n "    SHA-256: "
sha256sum "$DOWNLOAD_DIR/ArtFiles-v1.1.0-SourceCode.zip" | cut -d' ' -f1
echo "========================================================"

