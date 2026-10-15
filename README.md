# Artfiles — Modern, Fast & Private Android Storage & Gallery Manager

**Package ID**: `com.artraw.files`  
**Version**: `1.1.0` (Version Code `2`)  
**Target SDK**: Android 15 (API Level 35) / Android 16 Compatible  
**Architecture**: Native Kotlin + Android SDK + Material 3 Design  
**License**: Proprietary & Confidential — All Rights Reserved (Closed Source)

---

## 🔒 Security & Anti-Reverse Engineering Hardening

Artfiles incorporates commercial-grade anti-tamper and reverse-engineering protection:

1. **R8 Bytecode Obfuscation**:
   - All internal classes, business logic, storage algorithms, and models are minified, obfuscated, and flattened into non-human-readable identifiers.
   - Debugging metadata, line number tables, and source file descriptors are stripped.
2. **Cryptographic Anti-Tamper Signature Verification**:
   - The app verifies its own cryptographic SHA-256 certificate fingerprint (`81:2D:A6:11:CA:...`) at launch.
   - Any unauthorized APK decompilation, ad unit substitution, repackaging, or re-signing will trigger runtime integrity failure.
3. **Dynamic In-Memory AdMob Protection**:
   - Google AdMob ad units are masked in bytecode using runtime XOR encryption, blocking static analysis extraction tools.
4. **Anti-Debugging & Runtime Environment Check**:
   - Blocks unauthorized hooking frameworks and unauthorized debuggers in production builds.

---

## 🌟 Key Features

1. **Brand-New Custom App Icon**:
   - Ultra HD custom launcher icons generated with bicubic antialiasing across all density buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) with circular round mask support.
2. **All-Rounder Media Players (No External Apps Needed)**:
   - **Native Music Player**: Sleek dark UI, playlist support, playback controls, seekbar, skip ±10s, next/prev tracks.
   - **Native Video Player**: Fullscreen player with overlay controls, seekbar, gesture tap toggle, rewind/forward 10s.
   - **Native Photo Viewer**: Fullscreen photo gallery viewer with swipe navigation, image dimensions, share & delete.
3. **ZArchiver-Grade Archive Suite (ZIP Maker & Viewer)**:
   - **Archive Maker (Compress to ZIP)**: Compress single or multi-selected files and folders to ZIP with custom naming.
   - **Archive Viewer (Inspect Inside)**: Open and explore contents inside `.zip`, `.tar`, `.gz` files without extracting.
   - **Safe Extraction**: One-tap extract all with progress indicator and Zip Slip security protection.
4. **Gallery Experience & Real Thumbnail Caching**:
   - Asynchronous thumbnail rendering with an in-memory `LruCache` and thread pool for smooth scrolling.
   - Default Grid View for Images & Videos with thumbnail previews and badge indicators.
   - Instant toggle between Grid and List views.
5. **Multi-Select, Batch Copy & Move (Cut)**:
   - Visual checkmark badges on grid items (Google Photos / Samsung Gallery style).
   - "Select All" / "Deselect All" one-touch controls.
   - Sticky bottom action bar dynamically displaying **Move Here** or **Paste Here** with pending file count.
6. **New Folder Creation**:
   - Dedicated "+ New Folder" action in the top navigation bar and floating action button.
   - Modal dialog with instant name validation and sanitization.
7. **Advanced Duplicate Cleaner**:
   - Two-phase detection: fast file size matching followed by cryptographic SHA-256 hash verification.
   - **Auto-Select Copies**: Smart selection algorithm that automatically selects redundant copies while preserving the primary file.
8. **100% Guaranteed AdMob Integration**:
   - Official Google AdMob App ID & Banner Unit ID integration.
   - Post-initialization lifecycle loading with automatic fallback to test banner if live ad inventory returns no fill, ensuring ads always appear!

---

## 📥 Official Downloads

- **Official Release APK (Artfiles.apk)**: [Artfiles.apk (6.1 MB)](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.1.0/Artfiles.apk)
- **Offline / Ad-Free APK (For Developers)**: [ArtFiles-Offline.apk (6.1 MB)](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.1.0/ArtFiles-Offline.apk)
- **Full Project Source ZIP**: [Artfiles-v1.1.0-SourceCode.zip](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.1.0/Artfiles-v1.1.0-SourceCode.zip)

---

## ⚖️ Legal & Intellectual Property Notice
Copyright © 2026 ARTRAW. All Rights Reserved.  
This software is proprietary and confidential. Decompilation, disassembly, reverse engineering, unauthorized extraction of source code or assets, tampering, redistribution, or creation of derivative works without prior written permission is strictly prohibited.
