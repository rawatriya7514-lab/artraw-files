#!/bin/bash
set -e

# ==============================================================================
# Artfiles UI & Resource Fast Verification Tool
# Compiles and validates Android XML resources, layouts, and AAPT2 link phase.
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

ANDROID_JAR="/data/data/com.termux/files/home/android-sdk/platforms/android-35/android.jar"
TEMP_BUILD="$PROJECT_DIR/build/ui_verify"
rm -rf "$TEMP_BUILD"
mkdir -p "$TEMP_BUILD"

echo "🎨 Validating Artfiles UI and Resources..."
echo "  [1/2] Compiling resources with AAPT2..."
aapt2 compile --dir res -o "$TEMP_BUILD/res.zip"

echo "  [2/2] Linking resources against Android API 35..."
aapt2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    -o "$TEMP_BUILD/verify.apk" \
    "$TEMP_BUILD/res.zip"

rm -rf "$TEMP_BUILD"
echo "✅ UI & Resources Validation PASSED! No layout or drawable errors."
