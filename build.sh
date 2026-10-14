#!/bin/bash
set -e

# ==============================================================================
# ARTRAW Files — Official Native Build & Verification Pipeline
# Target: Android 16 (API Level 36 / 35 Compatibility)
# Package: com.artraw.files
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

ANDROID_JAR="/data/data/com.termux/files/home/android-sdk/platforms/android-35/android.jar"
KOTLIN_STDLIB="/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar"
KEYSTORE="/data/data/com.termux/files/home/android-sdk/debug.keystore"
JUNIT_JAR="/data/data/com.termux/files/home/android-sdk/libs/junit-4.13.2.jar"
HAMCREST_JAR="/data/data/com.termux/files/home/android-sdk/libs/hamcrest-core-1.3.jar"

echo "========================================================"
echo " Starting ARTRAW Files Build & Verification Pipeline"
echo "========================================================"

mkdir -p build/gen build/compiled build/classes build/test-classes build/dex

# 1. Compile Resources with AAPT2
echo "[1/6] Compiling resources with AAPT2..."
aapt2 compile --dir res -o build/compiled/resources.zip

# 2. Link Resources & Generate R.java
echo "[2/6] Linking resources and generating R.java..."
aapt2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --java build/gen \
    -o build/base.apk \
    build/compiled/resources.zip

# 3. Compile Kotlin Sources with kotlinc
echo "[3/6] Compiling Kotlin application sources..."
kotlinc -classpath "$ANDROID_JAR" \
    -d build/classes \
    build/gen/com/artraw/files/R.java \
    $(find src -name "*.kt")

# 4. Compile and Run Unit Tests
echo "[4/6] Compiling and running unit tests..."
kotlinc -classpath "$ANDROID_JAR:build/classes:$JUNIT_JAR:$HAMCREST_JAR" \
    -d build/test-classes \
    $(find test -name "*.kt")

java -cp "build/classes:build/test-classes:$JUNIT_JAR:$HAMCREST_JAR:$KOTLIN_STDLIB" \
    org.junit.runner.JUnitCore \
    com.artraw.files.FileClassificationTest \
    com.artraw.files.PathValidationTest \
    com.artraw.files.ConflictResolutionTest \
    com.artraw.files.SortingRulesTest

# 5. Compile Bytecode with D8
echo "[5/6] Generating DEX bytecode with D8..."
d8 $(find build/classes -name "*.class") \
   "$KOTLIN_STDLIB" \
   --min-api 26 \
   --output build/dex/

# 6. Package and Sign APK
echo "[6/6] Packaging and signing release APK..."
python3 -c "
import zipfile
with zipfile.ZipFile('build/base.apk', 'a') as apk:
    apk.write('build/dex/classes.dex', 'classes.dex')
"

apksigner sign --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --out build/artraw-files-release.apk \
    build/base.apk

echo "========================================================"
echo " APK VERIFICATION:"
apksigner verify -v build/artraw-files-release.apk
echo "--------------------------------------------------------"
echo " APK Path: $(pwd)/build/artraw-files-release.apk"
echo -n " SHA-256: "
sha256sum build/artraw-files-release.apk
echo "========================================================"
