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
   - High-resolution, bicubic antialiased launcher icons across all standard DPI buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) with circular round mask support.
2. **Gallery Experience & Real Thumbnail Caching**:
   - Asynchronous thumbnail rendering with an in-memory `LruCache` and thread pool for smooth scrolling.
   - Default Grid View for Images & Videos with thumbnail previews and badge indicators.
   - Instant toggle between Grid and List views.
3. **Multi-Select, Batch Copy & Move (Cut)**:
   - Visual checkmark badges on grid items (Google Photos / Samsung Gallery style).
   - "Select All" / "Deselect All" one-touch controls.
   - Sticky bottom action bar dynamically displaying **Move Here** or **Paste Here** with pending file count.
   - Cyclic move prevention and non-blocking background I/O.
4. **New Folder Creation**:
   - Dedicated "+ New Folder" action in the top navigation bar and floating action button.
   - Modal dialog with instant name validation and sanitization.
5. **Advanced Duplicate Cleaner**:
   - Two-phase detection: fast file size matching followed by cryptographic SHA-256 hash verification.
   - **Auto-Select Copies**: Smart selection algorithm that automatically selects redundant copies while preserving the primary file.
   - **Select All** & **Deselect All** granular controls.
   - Sticky action bar displaying exact reclaimable space (e.g. `Delete Selected (Free 45.2 MB)`).
6. **Direct Path Navigation & Quick Copy**:
   - Tap pencil icon to enter or paste custom storage paths.
   - One-tap button to copy current directory path to clipboard.
7. **AdMob Monetization (Official Production Integration)**:
   - Fully compliant AdMob Banner integration with banner lifecycle management and seamless background loading.

---

## 📥 Official Download

- **Production APK (AdMob Monetized & Hardened)**: [ArtFiles-Production.apk (3.1 MB)](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.1.0/ArtFiles-Production.apk)

---

## ⚖️ Legal & Intellectual Property Notice
Copyright © 2026 ARTRAW. All Rights Reserved.  
This software is proprietary and confidential. Decompilation, disassembly, reverse engineering, unauthorized extraction of source code or assets, tampering, redistribution, or creation of derivative works without prior written permission is strictly prohibited.
