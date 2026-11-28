# Padanam for the web

A complete browser version of the Padanam Kerala PSC study app, built with plain
HTML, CSS and JavaScript. There is no build step, framework or package install.

The deployed app has two kinds of account:

- **Administrator** — creates learner accounts, owns the library, scans papers,
  edits sets and questions, exports/imports backups and assigns sets.
- **Learner** — signs in, studies the sets the administrator assigned, and keeps
  their own attempts and profile.

Both roles sign in through the same form. Light, dark and system-following themes
are available in **Settings → Appearance**, and the choice is remembered in this
browser.

## Run it locally

- **Quick look:** open `index.html` in a modern browser. On `file://` the app
  runs in **local mode** with no sign-in, so the library, bilingual questions,
  timed mocks, practice mode and review all work offline.
- **Recommended:** serve the folder over HTTP, for example:

  ```
  npx serve web
  ```

  or any static server. On `localhost` and `127.0.0.1` the app also falls back to
  local mode when the Pages Functions are not running. That is the easiest way to
  work on the interface without a Neon database.

> A deployed site without the account secrets shows a setup page instead of
> falling back to local mode. Login cannot be bypassed on a public origin.

> Local storage is tied to the origin. A library saved from `file://` is separate
> from one saved on `http://localhost`.

## Deploy to Cloudflare Pages

1. Push this repository to GitHub or GitLab.
2. In Cloudflare, open **Workers & Pages → Create → Pages → Connect to Git**.
3. Select the repository and use these build settings:
   - **Root directory:** leave blank (the repository root).
   - **Build command:** `exit 0` (there is no build step).
   - **Build output directory:** `web`.
4. Deploy once.
5. Open the Pages project → **Settings → Variables and Secrets** and add the
   account and database secrets described below. Mark every key as **Encrypt**.
   Scanning keys are optional now: the administrator can save them in the admin
   console after signing in.
6. Redeploy so the Functions receive the new values.
7. Open the deployed site, sign in with `ADMIN_USERNAME` and `ADMIN_PASSWORD`,
   and create the learner accounts from **Admin console → Accounts**.

### Account secrets (required)

| Name | Value | Secret? |
| --- | --- | --- |
| `AUTH_SECRET` | a random string of at least 32 characters | **yes** |
| `ADMIN_USERNAME` | 3–32 lowercase letters, numbers, dots, dashes or underscores | no |
| `ADMIN_PASSWORD` | at least 8 characters | **yes** |
| `NEON_CONNECTION_STRING` | the pooled Neon connection string | **yes** |

`AUTH_SECRET` signs the stateless session tokens. Changing it signs everyone out.
`ADMIN_USERNAME` and `ADMIN_PASSWORD` bootstrap the one administrator account;
the password is checked on the server and is never stored in Neon. There is no
reset button for the bootstrap admin — to change that password, update the Pages
secret and redeploy. Learner passwords can be reset from **Admin console →
Accounts**. To revoke a learner, remove their row from the `padanam_users` table.

### Scanned deck database (optional)

Decks normally combine every question in the library. You can also keep a
separate, shared pool of scanned deck cards in a second Neon database. This is
optional: without these secrets, decks still work from the library alone.

| Name | Value | Secret? |
| --- | --- | --- |
| `DECK_NEON_CONNECTION_STRING` | the pooled connection string of the deck database | **yes** |
| `DECK_NEON_HTTP_ENDPOINT` | optional custom Neon SQL endpoint | no |

The deck database contains only scanned deck cards. The server creates its
table automatically:

- `padanam_deck_cards` stores `deck_id`, `card_id`, `main_topic`, `sub_topic`,
  `language`, `stem`, `answer`, `explanation`, `updated_ms` and a small JSONB
  payload with the Malayalam fields and deck title.

The administrator can press **Save to decks** after a scan. That writes the
extracted cards to this database without changing the library or the draft.
The scan screen has a **Question set** / **Deck cards** switch: deck mode uses a
separate AI prompt that asks for one question and one answer per card instead of
four options, so plain Q&A pages work as well as marked MCQ papers. The Decks
screen also links straight to `#/scan?mode=deck`.
Signed-in users on the web and Android then see those cards in the deck
library alongside their assigned sets. Card progress still syncs through the
normal account workspace under `scan:<deckId>:<cardId>` keys. The connection
string and endpoint are read only by the Pages Function and are never sent to
the browser or the APK.

