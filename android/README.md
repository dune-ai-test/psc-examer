# Padanam · Kerala PSC quiz app

Padanam turns photographs of printed PSC question papers into a private,
offline-first quiz library. It is a single-user Android app built with Kotlin
and Jetpack Compose, following the visual language of the thirteen HTML
mockups in the repository root.

The app itself lives entirely in this `android/` folder.

## What it does

- **Scan or import questions** — CameraX viewfinder with single-page, multi-page
  and batch-import modes (up to 8 photos per set).
- **AI extraction** — Gemini reads English and Malayalam text, groups each stem
  with options A–D, infers the correct answer and writes a short explanation in
  both languages. The app validates and normalises the response before saving.
- **Bilingual quiz** — every question carries English and Malayalam text. A
  language switch on the set, exam, result and review screens changes the
  question presentation without changing the app chrome.
- **Practice and mock exams** — choose question count and duration, resume an
  active exam after leaving the app, or finish early with confirmation.
- **Saved scores** — every attempt is written to Room with a self-contained
  answer snapshot; with auto-sync on it is mirrored to Neon as soon as you
  submit, otherwise via **Settings → Sync now**. Results show score, pass chip,
  correct / wrong / skipped breakdown, time and per-question review.
- **Offline first** — Room is the source of truth. Scanning needs the network
  for Gemini; everything already saved works without it. Neon sync is optional:
  automatic after a scan or submitted attempt when enabled, otherwise manual.
- **Extras beyond the mockups** — progress analytics, attempt history, editable
  profile, reminders, JSON export/share, demo set, connection tests and full
  settings for AI and database credentials.

## Screens

| Mockup | Screen | Notes |
| --- | --- | --- |
| 01 / 12 / 13 | Home | Active exam, daily goal, quick actions, recent sets, attempt strip |
| 02 / 11 | Library | Search, subject filters, empty state, set cards |
| 03 | Progress | Score trend, accuracy, streak, recent attempts |
| 04 | Profile | Editable name, goal, language, history, export, settings entry |
| 05 | Scan camera | Live CameraX preview, flash, lens flip, SINGLE / MULTI / BATCH |
| 06 | Analysing | File card, percentage, stage checklist, retry/settings/cancel |
| 07 | Scan result | Saved banner, confidence chips, correct options, JSON proof, actions |
| 08 | Exam setup | Practice/mock segmented control, count and duration chips |
| 09 | Question | Custom timer/counter, palette, flags, leave and submit dialogs |
| 10 | Results | Pass/fail hero, breakdown, stat tiles, share and review |
| — | Set detail | Hero, stats, language switch, question previews, attempts, rename/delete |
| — | Answer review | Filters, stored answer cards, correct/selected visuals, explanations |
| — | Settings | Gemini and Neon configuration, sync, notifications, export, reset |

## Tech stack

| Layer | Choice |
| --- | --- |
| Language / UI | Kotlin 2.0.21, Jetpack Compose, Material 3 |
| Build | AGP 8.7.3, min SDK 26, target/compile SDK 35 |
| Persistence | Room 2.6.1, DataStore Preferences |
| Networking | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization |
| Images | CameraX 1.4.1, Coil 2.7, ExifInterface |
| Background | WorkManager 2.10 (study reminders) |
| AI | Gemini REST `generateContent`, default `gemini-2.5-flash` |
| Cloud | Neon Postgres over its HTTP `/sql` endpoint |

Jetpack Navigation Compose owns the graph; ViewModels are created with manual
factories from `AppContainer`. There is no DI framework, no Hilt, and no
generated navigation graph, which keeps the project easy to read and modify.

## Getting started

### Requirements

- Android Studio Ladybug or newer, JDK 17
- Android SDK Platform 35 and build tools for AGP 8.7.3
- A physical device or emulator running Android 8.0 (API 26) or newer
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/)
- Optional: a Neon Postgres database for cross-device backup

### 1. Configure secrets

Create `android/local.properties` (it is ignored by git) and fill in:

```properties
GEMINI_API_KEY=your_google_ai_studio_key
GEMINI_MODEL=gemini-2.5-flash
NEON_CONNECTION_STRING=postgresql://user:password@ep-xxx.region.aws.neon.tech/dbname?sslmode=require
# Optional. Leave empty to derive https://api.<region>/sql automatically.
NEON_HTTP_ENDPOINT=
```

`local.properties.example` is the template. The same values can be entered at
runtime in **Profile → Settings**; runtime values override the build defaults,
so you can install one APK and still change keys without rebuilding.

If `NEON_HTTP_ENDPOINT` is blank, the endpoint is derived from the connection
string host by replacing its first label with `api` (for example
`ep-cool-name.eu-central-1.aws.neon.tech` becomes
`https://api.eu-central-1.aws.neon.tech/sql`). Fill it manually if your Neon
project uses a different host pattern.

### 2. Prepare Neon (optional)

The app calls `CREATE TABLE IF NOT EXISTS` before its first sync, so manual
setup is optional. To do it yourself, run
[`neon/schema.sql`](neon/schema.sql) once in the Neon SQL editor.

### 3. Build and install

Open the `android/` folder in Android Studio and press Run, or use a terminal:

```bash
cd android
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

This repository contains source only; it was authored without a local Android
SDK, so the first Gradle sync in Android Studio is the intended build step.

## How a scan works

1. **Capture** — CameraX writes a full-size JPEG to `cache/scan_inbox`.
   `SINGLE` proceeds immediately, `MULTI` collects up to eight pages, and
   `BATCH` opens the system photo picker and treats the selection as one set.
2. **Compress** — `ImageUtils.compressForUpload` downsamples to at most
   1600 px, applies EXIF rotation and re-encodes JPEG at quality 86.
3. **Extract** — `GeminiRepository` sends all pages inline with a JSON response
   schema and a carefully constrained system prompt. Gemini returns bilingual
   stems, four bilingual options, a correct letter, explanations, topic,
   difficulty and confidence.
4. **Validate** — questions without both a stem and four options are dropped,
   letters are normalised to A–D, blank Malayalam falls back to English, and
   confidence is clamped. An empty result becomes a friendly retry message.
5. **Save** — `LibraryRepository.saveScan` writes the set and every question to
   Room before the success screen appears. Titles get a `(2)`, `(3)` suffix if
   they collide.
6. **Confirm** — the result screen shows the saved banner, confidence chips,
   the correct option, an explanation and a JSON block proving the stored
   answer. From here you can practice, open the set or scan again.

The model never writes to the database directly. The app maps its response
onto local entities, so the library format stays stable even if the prompt or
model changes.

## Offline and sync behaviour

- Every set, question and attempt is stored in Room first.
- An active exam is kept in DataStore with an absolute `startedAtEpochMs`, so
  the countdown keeps running while the app is backgrounded.
- Attempts embed a snapshot of each question, its options, the selected answer,
  the correct answer and the explanation. Deleting a set never breaks history.
- Sync pushes rows where `synced = 0`, marks them clean, then pulls the complete
  remote library with insert-or-ignore semantics. It runs when auto-sync is on
  (after saving a scan or submitting an attempt) or when you tap
  **Settings → Sync now**; no background timers write to Neon.
- Deleted set ids are kept as DataStore tombstones until the next successful
  sync, preventing deleted sets from being resurrected by a pull.
- If Gemini or Neon is unreachable, the UI reports a readable error and keeps
  all local data intact.

## Bilingual design

English and Malayalam are stored separately for stems, options and
explanations. The UI language toggles question content only; navigation,
labels and buttons stay in English, matching the mockups. Malayalam text uses
Noto Sans Malayalam (`MlType`) so conjuncts render correctly, while English
uses DM Sans and Bricolage Grotesque.

## Design system

The mockups' tokens are centralised in `ui/theme`:

- Background `#F2F4F9`, ink `#12172B`, muted `#5D6478`
- Indigo `#5B4BF6`, soft indigo `#E9E7FE`
- Success `#0F9E7E`, amber `#F59E0B`, hairline `#E6E8F0`
- Dark cards `#1E2544 → #12172B` with a radial indigo glow
- DM Sans for body, Bricolage Grotesque for titles, IBM Plex Mono for labels

