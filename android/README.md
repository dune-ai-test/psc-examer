# Padanam · Kerala PSC quiz app

Padanam turns photographs of printed PSC question papers into a private,
offline-first quiz library. The Android app uses Kotlin and Jetpack Compose and
is the companion to the Padanam web app in `web/`.

Accounts, the question library and every attempt live in the same Neon
workspace as the website. The app itself carries **no Gemini key and no database
password**; every privileged call goes through the deployed Cloudflare Pages
functions (`/api/auth`, `/api/admin`, `/api/sync`, `/api/scan`).

## What it does

- **Sign in once** — the same admin-created username and password work on the
  web and on Android. The admin can also open a console inside the app.
- **Scan or import questions** — administrators capture up to 8 pages, review the
  bilingual draft, then save it as a new set or append it to an existing topic.
- **Bilingual quiz** — every question carries English and Malayalam text. The
  language switch changes question content only.
- **Practice and mock exams** — choose question count and duration, resume an
  active exam after leaving the app, or finish early with confirmation.
- **Saved scores** — every attempt is written to Room with a self-contained
  answer snapshot and synced to the account workspace when auto-sync is on.
- **Offline first** — Room is the source of truth. A signed-in session keeps
  working offline; the next sync merges both sides by timestamp.
- **Library assignment** — an admin assigns sets from the shared cloud library
  to a learner. The learner receives a copy in their own workspace; their
  attempts never change the admin's library.
- **Reminders and profile** — name, daily goal, language and notification
  preferences sync with the same account.

## Roles

| Role | Can do |
| --- | --- |
| Administrator | Scan/import sets, append questions, rename sets, export JSON, sync the admin library, create learners, reset learner passwords, assign sets |
| Learner | Practise, take mock exams, review attempts, edit their profile; scanning and library edits are hidden |

The administrator is bootstrapped from `ADMIN_USERNAME` / `ADMIN_PASSWORD` on
the server. Learner accounts are created in the admin console.

## Screens

| Screen | Notes |
| --- | --- |
| Login / loading | Site address, username, password; session restore on launch |
| Home | Active exam, daily goal, quick actions, recent sets, attempt strip |
| Library | Search, subject filters, empty state, set cards |
| Progress | Score trend, accuracy, streak, recent attempts |
| Profile | Editable name, goal, language, history, admin entry, settings |
| Scan camera | Live CameraX preview, flash, lens flip, SINGLE / MULTI / BATCH |
| Analysing / review | File card, percentage, stage checklist, draft preview, new-set or append destination picker |
| Scan result | Saved banner, confidence chips, correct options, actions |
| Exam setup | Practice/mock segmented control, count and duration chips |
| Question | Custom timer/counter, palette, flags, leave and submit dialogs |
| Results | Pass/fail hero, breakdown, stat tiles, share and review |
| Set detail | Hero, stats, language switch, question previews, attempts |
| Answer review | Filters, stored answer cards, correct/selected visuals |
| Admin console | Learners, create account, sync admin library, reset learner password, assign sets |
| Settings | Account, site address, cloud sync, sign out |

## Tech stack

| Layer | Choice |
| --- | --- |
| Language / UI | Kotlin 2.0.21, Jetpack Compose, Material 3 |
| Build | AGP 8.7.3, min SDK 26, target/compile SDK 35 |
| Persistence | Room 2.6.1 (offline cache), DataStore Preferences (session/settings) |
| Networking | OkHttp 4.12 + kotlinx.serialization |
| Images | CameraX 1.4.1, Coil 2.7, ExifInterface |
| Background | WorkManager 2.10 (study reminders) |
| Cloud | Cloudflare Pages Functions in front of Neon and the scan provider |

Jetpack Navigation Compose owns the graph; ViewModels are created with manual
factories from `AppContainer`. There is no DI framework and no generated
navigation graph.

## Getting started

### Requirements

- Android Studio Ladybug or newer, JDK 17
- Android SDK Platform 35 and build tools for AGP 8.7.3
- A physical device or emulator running Android 8.0 (API 26) or newer
- A deployed Padanam site with `AUTH_SECRET`, `ADMIN_USERNAME`,
  `ADMIN_PASSWORD` and `NEON_CONNECTION_STRING` configured in Cloudflare Pages.
  Scan secrets (`GEMINI_API_KEY` or the custom OpenAI-compatible values) are
  optional; when they are missing the scan screen shows a clear server message.

### Build and install

Open the `android/` folder in Android Studio and press Run, or push to the
`web` branch and let GitHub Actions publish a debug APK to Releases. Locally:

```bash
cd android
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### First launch

1. Enter the deployed site address, for example `https://your-site.pages.dev`.
2. Sign in with the administrator account or a learner account created by the
   administrator.
3. Press **Sync now** in Settings to pull the account library.

No `local.properties` secrets are needed. The app builds with no keys at all.

## How a scan works

1. **Capture** — CameraX writes a full-size JPEG to `cache/scan_inbox`.
   `SINGLE` proceeds immediately, `MULTI` collects up to eight pages, and
   `BATCH` opens the system photo picker and treats the selection as one set.
2. **Compress** — `ImageUtils.compressForUpload` downsamples to at most
   1600 px, applies EXIF rotation and re-encodes JPEG at quality 86.