### Gemini through Pages

These variables are the fallback. The administrator can save a provider, model
and key from **Admin console → AI scanning**; that row in Neon takes precedence
and survives redeploys.

| Name | Value | Secret? |
| --- | --- | --- |
| `SCAN_PROVIDER` | `gemini` | no |
| `GEMINI_API_KEY` | your Google AI Studio key | **yes** |
| `GEMINI_MODEL` | `gemini-2.5-flash` (optional) | no |

### OpenAI-compatible API through Pages

| Name | Value | Secret? |
| --- | --- | --- |
| `SCAN_PROVIDER` | `openai` | no |
| `OPENAI_BASE_URL` | for example `https://api.openai.com/v1` | no |
| `OPENAI_MODEL` | a vision-capable model name | no |
| `OPENAI_API_KEY` | your API key | **yes** |

The scan Function at `/api/scan` only accepts small JSON scan requests, uses the
server-side key, and never returns the key to the browser. A companion Function
at `/api/models` lists the models available to the configured provider; fetched
lists stay in memory and are never written to `localStorage`, backups or Neon.
`/api/ai-settings` lets the signed-in administrator read the non-secret status
and save or reset the database row. The stored key is write-only: it is never
returned to the browser. The `web/_routes.json` file keeps static assets off the
Functions invocation path.

> Pages Functions require Git integration or a Wrangler deployment. Direct Upload
> from the Cloudflare dashboard does not include the `functions/` directory.

> The proxy keeps the key secret, but the endpoint itself is still public. Add a
> Cloudflare rate-limiting rule or Access policy if you do not want others using
> your quota.

For local function development, create a `.dev.vars` file at the repository root
(already ignored by Git) and run:

```
npx wrangler pages dev web
```

## Accounts, sessions and permissions

- Passwords are stored in Neon as PBKDF2-SHA256 hashes with 100,000 iterations
  and a per-user salt. Plain passwords are never written to the database, the
  browser or logs.
- Sign-in returns an HMAC-SHA256 signed token with a 7-day lifetime. The browser
  keeps it in `localStorage` under `padanam.auth.v1` and sends it as
  `Authorization: Bearer …` to `/api/admin` and `/api/sync`.
- The administrator session is checked against `ADMIN_USERNAME`; learner
  sessions re-check the `padanam_users` row on every request, so removing a user
  takes effect immediately.
- Sign-in is rate-limited per server isolate (best effort), and invalid
  usernames and passwords share one generic error message.
- `#/admin` and `#/scan` redirect learners to an “admins only” page. All write
  actions also check the role in the browser, and the Functions check it again.

### Scope of each role

| Area | Administrator | Learner |
| --- | --- | --- |
| Study assigned sets, practice and mock tests | yes | yes |
| Progress, profile and attempts | yes | own attempts |
| Scan papers, add/edit sets and questions | yes | no |
| Export/import a backup, replace the cloud copy | yes | no |
| Create accounts, reset learner passwords and assign libraries | yes | no |
| Appearance and account settings | yes | yes |

There are deliberately **no delete or data-reset buttons** anywhere in the app:
no delete set, delete question, clear attempts, reset data or reset cloud. A
learner password reset is the only reset action, and it is administrator-only.
A backup import is the only replace operation, and it is also administrator-only.

## Libraries and assignment

The administrator’s library is the master copy. **Admin console → Accounts**
lists every learner with the sets they already have. Opening **Assign sets** on
an account shows the admin library; tick one or more sets and submit. The server
copies those sets into the learner’s Neon workspace with a fresh timestamp, and
they appear on the learner’s next sync (signing in triggers one immediately).
**Reset password** on the same account opens a field for a new password; the
current learner session stays valid until its seven-day token expires.

