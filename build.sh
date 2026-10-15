#!/bin/bash
set -e

# ==============================================================================
# ARTRAW Files — Official Native Build & Verification Pipeline
# Google Play Store Release Ready (AAB + APK)
# Package: com.artraw.files
# Version: 1.0.0 (versionCode: 1)
# Target SDK: Android 15 / 16 (API Level 35)
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

echo "========================================================"
echo " Starting ARTRAW Files Google Play Release Pipeline"
echo "========================================================"
echo " Package ID:        com.artraw.files"
echo " Version Name:      1.0.0"
echo " Version Code:      1"
echo " Target SDK:        API 35 (Android 15)"
echo " Min SDK:           API 21 (Android 5.0)"
echo " Keystore:          $RELEASE_KEYSTORE"
echo " AdMob Banner ID:   ca-app-pub-9417083543459963/1228883736"
echo "========================================================"

mkdir -p build/gen build/compiled build/classes build/test-classes build/dex build/module

# 1. Compile Resources with AAPT2
echo "[1/8] Compiling resources with AAPT2..."
aapt2 compile --dir res -o build/compiled/resources.zip

# 2. Link Resources for Binary APK & Generate R.java
echo "[2/8] Linking binary resources and generating R.java..."
aapt2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 1 \
    --version-name "1.0.0" \
    --java build/gen \
    -o build/base.apk \
    build/compiled/resources.zip

# 3. Link Resources in Protobuf format for AAB
echo "[3/8] Linking Protobuf resources for Android App Bundle (AAB)..."
aapt2 link -I "$ANDROID_JAR" \
    --proto-format \
    --manifest AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 1 \
    --version-name "1.0.0" \
    -o build/proto-base.apk \
    build/compiled/resources.zip

# 4. Compile Kotlin Sources with kotlinc
echo "[4/8] Compiling Kotlin application sources..."
kotlinc -classpath "$ANDROID_JAR:$ADMOB_CLASSPATH" \
    -d build/classes \
    build/gen/com/artraw/files/R.java \
    $(find src -name "*.kt")

# 5. Compile and Run Unit Tests
echo "[5/8] Compiling and running unit tests..."
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

# 6. R8 Code Shrinking, Minification & Optimization
echo "[6/8] Executing R8 release code shrinking & optimization..."
r8 --release \
   --min-api 26 \
   --lib "$ANDROID_JAR" \
   --pg-conf proguard-rules.pro \
   --pg-map-output build/mapping.txt \
   --output build/dex/ \
   $(find build/classes -name "*.class") \
   "$KOTLIN_STDLIB" \
   $(find "$ADMOB_LIBS_DIR" -name "*.jar")

# 7. Package and Sign Release APK (Universal)
echo "[7/8] Packaging and signing Release APK..."
python3 -c "
import zipfile
with zipfile.ZipFile('build/base.apk', 'a') as apk:
    apk.write('build/dex/classes.dex', 'classes.dex')
"

apksigner sign --ks "$RELEASE_KEYSTORE" \
    --ks-pass "pass:$RELEASE_STORE_PASS" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --out build/artraw-files-release.apk \
    build/base.apk

# 8. Build and Sign Release Android App Bundle (AAB) for Play Store
echo "[8/8] Building and signing Release Android App Bundle (.aab)..."
python3 -c "
import zipfile

with zipfile.ZipFile('build/proto-base.apk', 'r') as proto_apk:
    with zipfile.ZipFile('build/base.zip', 'w', zipfile.ZIP_DEFLATED) as base_zip:
        manifest_data = proto_apk.read('AndroidManifest.xml')
        base_zip.writestr('manifest/AndroidManifest.xml', manifest_data)
        for item in proto_apk.infolist():
            if item.filename.startswith('res/'):
                base_zip.writestr(item.filename, proto_apk.read(item.filename))
            elif item.filename == 'resources.pb':
                base_zip.writestr('resources.pb', proto_apk.read(item.filename))
        with open('build/dex/classes.dex', 'rb') as f:
            base_zip.writestr('dex/classes.dex', f.read())
"

java -jar "$BUNDLETOOL_JAR" build-bundle \
    --modules=build/base.zip \
    --output=build/artraw-files-unsigned.aab \
    --overwrite

jarsigner -keystore "$RELEASE_KEYSTORE" \
    -storepass "$RELEASE_STORE_PASS" \
    -keypass "$RELEASE_KEY_PASS" \
    -sigalg SHA256withRSA \
    -digestalg SHA-256 \
    build/artraw-files-unsigned.aab \
    "$RELEASE_KEY_ALIAS" > /dev/null

mv build/artraw-files-unsigned.aab build/artraw-files-release.aab

echo "========================================================"
echo " VERIFICATION SUMMARY:"
echo "--------------------------------------------------------"
echo " [AAB] Validating Android App Bundle..."
java -jar "$BUNDLETOOL_JAR" validate --bundle=build/artraw-files-release.aab > /dev/null
echo "       -> BundleTool structure validation: PASSED"
jarsigner -verify build/artraw-files-release.aab > /dev/null
echo "       -> Jarsigner release signature:      PASSED"
echo ""
echo " [APK] Validating Release APK..."
apksigner verify -v build/artraw-files-release.apk | grep -E "Verifies|v1 scheme|v2 scheme|v3 scheme"
echo "--------------------------------------------------------"
echo " RELEASE ARTIFACTS:"
echo " - AAB (Google Play Store): $(pwd)/build/artraw-files-release.aab"
echo "   Size: $(du -h build/artraw-files-release.aab | cut -f1)"
echo -n "   SHA-256: "
sha256sum build/artraw-files-release.aab | cut -d' ' -f1
echo ""
echo " - APK (Universal Direct):  $(pwd)/build/artraw-files-release.apk"
echo "   Size: $(du -h build/artraw-files-release.apk | cut -f1)"
echo -n "   SHA-256: "
sha256sum build/artraw-files-release.apk | cut -d' ' -f1
echo "========================================================"
