# LEANOS

A native, offline-first Android companion for a personal fat-loss journey. Kotlin, Jetpack Compose / Material 3, MVVM, Hilt, Room, DataStore, Navigation Compose, Coroutines / Flow, and Retrofit. Android 8.0+ (API 26), compiling and targeting Android 16 (API 36) with AGP 8.13.2.

**Your body. Your progress. Your rules.**

## Download the Android app

Open [LEANOS-debug.apk](downloads/LEANOS-debug.apk) on GitHub and use **Download raw file** (the download icon). On Android, open the downloaded APK and allow installation from your browser/file manager if prompted. Requires Android 8.0 or later. This is a debug-signed build; AI scanning requires the optional backend setup below.

APK integrity: [SHA256SUMS](downloads/SHA256SUMS).

## Version 1.1 · floating glass navigation

The bottom navigation now floats above the system gesture area in a rounded, translucent capsule. Layered highlights, a thin glass rim and soft shadow adapt to dark/light themes. Animated lime selection, persistent labels, accessible tab semantics, generous touch targets and a centered maximum width improve navigation on phones and tablets. Tapping the current tab preserves the screen; photo scanning hides the bar for focused review. Content and snackbars stay clear of the navigation area. This is a glass-style surface rather than a device-dependent backdrop blur.

Install the newer APK over version 1.0 to retain your data; the app ID, signing key and Room schema remain unchanged.

## Build and install