Assignment is additive. A learner starts with an empty library and only receives
what the administrator assigns; demo sets are seeded only in local mode or when
the administrator presses **Restore demos**. Learners cannot create, scan, edit,
import or delete library content.

### Where data lives

- Local state is scoped per account: `padanam.web.v1.admin` for the
  administrator, `padanam.web.v1.<userId>` for each learner, and the unscoped
  `padanam.web.v1` key for local mode.
- On the first administrator sign-in, an existing local-mode library is copied
  once into the admin scope so previous work is not lost.
- Neon stores sets, questions, attempts and the profile. The administrator uses
  the workspace `admin:<username>`; each learner uses `user:<id>`.
- Scan photos, browser-stored AI keys, the session token and the model catalog
  are never uploaded. A site-proxy key is sent only when the administrator saves
  it and is stored in `padanam_ai_settings`; it is never returned to a browser.

The legacy `SYNC_TOKEN` path remains available on the server for older
self-hosted deployments, but the app itself uses the signed-in account. Tables
are created on the first request:

- `padanam_users` holds learner accounts and password hashes.
- `padanam_sets` and `padanam_attempts` hold JSON payloads keyed by workspace.
- `padanam_profile` holds profile, profile timestamp, attempts-cleared timestamp
  and the sync epoch.
- `padanam_tombstones` records set and attempt revisions used during merges.
- `padanam_deck_progress` holds learned swipe-deck card states per workspace.
- `padanam_ai_settings` holds the admin-selected scanner provider, model, base URL
  and write-only API key. Environment variables are the fallback.
- `padanam_deck_cards` lives in the optional second deck database and holds only
  scanned deck cards.

Normal sync pulls, merges by id and timestamp, then pushes upserts. An admin
backup import replaces the cloud copy authoritatively. The service worker
bypasses `/api/*`, so account and sync requests always reach the network.

## What it does

- **Library** — browse the sets assigned to the account. The administrator can
  also scan papers or create sets and questions by hand. Questions keep aligned
  English and Malayalam versions.
- **Scanning** — photos are resized in the browser (max 1600 px, JPEG) and sent
  to Gemini, a custom OpenAI-compatible endpoint, or the optional site proxy.
  Choose **Question set** to extract four-option MCQs, or **Deck cards** to
  extract one question and one answer per card. Nothing is saved until you choose
  **Create a new set**, **Save into** an existing set, or **Save to decks**.
  Appending keeps that set’s title, subject and previous questions. Deck mode
  writes only to the separate scanned deck pool and leaves the library untouched.
  Until the selected provider is configured, the sample extraction demonstrates
  the flow. **Fetch models** loads the live catalog from Gemini, the custom
  endpoint’s `/models` route, or the site `/api/models` Function.
- **Exams** — timed mocks with an absolute deadline (the timer keeps running if
  you leave), untimed practice, question palette, flags, keyboard shortcuts and
  auto-submit at expiry. Attempts store question snapshots, so past results are
  reviewable even if the set later changes.
- **Progress** — score trend, streaks, subject accuracy and attempt history,
  calculated from attempts saved to the account.
- **Decks** — one question and one answer at a time, grouped by the same main
  topic and subtopic as the library. Cards can come from assigned sets and from
  an optional shared pool of scanned deck cards. Swipe or use the buttons to
  sort **Known** and **Again**, undo the last swipe, review the missed pile, or
  shuffle another round. Learned state syncs through the account; the current
  round stays on this device.
- **Accounts** — administrator-created users, account-scoped local storage and
  Neon sync behind the login gate. Sessions survive reloads for 7 days and an
  offline browser can keep studying its cached copy.
- **Offline** — served over HTTP, a service worker caches the app shell on the
  first visit. Library, practice, mock tests and review keep working without a
  connection; only new AI scans and account sync need the network. `file://`
  skips the worker.

## Scanning providers

The administrator configures scanning in **Admin console → AI scanning**.
Direct Gemini and custom credentials are stored only in that browser; the site
proxy saves its provider, model, base URL and key in the `padanam_ai_settings`
row in Neon. No key is ever returned to the browser, included in backups or sent
to the APK. The Scan page uses the sample extraction until the selected provider
is configured.

