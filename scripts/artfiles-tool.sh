#!/bin/bash
# ==============================================================================
# Artfiles CLI Operations & Maintenance Tool
# ==============================================================================
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

COMMAND="${1:-help}"

case "$COMMAND" in
    build)
        echo "==> Running full Artfiles build pipeline..."
        ./build.sh
        ;;
    verify)
        echo "==> Verifying Artfiles.apk release integrity..."
        if [ -f "build/artifacts/Artfiles.apk" ]; then
            apksigner verify -v "build/artifacts/Artfiles.apk" | grep -E "Verifies|v1 scheme|v2 scheme|v3 scheme"
            ls -lh "build/artifacts/Artfiles.apk"
            sha256sum "build/artifacts/Artfiles.apk"
        else
            echo "Error: build/artifacts/Artfiles.apk not found. Run './scripts/artfiles-tool.sh build' first."
            exit 1
        fi
        ;;
    release)
        echo "==> Publishing Artfiles.apk to GitHub Release..."
        if [ ! -f "build/artifacts/Artfiles.apk" ]; then
            echo "Error: Artfiles.apk not found. Building first..."
            ./build.sh
        fi
        TAG="${2:-v1.1.0}"
        echo "Uploading to GitHub release tag: $TAG"
        gh release upload "$TAG" "build/artifacts/Artfiles.apk" --clobber
        gh release upload "$TAG" "build/artifacts/ArtFiles-Offline.apk" --clobber
        echo "==> GitHub release updated successfully!"
        ;;
    status)
        echo "==> Artfiles Project Status:"
        git status -s
        echo "--- Latest Commits ---"
        git log -n 3 --oneline
        echo "--- Download Artifacts ---"
        ls -lh /sdcard/Download/Artfiles* 2>/dev/null || true
        ;;
    *)
        echo "Usage: ./scripts/artfiles-tool.sh [build|verify|release|status]"
        exit 0
        ;;
esac