Open this directory in Android Studio, select a JDK 17 installation, and install Android SDK Platform 36 and SDK Build Tools 35.0.0 (AGP's default). Android Studio creates ignored `local.properties`; for CLI builds set `ANDROID_HOME` to your SDK or put `sdk.dir=/your/android-sdk` in that file. The Gradle wrapper is pinned to 8.13 with distribution SHA-256 verification.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK is a development build, signed with a generated debug key. Use your own protected release signing key for distribution. No Gemini key or deployment token is bundled in the APK.

On this cloud machine the JDK, SDK and caches are under `/workspace/tooling`. To rerun checks here:

```sh
export JAVA_HOME=/workspace/tooling/jdk17
export ANDROID_USER_HOME=/workspace/tooling/android-user
export GRADLE_USER_HOME=/workspace/tooling/gradle-home
./gradlew assembleDebug testDebugUnitTest lintDebug \
  -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 \
  -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
```

These proxy/trust-store paths are specific to this cloud machine; normal Android Studio builds do not need them. Runtime caches are redirected because the machine's home directory is read-only. This checkout is already isolated; no Git worktree is needed.

## Use the app

1. Complete three-step onboarding. Editable defaults: age 19, height 169 cm, starting/current weight 110 kg, goal 80 kg, food budget RM350 per 30 days, Malaysia/MYR, brunch around 10 AM and dinner 6–8 PM, walking and beginner strength, no cooking or refrigerator. Your nickname is required; no login exists.
2. **Home** shows only saved measurements, today's habit and meal totals, challenge completion, budget and a weekly weight chart. Empty states stay empty until you log data. Initial weight comes from the value you confirm during onboarding.
3. **Track** saves partial daily entries, sleep, water, energy, notes and rest days. Load any past date to edit. Meals and known protein totals are derived from meal records. Add walking, strength or other sessions; editing/deleting sessions adjusts daily duration totals transactionally. Manual walking/exercise fields represent the day's total, including sessions, rather than extra minutes. Beginner movement guidance is provided.
4. **Meals** supports add/edit/delete, date/time, category, RM cost (including free food), ingredients/portion notes and optional nutrition. Calories are never required. Weekly summaries explicitly report incomplete nutrition coverage.
5. **Progress** has editable/deletable weight/waist measurements, selectable 7/30/90-day or all-time charts, tap-to-inspect values, monthly summaries and history. One weight and one habit entry per date; saving the same date updates it. Changing a weigh-in date requires deleting/recreating to avoid silently overwriting another measurement. Editing current weight in Profile records a weigh-in for today when changed.
6. **Budget** opens from Home. Meal prices feed it directly; no duplicated expense row is generated. Other food expenses have independent edit/delete controls. Do not log the same purchase twice. Budget alerts encourage adjusting spending/budget, never missing meals.
7. **Profile** edits all personal details/targets and dark/light/system theme. JSON export/import uses Android's system document picker; import requires confirmation and replaces records only after complete validation inside a database transaction. Delete all data removes records, preferences, scanner credentials and cached photos. No demo records are seeded, so there is no demo reset control.

### Calculations and data choices

- Budget periods are consecutive 30-day windows anchored to onboarding date, not calendar months. The remaining days include today. Daily allowance = max(0, remaining budget) / remaining days. An overspend is still shown as a negative balance. Spending is summed in integer sen.
- Goal progress = `(starting − latest recorded weight) / (starting − goal)`, clamped to 0–100%. No target date or guaranteed loss is calculated.
- Seven habits contribute equally: step goal, walking/exercise or rest, water goal, sleep goal, meal logged, known protein goal, no sugary drinks. A full habit streak counts consecutive complete days and permits today to remain unfinished. The 30-day challenge starts at onboarding. Past dates are editable.
- Nutrition totals include only values supplied by the user or confirmed AI estimates; missing values are not evidence of zero nutrition. The UI reports calorie coverage. For AI meals, the saved calorie value initially uses the midpoint of the displayed range, with range/uncertainty retained in notes; the user can change everything.
- Manual steps are the supported activity source. Health Connect is not enabled; no activity permissions are requested.
- Room schema version 1 is exported in `app/schemas/`. There are no pre-v1 installations to migrate. Destructive fallback is **not** enabled. Future schema updates must increment the version, supply explicit Room migrations and add migration tests; never erase records to make an update pass.
- Room lives in Android app-private storage with OS file encryption. It is not an independently encrypted SQLCipher database. Automatic Android backups are disabled; explicit user exports are readable JSON containing sensitive personal data. Preferences contain theme/backend URL only. Scanner access credentials are AES-GCM encrypted with an Android Keystore key.

## Optional Gemini food scanner

Offline tracking needs no server. Scanning requires your own deployed backend, a server-side Gemini API key and a client access token. Camera capture uses the installed system camera app; gallery selection uses Android Photo Picker (with platform fallback). No broad photo-library permission is requested. Preview, crop/rotate, retake, food/label modes, structured analysis, ingredient/portion correction and explicit save are implemented. Camera EXIF orientation is normalized and images are downsampled before upload.

The Android app sends `POST /analyze` as multipart `image` (JPEG) and `mode` (`food` or `label`), authenticated by `Authorization: Bearer <client-token>`. The backend calls the official Gemini `v1beta/models/{model}:generateContent` endpoint with inline image data and JSON response schema. Defaults to `gemini-2.5-flash`; set `GEMINI_MODEL` to a vision/structured-output capable model available to your account if needed.

Responses identify components, likely ingredients, serving description, gram/calorie ranges, approximate macros/fibre, cooking method, sauces, confidence and uncertainty. Both server and Android validate the result. No database source is invented. The scanner does not retrieve a food composition database. Label mode asks for visible nutrition and serving basis; illegible labels should be retried or logged manually. Review is essential: image estimates and model transcriptions can be wrong. AI never writes a meal without a separate Save action.

Timeouts, authorization failures, network problems, quota limits, malformed responses and unavailable analysis leave offline features usable. Photos are held transiently in server memory, re-encoded to remove metadata, and not logged or persisted by the backend. Google receives submitted images; review Google's current Gemini API data-use terms before submitting sensitive content.

### Deploy the private backend

Requirements: Node 22+ (or Docker + Compose), a server/domain you control, and an enabled Gemini API key. Do not send the Gemini key to chat or put it in source control.

```sh
cd backend
npm ci
npm test
cp .env.example .env
chmod 600 .env
node create-client-token.js
```

The last command creates `.client-token` (mode 0600) and prints only its SHA-256 digest. Put that digest into `.env` as `CLIENT_TOKEN_SHA256`, set `GEMINI_API_KEY` securely on the server, and set `SCANNER_DOMAIN` to your real DNS hostname. Read `.client-token` privately to copy it into Android Profile → client access token; it is a separate, revocable credential and is not the Gemini key. Store it in a password manager, then remove the server copy if desired. Regenerating the token and updating its hash revokes old access; restart the server and replace the token in Android.

For the provided deployment, point the domain's DNS at your server and allow inbound TCP 80/443. Then:

```sh
docker compose up -d --build
curl --fail https://YOUR_DOMAIN/health
```

Caddy obtains/renews TLS certificates. Only Caddy publishes ports; the scanner remains on the private Compose network. Enter `https://YOUR_DOMAIN` in Android Profile and save the client token. No HTTP or custom certificate-trust bypass is supported by the Android client. Do not expose port 8080 directly. `TRUST_PROXY=1` is safe only with exactly one controlled reverse proxy in front of the private backend; leave unset for direct local Node tests.

For another host, `npm start` loads `.env`; bind it behind your host's HTTPS reverse proxy. `/health` reports process availability, not Gemini readiness. Test a real food photo from the app to confirm model/key/quota access. Configure Google project quotas and billing alerts. Deploy a **single replica**: rate counters are in memory (30 requests/IP/minute and 10 authorized scans/minute across the private token), reset on restart, and are not distributed. Multi-user/public deployment needs per-user authentication, durable shared rate limiting and an appropriate privacy policy. There are no public registrations or fake logins.

### Tests

`./gradlew testDebugUnitTest` runs calculation and AI validation tests, Room meal CRUD / onboarding reopen / backup atomicity / activity edit tests, and a Robolectric Compose onboarding → meal logging → navigation → Activity recreation flow. Robolectric downloads its signed/checksummed Android runtime artifacts from Maven Central on first run. `./gradlew lintDebug` checks Android issues; `assembleDebug` makes the APK.

`cd backend && npm test` tests schema validation, the real HTTP proxy with valid/invalid images, auth/rate limits, request construction and upstream error handling. These tests mock Gemini responses. A live Gemini scan requires deployment and your server-side key and is not implied by passing the automated tests.

See `VALIDATION.md` for the checks actually completed in this environment and remaining device/deployment checks.