A custom 62 dp status bar (real time, faux Dynamic Island, signal, Wi-Fi,
battery) replaces the system bar on every screen so the app matches the
iPhone-framed mockups.

## Project layout

```
android/
├─ app/src/main/java/com/padanam/psc/
│  ├─ AppContainer.kt            manual dependency container
│  ├─ MainActivity.kt            edge-to-edge Compose host
│  ├─ PadanamApp.kt              Application, notification channel, reminders
│  ├─ data/
│  │  ├─ local/                  Room entities, DAO, database
│  │  ├─ model/                  AI models, active exam, answer records
│  │  ├─ prefs/                  settings and active-exam DataStores
│  │  ├─ remote/                 Gemini + Neon HTTP clients
│  │  └─ repo/                   library, attempts, sync repositories
│  ├─ ui/
│  │  ├─ components/             shared chrome, cards, question visuals
│  │  ├─ theme/                  colours, typography, Material theme
│  │  ├─ home/ library/ progress/ profile/ settings/ setdetail/
│  │  ├─ exam/ quiz/             setup, question runner, results, review
│  │  ├─ scan/                   camera, analysing, scan result
│  │  ├─ AppViewModel.kt         shared library, settings and sync state
│  │  └─ PadanamRoot.kt          navigation graph
│  ├─ util/                      JSON, image, format, scan-session helpers
│  └─ work/                      reminder worker and scheduler
├─ app/src/main/res/             fonts, icons, themes, FileProvider paths
├─ neon/schema.sql               optional manual Neon bootstrap
└─ local.properties.example      secret template
```

## Installing the APK

1. Enable **Install unknown apps** for your file manager or browser.
2. Copy `app-debug.apk` to the device and open it.
3. On first launch, open **Settings**, paste the Gemini key and optionally the
   Neon connection string, then use **Test** on each card.
4. Scan a page. If the photo is sharp and the four options are visible, the
   question is saved in a few seconds.

## Privacy and security

- Photographs stay in the app cache and are sent only to Gemini for extraction.
- Neon stores the full library and every attempt for the single configured user.
- **Secrets are compiled into the APK when supplied through
  `local.properties`.** Anyone with the APK can extract them. This build is for
  personal use only. For distribution, put Gemini and Neon behind a small proxy
  or serverless function and ship no credentials; prefer Neon Auth or a scoped
  proxy over embedding a direct Postgres connection string.
- Keep `local.properties` out of version control; the included `.gitignore`
  already does this.
- Neon rows are not encrypted by the app. Use Neon's at-rest encryption and a
  dedicated database role limited to the three tables above.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| "Add your Gemini API key" | Paste a key in Settings or rebuild with `GEMINI_API_KEY` set. |
| Gemini 404 | Use a model your key can access, normally `gemini-2.5-flash`. |
| Gemini 429 | Wait a minute; the free tier has per-minute limits. |
| Camera preview is black | Grant camera permission, or use **Import photos**. Emulators often need a virtual scene camera. |
| "No complete questions were found" | Crop tighter, improve lighting, keep all four options in frame. |
| Neon "relation does not exist" | Run `neon/schema.sql` once in the Neon SQL editor. |
| Neon connection fails | Check the full connection string includes the database and `sslmode=require`; set `NEON_HTTP_ENDPOINT` manually if host derivation fails. |
| Scores missing on a new phone | Install, configure the same Neon database, then **Settings → Sync now**. |
| Reminders never appear | Allow notifications on Android 13+ and enable reminders in Profile. |

## Manual test checklist

- [ ] Complete onboarding by editing the profile name and daily goal.
- [ ] Seed the demo set; start practice and confirm the score is saved.
- [ ] Scan one clear English page; verify both languages and the correct option.
- [ ] Scan a Malayalam page; switch language on the result and during review.
- [ ] Start a mock exam, background the app for a minute, return and confirm the
      timer kept counting.
- [ ] Force-stop mid-exam; reopen and resume from the home banner.
- [ ] Delete a set and confirm its attempts still open from Progress.
- [ ] Export the library and open the JSON from another app.
- [ ] Configure Neon, sync, then check the row counts in the Neon SQL editor.
- [ ] Enable airplane mode and confirm saved sets and reviews still work.
