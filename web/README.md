# Padanam for the web

A complete browser version of the Padanam Kerala PSC study app, built with plain
HTML, CSS and JavaScript. There is no build step, framework or package install.

## Run it

- **Quick look:** open `index.html` in a modern browser. The library, bilingual
  questions, timed mocks, practice mode and review all work from `file://`.
- **Recommended:** serve the folder over HTTP, for example:

  ```
  npx serve web
  ```

  or any static server. A local origin gives the most reliable camera, file
  picker and AI endpoint behaviour, and it enables the self-hosted fonts, web
  manifest and offline caching.

> Opening from `file://` works, but browsers block the manifest and the
> self-hosted font files on local pages, so the app falls back to system fonts.
> Serve over HTTP for the full design.

> Local storage is tied to the origin. A library saved from `file://` is separate
> from one saved on `http://localhost`. Use **Settings → Export a backup file**
> to move between them.

## Deploy to Cloudflare Pages

The static app can be served on its own, but keeping an API key out of the
browser requires the included Pages Function. Environment variables on a
static Pages site are **not** visible to `web/` JavaScript, so the function in
`functions/api/scan.js` reads the key on the server and proxies scan requests.

1. Push this repository to GitHub or GitLab.
2. In Cloudflare, open **Workers & Pages → Create → Pages → Connect to Git**.
3. Select the repository and use these build settings:
   - **Root directory:** leave blank (the repository root).
   - **Build command:** `exit 0` (there is no build step).
   - **Build output directory:** `web`.
4. Deploy once.
5. Open the Pages project → **Settings → Variables and Secrets** and add the
   variables for the provider you want. Mark the key as **Encrypt**.
