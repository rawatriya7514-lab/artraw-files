# ARTRAW Files — Premium Native Android File Manager

**Package ID**: `com.artraw.files`  
**Target SDK**: Android 16 (API Level 36 / 35 Compatibility)  
**Architecture**: Native Kotlin + Android SDK + Material 3 Design  
**License**: Private & Open-Source Friendly (100% Offline, Zero Telemetry)

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

## Build Instructions

### Prerequisites (Termux or Linux aarch64)
- `openjdk-21`
- `kotlin` (v2.4+)
- `aapt2`
- `d8`
- `apksigner`
- `python3`

### Run Single-Command Build & Verification
```bash
cd artraw-files
./build.sh
```

---

## Downloads & Releases

Direct production downloads with real AdMob integration:
- **Production APK (Direct Android Install)**: [ARTRAW-Files-Production.apk](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.0.0-admob/ARTRAW-Files-Production.apk)
- **Production AAB (Google Play Store Bundle)**: [ARTRAW-Files-Production.aab](https://github.com/rawatriya7514-lab/artraw-files/releases/download/v1.0.0-admob/ARTRAW-Files-Production.aab)

---

## Test & Verification Summary
- **JUnit Test Runner**: 12/12 Tests PASSED
- **APK Signatures**: v1, v2, v3 Verified True
- **Latest Release**: [v1.0.0-admob](https://github.com/rawatriya7514-lab/artraw-files/releases/tag/v1.0.0-admob)