3. **Extract** — `ScanRepository` posts the images to `/api/scan`. The server
   holds the AI key and applies the same bilingual extraction prompt as the web
   app.
4. **Validate** — questions without both a stem and four options are dropped,
   letters are normalised to A–D, blank Malayalam falls back to English, and
   confidence is clamped. An empty result becomes a friendly retry message.
5. **Review** — extraction stops at a draft. Nothing is written yet; the review
   card previews the first questions and lists every existing set as an append
   destination alongside **Create a new set**.
6. **Save** — `LibraryRepository.saveScan` writes a new set, or `appendScan`
   updates the chosen set in place. Appending keeps its title, subject and
   previous questions, gives the new questions fresh IDs and bumps `updatedAt`.
   New titles get a `(2)`, `(3)` suffix if they collide.
7. **Sync** — when auto-sync is on, the saved set is pushed to the signed-in
   workspace. The admin can then assign it to a learner from the admin console.

## Offline and sync behaviour

- Every set, question and attempt is stored in Room first.
- An active exam is kept in DataStore with an absolute `startedAtEpochMs`, so
  the countdown keeps running while the app is backgrounded.
- Attempts embed a snapshot of each question, its options, the selected answer,
  the correct answer and the explanation.
- Sync pulls the account workspace, merges newer sets and new attempts, then
  pushes the union. Set timestamps decide the winner.
- The sync payload matches the web JSON exactly, so `padanam_sets`,
  `padanam_attempts` and `padanam_profile` are shared between web and Android.
- Signing out clears the offline cache so the next account cannot read the
  previous learner's library.
- If the server is unreachable, the UI reports a readable error and keeps all
  local data intact.

## Bilingual design

English and Malayalam are stored separately for stems, options and
explanations. The UI language toggles question content only; navigation,
labels and buttons stay in English. Malayalam text uses Noto Sans Malayalam
(`MlType`) so conjuncts render correctly, while English uses DM Sans and
Bricolage Grotesque.

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
│  │  ├─ auth/                   session DataStore
│  │  ├─ local/                  Room entities, DAO, database
│  │  ├─ model/                  AI models, active exam, answer records
│  │  ├─ prefs/                  settings and active-exam DataStores
│  │  ├─ remote/                 Padanam API client and scan prompt
│  │  └─ repo/                   library, attempts, cloud sync
│  ├─ ui/
│  │  ├─ admin/                  admin console and view model
│  │  ├─ auth/                   login and loading screens
│  │  ├─ components/             shared chrome, cards, question visuals
│  │  ├─ theme/                  colours, typography, Material theme
│  │  ├─ home/ library/ progress/ profile/ settings/ setdetail/
│  │  ├─ exam/ quiz/             setup, question runner, results, review
│  │  ├─ scan/                   camera, analysing, scan result
│  │  ├─ AppViewModel.kt         shared auth, library, settings and sync state
│  │  └─ PadanamRoot.kt          navigation graph and login gate
│  ├─ util/                      JSON, image, format, scan-session helpers
│  └─ work/                      reminder worker and scheduler
├─ app/src/main/res/             fonts, icons, themes, FileProvider paths
└─ app/src/main/AndroidManifest.xml
```

## Privacy and security

- Photographs stay in the app cache and are sent only to the deployed scan
  function while a scan is running.
- Neon stores sets, questions, attempts and the profile. It never stores images,
  AI keys or passwords (passwords are PBKDF2 hashes).
- The APK contains no API keys and no database credentials. Sessions are
  HMAC-signed tokens issued by the server and stored in app-private DataStore.
- Android backup is disabled so a restored backup cannot copy another device's
  session or library.
- Sign-out clears the local cache, exam state and sync status.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| "Add the Padanam site address" | Enter the deployed site URL in Settings or on the login screen. |
| "The scanning provider is not configured" | Set the scan secrets on the server; the app never holds them. |
| "Your session expired" | Sign in again. Sessions last seven days. |
| Camera preview is black | Grant camera permission, or use **Import photos**. Emulators often need a virtual scene camera. |
| "No complete questions were found" | Crop tighter, improve lighting, keep all four options in frame. |
| Scores missing on a new phone | Sign in with the same account and press **Settings → Sync now**. |
| Reminders never appear | Allow notifications on Android 13+ and enable reminders in Profile. |

## Manual test checklist

- [ ] Sign in as admin with the deployed site address.
- [ ] Open the admin console, create a learner and assign a synced set.
- [ ] Reset that learner's password and sign in with the new one.
- [ ] Sign out; the local library is cleared.
- [ ] Sign in as the learner and confirm only assigned sets appear.
- [ ] Scan one clear page as admin; verify both languages and the correct option.
- [ ] Scan again, choose an existing set on the review screen, and confirm the
      questions are appended while its title, subject and old questions remain.
- [ ] Start a mock exam, background the app for a minute, return and confirm the
      timer kept counting.
- [ ] Force-stop mid-exam; reopen and resume from the home banner.
- [ ] Submit an attempt, then confirm it appears on the web for the same account.
- [ ] Export the library as admin and open the JSON from another app.
- [ ] Enable airplane mode and confirm saved sets and reviews still work.