6. Add the cloud backup secrets too if you want sync (optional). See
   [Cloud backup with Neon](#cloud-backup-with-neon).
7. Redeploy the project so the functions receive the new values.
8. Open the deployed app → **Settings → Provider → Cloudflare Pages** → **Test
   connection** → **Save settings**.

### Gemini through Pages

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

The Pages Function is available at `/api/scan`. It only accepts small JSON scan
requests, uses the server-side key, and never returns the key to the browser.
The `web/_routes.json` file keeps static assets off the Functions invocation
path.

> Pages Functions require Git integration or a Wrangler deployment. Direct
> Upload from the Cloudflare dashboard does not include the `functions/`
> directory.

> The proxy keeps the key secret, but the endpoint itself is still public. Add a
> Cloudflare rate-limiting rule or Access policy if you do not want others using
> your quota.

For local function development, create a `.dev.vars` file at the repository root
(already ignored by Git) and run:

```
npx wrangler pages dev web
```

## Cloud backup with Neon

Cloud backup is optional. It stores sets, questions, attempts and the profile in
your own [Neon](https://neon.tech) Postgres database, so several browsers can
share one library. Scan photos, AI keys and the sync code itself are never
uploaded.

1. Create a Neon project and copy a **pooled** connection string from the Neon
   dashboard. It looks like
   `postgresql://user:password@ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`.
2. In **Cloudflare Pages → Settings → Variables and Secrets**, add:

   | Name | Value | Secret? |
   | --- | --- | --- |
   | `NEON_CONNECTION_STRING` | the pooled connection string | **yes** |
   | `SYNC_TOKEN` | a sync code of at least 16 characters | **yes** |
   | `NEON_HTTP_ENDPOINT` | optional full `https://…/sql` URL | no |

   Mark the connection string and the sync code as **Encrypt**.
3. Redeploy. Open **Settings → Cloud backup**, enter the same sync code and
   press **Save sync code**. The app tests the function, creates the tables and
   can sync immediately.
4. Enter the same code in another browser to share the library. A different code
   points the browser at a different dataset.

The sync Function at `/api/sync` reads the connection string on the server and
uses the Neon database host from it to call the HTTP SQL endpoint
(`https://<neon-host>/sql`). Set `NEON_HTTP_ENDPOINT` only if your Neon project
needs an explicit endpoint. Tables are created on the first request:

- `padanam_sets` and `padanam_attempts` hold JSON payloads keyed by workspace.
- `padanam_profile` holds profile, profile timestamp, attempts-cleared timestamp
  and the sync epoch.
- `padanam_tombstones` records set and attempt deletions.

The workspace is derived from `SYNC_TOKEN`, so the code is both the password and
the dataset identity. Use a long random value and treat it like a password.
Normal sync pulls, merges by id and timestamp, then pushes upserts. Deleting a
set writes a tombstone, clearing attempts writes `attemptsClearedAt`, and
importing a backup or resetting the app replaces/resets the cloud copy
authoritatively. Changing `SYNC_TOKEN` starts a separate workspace instead of
renaming the old one.

`NEON_CONNECTION_STRING` and `SYNC_TOKEN` stay in Pages secrets. The browser
stores the sync code in `localStorage` under `padanam.sync.v1`; after **Forget
code** it keeps only a non-secret fingerprint, so reconnecting the same code
still has the pending deletions. Backups include neither the code nor the
fingerprint. The service worker bypasses `/api/*`, so sync always reaches the
network.

## What it does

- **Library** — demo sets are seeded on first run; scan papers or create sets
  and questions by hand. Questions keep aligned English and Malayalam versions.
- **Scanning** — photos are resized in the browser (max 1600 px, JPEG) and sent
  to Gemini, a custom OpenAI-compatible endpoint, or the optional Cloudflare
  Pages proxy. Nothing is saved until you press **Save to library**. Until the
  selected provider is configured, the sample extraction demonstrates the flow.
- **Exams** — timed mocks with an absolute deadline (the timer keeps running if
  you leave), untimed practice, question palette, flags, keyboard shortcuts and
  auto-submit at expiry. Attempts store question snapshots, so past results are
  reviewable even if the set later changes.
- **Progress** — score trend, streaks, subject accuracy and attempt history,
  all calculated from locally saved attempts.
- **Cloud backup** — optional Neon sync for sets, questions, attempts and
  profile. Off by default; connect it in **Settings → Cloud backup** with the
  sync code you set in Cloudflare Pages.
- **Data** — profile, settings, sets and attempts live in `localStorage` under
  `padanam.web.v1`. JSON export/import moves everything except API keys and the
  sync code.
- **Offline** — served over HTTP, a service worker caches the app shell on the
  first visit. Library, practice, mock tests and review keep working without a
  connection; only new AI scans and cloud sync need the network. `file://` skips
  the worker.

## Scanning providers

Choose the provider in **Settings**. Direct Gemini and custom credentials are
stored only in this browser; the Cloudflare Pages option keeps the key in Pages
secrets. No key is ever included in backups. The Scan page uses the sample
extraction until the selected provider is configured.

### Gemini

Create a key in [Google AI Studio](https://aistudio.google.com/app/apikey), then
paste it in **Settings**. Use a key restricted to the Generative Language API,
and rotate it if you share the device.

The default model is `gemini-2.5-flash`; `gemini-2.5-pro` is available for dense
pages, and `gemini-2.0-flash` is a fallback for older keys.

### OpenAI-compatible APIs

Choose **OpenAI-compatible**, then enter the API base URL (for example
`https://api.openai.com/v1`), an optional key and a vision-capable model name.
Padanam appends `/chat/completions` when the URL does not already include it, and
sends requests straight from the browser with an optional `Authorization: Bearer`
header. The endpoint must support the OpenAI chat-completions format with image
inputs and allow cross-origin (CORS) requests from the app's origin.

A key is optional because local servers often do not need one. Only use endpoints
you trust: the browser sends the compressed page images directly to that address.

### Cloudflare Pages proxy

Choose **Cloudflare Pages** to send scans to `/api/scan` on the same site. The
browser sends no API key; the function reads `GEMINI_API_KEY` or
`OPENAI_API_KEY` from the encrypted Pages secrets. See
[Deploy to Cloudflare Pages](#deploy-to-cloudflare-pages) for the variables.

## Files

```
index.html               app shell, navigation and icon sprite
manifest.webmanifest     installable web-app metadata
sw.js                    offline app-shell cache (HTTP only)
css/tokens.css           design tokens, fonts and base components
css/app.css              shell, dashboard, library and set detail
css/views.css            scan, exam setup, runner and results
css/account.css          progress, profile, settings and editors
js/data.js               demo sets, subjects and sample extraction
js/core.js               state, persistence, scoring and overlays
js/gemini.js             image compression, Gemini, OpenAI-compatible and proxy clients
js/sync.js               optional Neon sync, merge, tombstones and auto-sync
js/views.js              all page markup and scan/exam UI
js/app.js                router, actions, forms, clock and startup
assets/                  logo, favicon and self-hosted OFL fonts
_routes.json             limits Pages Functions to /api/* requests
../functions/api/scan.js Cloudflare Pages Function; reads keys from Pages secrets
../functions/api/sync.js Cloudflare Pages Function; Neon sync, connection string stays server-side
```

If you change app files after deploying, bump `CACHE` in `sw.js` so returning
visitors receive the new shell instead of the cached one.

## Privacy

Padanam has no account server. Scan photos leave the browser only when you press
**Extract questions**, and they go to Google Gemini, the custom endpoint, or the
Cloudflare Pages Function selected in Settings. With the Pages proxy, Cloudflare
forwards them to the configured provider and keeps the API key server-side.
Photos are never added to the library.

Everything else stays in this browser unless you turn on **Cloud backup**. With
sync on, sets, questions, attempts and profile are sent to the Neon database
configured in your Pages secrets. Photos, AI keys, device settings and the sync
code are never uploaded, and exports/backups exclude keys and the sync code.
