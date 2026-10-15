---
name: artfiles-manager
description: |
  Manage, build, test, verify, and release the Artfiles Android application. Specialized skill for compiling Kotlin sources with AAPT2 and D8, signing release APKs with apksigner, verifying AdMob configurations and banner fallbacks, managing media players & archive tools, and publishing official Artfiles.apk releases to GitHub.
---

# Artfiles Manager Skill

Comprehensive automation and operations guide for **Artfiles** (`com.artraw.files`), the modern, privacy-first, all-rounder Android storage manager and media studio.

---

## Quick Reference

| Action | Command | Output |
| :--- | :--- | :--- |
| **Full Build** | `./build.sh` | Generates `Artfiles.apk`, `ArtFiles-Offline.apk`, source ZIP |
| **Verify Signatures** | `apksigner verify -v build/artifacts/Artfiles.apk` | Verifies v1, v2, v3 signature schemes |
| **Check Artifacts** | `ls -lh /sdcard/Download/Artfiles*` | Primary release APKs in user's Download directory |
| **Publish to GitHub** | `gh release upload v1.1.0 build/artifacts/Artfiles.apk --clobber` | Updates official GitHub release |

---

## Project Specifications

- **Application Name**: Artfiles
- **Package ID**: `com.artraw.files`
- **Current Version**: 1.1.0 (versionCode: 2)
- **Target SDK**: Android 15 (API Level 35)
- **Minimum SDK**: Android 5.0 (API Level 21)
- **Release Keystore**: `keystore/artraw-release.keystore`
- **Output Target**: Strict naming `Artfiles.apk` (6.1 MB full D8 dex with all media players and AdMob classes)

---

## AdMob Architecture & Guaranteed Display

Artfiles implements a dual-mode high-reliability banner engine (`AdMobManager.kt`):

1. **Production Unit ID**: `ca-app-pub-9417083543459963/1228883736` (Runtime XOR obfuscated in `AppSecurity.kt`).
2. **Google Guaranteed Test Unit ID**: `ca-app-pub-3940256099942544/6300978111`.
3. **Fallback Logic**:
   - When sideloaded or before Google Play review, AdMob live ads frequently return Error Code 3 (`ERROR_CODE_NO_FILL`).
   - `AdMobManager` catches `onAdFailedToLoad` and immediately re-requests Google's official test banner.
   - A 4.5-second safety watchdog ensures ads are displayed even if network requests hang.
4. **Layout Requirements**:
   - `adViewContainer` must be `visibility="visible"` with a fixed `50dp` height so AdMob measures valid dimensions on the first layout pass.

---

## Built-in Media & Archive Suite

- **Audio Player**: `AudioPlayerActivity.kt` (`activity_audio_player.xml`)
  - Auto playlist queue, progress seekbar, skip ±10s, next/prev tracks.
- **Video Player**: `VideoPlayerActivity.kt` (`activity_video_player.xml`)
  - Fullscreen video playback with autohiding overlay controls and seekbar.
- **Photo Viewer**: `ImageViewerActivity.kt` (`activity_image_viewer.xml`)
  - Fullscreen gallery with swipe gestures, sharing, and metadata.
- **Archive Studio**:
  - `ArchiveManager.kt`: Fast ZIP compression and extraction with Zip Slip directory traversal protection.
  - `ArchiveViewerActivity.kt`: In-archive file inspector without extracting.
- **Routing**: `FileViewerHelper.kt` routes all MIME types and extensions to internal native activities.

---

## Release Checklist

1. Verify `./build.sh` completes with exit code 0.
2. Confirm `build/artifacts/Artfiles.apk` is ~6.1 MB and signed with v1, v2, v3 schemes.
3. Verify file copy to `/sdcard/Download/Artfiles.apk`.
4. Upload `Artfiles.apk` to GitHub release using `gh release upload`.
5. Update `README.md` download links.
