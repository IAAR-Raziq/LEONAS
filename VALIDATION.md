# Validation · 16 October 2026

The current checkout built successfully in this cloud machine with Temurin JDK 17.0.16, Gradle 8.13, AGP 8.13.2, SDK Platform 36 and Build Tools 35.0.0. The machine initially had a Java runtime without a compiler; a checksum-verified JDK was installed. SDK, Gradle, Robolectric and test caches were placed in writable directories. TLS/artifact verification remained enabled.

## Completed

- `assembleDebug`: passed. Native Android APK created at `app/build/outputs/apk/debug/app-debug.apk` (package `com.leanos.app`, version 1.0.0, min SDK 26, target SDK 36).
- `testDebugUnitTest`: **12 passed, 0 failed, 0 skipped**: 7 calculation/validation tests, 4 Room persistence/CRUD/backup/activity tests, 1 Compose application flow test.
- Compose flow ran with Robolectric API 35 and native graphics: three-step onboarding, meal entry/save, Meals and Progress navigation, Home return, Activity recreation with the profile retained. Database tests ran on Robolectric API 28, including close/reopen persistence and an invalid import leaving existing data intact.
- `lintDebug`: passed with no errors. Remaining warnings are dependency update notices and Kotlin convenience API suggestions; no lint assertions were disabled or baselined.
- `apksigner verify --verbose`: passed, one signer, APK Signature Scheme v2. This is a debug-signed build.
- `zipalign -c -P 16 4`: passed (including 16 KB ZIP alignment for native libraries).
- Backend `npm test`: **6 passed, 0 failed, 0 skipped**. Real local HTTP requests exercise bearer authentication, valid/invalid image decoding, mode validation and rate limiting. Gemini transport is mocked to test structured requests, invalid/truncated results, quota and network errors.
- Backend `npm audit --omit=dev --audit-level=high`: **0 known vulnerabilities** at validation time, after updating flagged upload/image/rate-limit dependencies. Backend tests ran on Node 24.19.0; Docker is configured for Node 22.

APK SHA-256: `e4f3d9c8965dd3c4f878ed418952fb04b05092ca37cd01f26d64bd5726bd5540`

## Remaining deployment and device checks

- Live Gemini inference was **not run**: no Gemini API key or deployed HTTPS scanner endpoint was provided. Deploy `backend/`, configure its server-side key and client-token hash, then enter the endpoint and client token in Android Profile. The rest of the app runs offline.
- HTTPS/Caddy deployment and Google model/key/quota availability have not been validated on a production server. The provided deployment is for one personal user and one replica.
- No physical Android device or hardware emulator was attached. Camera intent behavior, OEM Photo Picker fallback, crop UI, Keystore-backed scanner credentials, system document import/export and network scanning still need a device smoke check. Automated Room backup/import and native-graphics Compose tests do not replace those checks.
- No release signing, Play Store publication, cloud authentication, food-composition database or Health Connect integration was performed. Manual steps and manual nutrition entry are supported. There are no demo records.

## Device smoke checklist

Install the debug APK; confirm your profile defaults; save partial habits and a past-date record; add/edit/delete a meal and check its budget cost updates once; log/edit a weigh-in and workout session; switch themes; export a backup, import it after confirmation, and restart the app. After configuring the optional backend, test camera/gallery, crop/retake, food and nutrition-label modes, component corrections, portion confirmation, Save/Cancel, an offline request and an invalid token. Export any data you want to retain before trying Delete all personal data.
