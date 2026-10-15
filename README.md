# ARTRAW Files — Secured Native Android Storage Manager

**Package ID**: `com.artraw.files`  
**Target SDK**: Android 15 (API Level 35) / Android 16 Compatible  
**Architecture**: Native Kotlin + Android SDK + Material 3 Design  
**License**: Proprietary & Confidential — All Rights Reserved (Closed Source)

---

## 🔒 Security & Anti-Reverse Engineering Protection

ARTRAW Files includes multi-tier bytecode hardening and tamper protection:

1. **R8 Bytecode Obfuscation**:
   - All proprietary business logic, models, analysis algorithms, and utilities are aggressively obfuscated and flattened into unreadable symbols.
   - Stripped debugging metadata, line number tables, and source file identifiers (`!SourceFile,!LineNumberTable`).
2. **Cryptographic Anti-Tamper Signature Verification**:
   - The application verifies its own cryptographic SHA-256 certificate fingerprint (`81:2D:A6:11:CA:...`) at runtime.
   - Any attempt to decompile, patch, replace ad units, and re-sign the APK with a custom key will fail integrity checks and prevent unauthorized clone execution.
3. **Dynamic In-Memory AdMob Decryption**:
   - Google AdMob ad unit identifiers are encrypted using runtime masking in bytecode, preventing plaintext scraping or replacement via decompilation tools.
4. **Anti-Debugging & Anti-Cloning**:
   - Enforces production runtime environment integrity and rejects debuggable hooks.

---

## Key Features

1. **Modern Flagship UI / UX**:
   - Deep charcoal/slate surfaces (`#121316`) with signature crimson/ruby accents (`#E53935`).
   - Clean Material 3 cards, rounded elevation, and responsive layouts.
   - Dual theme support (Refined Dark and Clean Light) with English and Hindi localization.
2. **Direct Path Navigation & Copy Path**:
   - **Type Path**: Tap the edit/pencil icon on the path bar to directly type or paste any directory or file path.
   - **Copy Path**: One-tap copy button right in the navigation bar to copy the current path to clipboard.
   - **Contextual Copy Path**: Long-press or select any file/folder and choose "Copy Path" from the More menu.
3. **Comprehensive Document & Media Recognition**:
   - Documents: PDF, TXT, DOC, DOCX, XLS, XLSX, PPT, PPTX, CSV, RTF, ODT, ODS, ODP, MD, JSON, XML, HTML, LOG.
   - Media: Images (JPG, PNG, WEBP, GIF, SVG, BMP, HEIC), Videos (MP4, MKV, AVI, MOV, WEBM), Audio (MP3, WAV, OGG, FLAC, M4A, AAC, OPUS).
   - APKs: Package name, version name, label inspection without automatic execution.
   - Archives: ZIP, 7Z, TAR, GZ, RAR with honest format transparency (ZIP/GZ internal unpack; 7Z/RAR external app).
4. **Contextual Operations Engine**:
   - Top contextual action bar: `COPY | CUT | MOVE | SHARE | MORE (Rename, Details, Open With, Delete)`.
   - Sticky Paste bar when clipboard items are pending.
   - Conflict resolution: `Replace`, `Keep Both (Auto-numbered)`, `Skip`, `Cancel`.
   - Cyclic copy/move prevention (`isDescendantOf` validation).
   - Safe file writes using temporary swap files.
   - Permanent delete confirmation with item count and byte size.
5. **Storage Analysis & Cryptographic Duplicate Detection**:
   - Large Files discovery (>50 MB).
   - Duplicate Files finder: Preliminary file size filter followed by on-demand SHA-256 cryptographic verification.
6. **Built-in Text Viewer & FileProvider**:
   - Integrated monospace text/code reader with line count, file size, and share sheet.
   - Secure `ArtrawFileProvider` content URIs (never exposes raw `file://` URIs).

---

## Downloads & Releases

Direct download for the secured, hardened production APK with Google AdMob revenue integration:
- **Production APK (Secured & Hardened)**: [ARTRAW-Files-Production.apk (2.8 MB)](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.0.0-admob/ARTRAW-Files-Production.apk)

---

## Verification Summary
- **JUnit Test Runner**: 12/12 Tests PASSED
- **APK Signatures**: v1, v2, v3 Verified True
- **Obfuscation**: R8 Release Minification Active
- **Integrity**: SHA-256 Release Keystore Binding Active
- **Latest Release**: [v1.0.0-admob](https://github.com/rawatriya7514-lab/artraw-files/releases/tag/v1.0.0-admob)

---

## Legal & Intellectual Property Notice
Copyright © 2026 ARTRAW. All Rights Reserved.  
This software and associated documentation files are proprietary and confidential. Decompilation, disassembly, reverse engineering, unauthorized extraction of source code or assets, tampering, redistribution, or creation of derivative works without prior written permission is strictly prohibited.