Changing the site proxy model no longer needs a redeploy: save it in the admin
console and the next scan uses it. `SCAN_PROVIDER`, `GEMINI_*` and `OPENAI_*`
Pages variables remain as a fallback for deployments that have not saved a row
yet.

### Gemini

Create a key in [Google AI Studio](https://aistudio.google.com/app/apikey), then
paste it in the admin console. Use a key restricted to the Generative Language
API, and rotate it if you share the device.

The default model is `gemini-2.5-flash`; `gemini-2.5-pro` is available for dense
pages, and `gemini-2.0-flash` is a fallback for older keys.

### OpenAI-compatible APIs

Choose **OpenAI-compatible**, then enter the API base URL (for example
`https://api.openai.com/v1`), an optional key and a vision-capable model name.
Padanam appends `/chat/completions` when the URL does not already include it, and
sends requests straight from the browser with an optional `Authorization: Bearer`
header. The endpoint must support the OpenAI chat-completions format with image
inputs and allow cross-origin (CORS) requests from the app’s origin.

A key is optional because local servers often do not need one. Only use endpoints
you trust: the browser sends the compressed page images directly to that address.

### Site proxy (Neon)

Choose **Site proxy** to send scans to `/api/scan` on the same site. The browser
sends no API key. The Function reads the provider, model, base URL and key from
the `padanam_ai_settings` row in Neon, falling back to the Pages variables when
no row exists. The key is write-only and is never returned by `/api/scan` or
`/api/ai-settings`.

## Files

```
index.html               app shell, navigation and icon sprite
manifest.webmanifest     installable web-app metadata
sw.js                    offline app-shell cache (HTTP only)
css/tokens.css           design tokens, fonts and base components
css/app.css              shell, dashboard, library and set detail
css/views.css            scan, exam setup, runner and results
css/account.css          progress, profile, settings, auth and admin console
js/data.js               demo sets, subjects and sample extraction
js/core.js               state, scoped persistence, scoring and overlays
js/gemini.js             image compression and AI provider clients
js/sync.js               Neon sync client, merge, tombstones and auto-sync
js/auth.js               account probe, login/logout and session storage
js/admin.js              admin API client for accounts, passwords and assignment
js/deck.js               deck cards, rounds, swipe progress and scanned deck cache
js/views.js              all page markup, including login and admin
js/app.js                router, auth gates, actions, forms and startup
assets/                  logo, favicon and self-hosted OFL fonts
_routes.json             limits Pages Functions to /api/* requests
../functions/_shared.js  Neon, schema, sessions, hashing and HTTP helpers
../functions/api/auth.js Pages Function; login, session, logout and status
../functions/api/admin.js Pages Function; create users, reset passwords, assign sets
../functions/api/sync.js Pages Function; account/legacy Neon sync
../functions/api/decks.js Pages Function; scanned deck card read and save
../functions/api/scan.js Pages Function; reads the saved AI settings or env fallback
../functions/api/models.js Pages Function; lists models for the configured provider
../functions/api/ai-settings.js Pages Function; admin read/save/reset of AI settings
```

If you change app files after deploying, bump `CACHE` in `sw.js` so returning
visitors receive the new shell instead of the cached one.

## Privacy

Account usernames, password hashes, sets, questions, attempts and profile are
stored in your Neon database. The browser keeps a signed session token and the
account-scoped copy of the library in `localStorage`. Passwords are never stored
in plain text.

Scan photos leave the browser only when the administrator presses **Extract
questions**, and they go to Google Gemini, the custom endpoint, or the site proxy
selected in the admin console. With the site proxy, Cloudflare forwards them to
the configured provider and keeps the API key server-side. Photos are never added
to the library or uploaded to Neon.

AI keys are never returned to the browser in site-proxy mode. AI keys, scan
photos, the session token and the fetched model catalog are never included in
backups or sync payloads.

Scanned deck cards saved through **Save to decks** are stored in the optional
second Neon database and are visible to every signed-in account. Their learned
state is ordinary deck progress and syncs through the account workspace.
