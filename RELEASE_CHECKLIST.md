# ARTFILES — RELEASE CHECKLIST (v1.2.1 → FINAL)

Status key: ✅ VERIFIED | ❌ FAILED | ⚠️ BLOCKED | ⏳ NOT TESTED

## CRITICAL — Launch / Crash Issues

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| C1 | App launches (SplashActivity → MainActivity/PermissionActivity) | ⏳ NOT TESTED | Code compiles, no runtime test on device |
| C2 | Storage permission grant flow (PermissionActivity) | ⏳ NOT TESTED | Code written, not device-tested |
| C3 | Storage permission denial handling | ⏳ NOT TESTED | Code handles `isExternalStorageManager() == false` |
| C4 | Return from system settings auto-verifies permission | ⏳ NOT TESTED | PermissionActivity polls every 2s |
| C5 | App survives permission revocation | ⏳ NOT TESTED | Not tested |

## STORAGE ACCESS

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| S1 | Browse internal storage (/storage/emulated/0) | ⏳ NOT TESTED | FileRepository.getPrimaryStorageRoot() |
| S2 | List files and folders | ⏳ NOT TESTED | FileRepository.listFiles() |
| S3 | Navigate subdirectories | ⏳ NOT TESTED | BrowserActivity.currentDirectory |
| S4 | Empty folder state | ⏳ NOT TESTED | layoutEmptyState in layouts |
| S5 | Inaccessible/restricted folders (Android/data) | ⏳ NOT TESTED | PathSecurity.isAndroidRestrictedStorage() |

## THUMBNAILS & ICONS

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| T1 | Image thumbnails display | ⏳ NOT TESTED | ThumbnailLoader.decodeSampledBitmap |
| T2 | Video thumbnails display | ⏳ NOT TESTED | ThumbnailLoader.decodeVideoThumbnail |
| T3 | APK icons extract from package manager | ⏳ NOT TESTED | ThumbnailLoader.decodeApkIcon |
| T4 | Document/audio/archive fallback icons | ⏳ NOT TESTED | getFallbackIconRes() |
| T5 | Encrypted files show lock icon | ⏳ NOT TESTED | isEncrypted check in ThumbnailLoader |
| T6 | Directories show folder icon | ⏳ NOT TESTED | isDirectory check in ThumbnailLoader |

## FILE OPERATIONS

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| F1 | Copy files | ⏳ NOT TESTED | FileOperationManager.executePaste(COPY) |
| F2 | Cut/Move files | ⏳ NOT TESTED | FileOperationManager.executePaste(CUT) |
| F3 | Paste with conflict resolution | ⏳ NOT TESTED | ConflictMode REPLACE/KEEP_BOTH/SKIP/CANCEL |
| F4 | Rename files | ⏳ NOT TESTED | FileOperationManager.renameFile() |
| F5 | Delete files (permanent) | ⏳ NOT TESTED | FileOperationManager.deleteFiles() |
| F6 | Move to Trash (soft delete) | ⏳ NOT TESTED | TrashManager.moveToTrash() |
| F7 | Restore from Trash | ⏳ NOT TESTED | TrashManager.restoreItem() |
| F8 | Empty Trash | ⏳ NOT TESTED | TrashManager.emptyTrash() |
| F9 | Share files | ⏳ NOT TESTED | FileViewerHelper.shareFiles() |
| F10 | Multi-selection | ⏳ NOT TESTED | FileListAdapter selection mode |

## NAVIGATION & UI

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| N1 | Bottom navigation (Home/Browse/Analysis/Settings) | ⏳ NOT TESTED | All activities declared in manifest |
| N2 | Category browsing (Images/Videos/Audio/Docs/etc.) | ⏳ NOT TESTED | CategoryActivity + getCategoryFiles() |
| N3 | Search functionality | ⏳ NOT TESTED | BrowserActivity search bar |
| N4 | Sort files | ⏳ NOT TESTED | SortOption enum + FileRepository.sortWith |
| N5 | Loading states | ⏳ NOT TESTED | ProgressBar in layouts |
| N6 | Empty states | ⏳ NOT TESTED | layoutEmptyState in layouts |
| N7 | Error states | ⏳ NOT TESTED | Toast messages in activities |

## ANDROID COMPATIBILITY

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| A1 | Min SDK 21 compatibility | ⏳ NOT TESTED | manifest minSdkVersion="21" |
| A2 | Target SDK 35 (Android 15) | ⏳ NOT TESTED | manifest targetSdkVersion="35" |
| A3 | MANAGE_EXTERNAL_STORAGE permission | ⏳ NOT TESTED | Declared in manifest |
| A4 | READ_MEDIA_* permissions (API 33+) | ⏳ NOT TESTED | Declared in manifest |

## BUILD & RELEASE

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| B1 | Build compiles without errors | ⏳ NOT TESTED | build.sh runs, needs re-run |
| B2 | 18/18 unit tests pass | ❌ NOT RUN | Last run showed OK (18 tests) |
| B3 | APK signed with consistent keystore | ⏳ NOT TESTED | Keystore exists, needs verification |
| B4 | APK v1+v2+v3 signature verification | ⏳ NOT TESTED | apksigner available |
| B5 | Single production APK (no duplicates) | ❌ FAILED | build.sh produces 3 artifacts |
| B6 | Consistent versionCode/versionName | ✅ VERIFIED | 3 / 1.2.0 in build.gradle.kts |
| B7 | Git tag created | ❌ FAILED | No tags exist |
| B8 | GitHub release published | ❌ FAILED | No releases exist |

## KNOWN LIMITATIONS (NOT BLOCKERS)

| # | Limitation |
|---|-----------|
| L1 | Cannot test on-device (no physical device/emulator available) |
| L2 | AdMob uses test App ID (not production) — ads won't show real ads |
| L3 | R8 minification disabled in release build |
| L4 | Cannot verify GitHub release from this environment without network |

---
