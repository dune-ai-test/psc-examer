/* =========================================================
   Padanam — Neon cloud sync for Cloudflare Pages
   The connection string and sync code live in Pages secrets.
   The browser only sees the sync code the owner typed in.
   Sets, questions, attempts and profile sync; photos and AI
   keys never leave the browser.
   ========================================================= */

const MIN_TOKEN_LENGTH = 16;
const MAX_SETS = 1000;
const MAX_ATTEMPTS = 5000;
const MAX_TOMBSTONES = 5000;
const MAX_BODY_BYTES = 6 * 1024 * 1024;
const NEON_TIMEOUT_MS = 45000;

export function onRequest(context) {
  if (context.request.method === 'GET') {
    return json(statusPayload(syncConfig(context.env)), 200);
  }
  if (context.request.method === 'POST') {
    return handleSync(context);
  }
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

async function handleSync(context) {
  const config = syncConfig(context.env);
  if (!config.configured) {
    return json({ error: config.error || 'Cloud sync is not configured.' }, 503);
  }
  if (!sameOrigin(context.request)) {
    return json({ error: 'This endpoint only accepts requests from the app.' }, 403);
  }

  let body;
  try {
    body = await readBody(context.request);
  } catch (err) {
    return json({ error: err.message || 'Send a JSON request body.' }, 400);
  }

  const action = String(body.action || '').trim();
  if (['test', 'pull', 'push', 'replace', 'reset'].indexOf(action) < 0) {
    return json({ error: 'Unknown action. Use test, pull, push, replace or reset.' }, 400);
  }

  const token = String(body.token || '').trim();
  if (!token) {
    return json({ error: 'Enter the sync code you set as SYNC_TOKEN in Cloudflare Pages.' }, 401);
  }
  if (token.length < MIN_TOKEN_LENGTH) {
    return json({ error: 'The sync code must be at least ' + MIN_TOKEN_LENGTH + ' characters.' }, 400);
  }
  if (!(await tokensMatch(token, config.token))) {
    return json({ error: 'That sync code is not correct.' }, 401);
  }

  const workspace = await workspaceFor(token);

  try {
    if (action === 'test') {
      await ensureSchema(config);
      await neonQuery(config, 'SELECT 1 AS ok');
      return json({
        ok: true,
        provider: 'neon',
        host: config.host,
        message: 'Connected to Neon.'
      });
    }

    if (action === 'pull') {
      await ensureSchema(config);
      return json(await pullWorkspace(config, workspace));
    }

    if (action === 'push') {
      let payload;
      try {
        payload = validateSnapshot(body);
      } catch (err) {
        return json({ error: err.message || 'The sync payload is invalid.' }, 400);
      }
      await ensureSchema(config);
      const counts = await pushWorkspace(config, workspace, payload);
      return json({ ok: true, sets: counts.sets, attempts: counts.attempts });
    }

    if (action === 'replace' || action === 'reset') {
      const empty = action === 'reset';
      let payload;
      if (empty) {
        payload = { sets: [], attempts: [], profile: {}, profileUpdatedAt: new Date().toISOString(), attemptsClearedAt: new Date().toISOString(), tombstones: [] };
      } else {
        try {
          payload = validateSnapshot(body);
        } catch (err) {
          return json({ error: err.message || 'The sync payload is invalid.' }, 400);
        }
      }
      await ensureSchema(config);
      const result = await replaceWorkspace(config, workspace, payload);
      return json({ ok: true, epochMs: result.epochMs, sets: result.sets, attempts: result.attempts });
    }

    return json({ error: 'Unknown action.' }, 400);
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'Cloud sync failed.' }, 502);
  }
}

/* ── Configuration ─────────────────────────────────────── */
function syncConfig(env) {
  const connectionString = String(env.NEON_CONNECTION_STRING || '').trim();
  const rawEndpoint = String(env.NEON_HTTP_ENDPOINT || '').trim();
  const token = String(env.SYNC_TOKEN || '').trim();

  let endpoint = '';
  let host = '';
  let error = '';

  if (!connectionString) {
    error = 'Set the NEON_CONNECTION_STRING secret in Cloudflare Pages.';
  } else {
    try {
      const url = new URL(connectionString);
      if (url.protocol !== 'postgres:' && url.protocol !== 'postgresql:') {
        throw new Error('NEON_CONNECTION_STRING must start with postgres:// or postgresql://.');
      }
      host = url.hostname;
      endpoint = rawEndpoint ? normalizeEndpoint(rawEndpoint) : deriveEndpoint(host);
    } catch (err) {
      error = /must start with/.test(err && err.message ? err.message : '')
        ? err.message
        : 'NEON_CONNECTION_STRING must be a valid postgres:// or postgresql:// connection string.';
    }
  }

  if (!token) {
    error = error || 'Set the SYNC_TOKEN secret in Cloudflare Pages.';
  }
  if (token && token.length < MIN_TOKEN_LENGTH) {
    error = error || 'SYNC_TOKEN must be at least ' + MIN_TOKEN_LENGTH + ' characters.';
  }

  return {
    connectionString,
    token,
    endpoint,
    host: safeHost(endpoint) || host,
    error,
    configured: !!connectionString && !!token && !error && !!endpoint
  };
}

function statusPayload(config) {
  return {
    ok: true,
    provider: 'neon',
    configured: config.configured,
    host: config.host,
    error: config.error
  };
}

function deriveEndpoint(hostname) {
  const host = String(hostname || '').trim();
  if (!host) throw new Error('Could not derive the Neon SQL endpoint.');
  return 'https://' + host + '/sql';
}

function normalizeEndpoint(value) {
  let url;
  try {
    url = new URL(value);
  } catch (err) {
    throw new Error('NEON_HTTP_ENDPOINT is not a valid URL.');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new Error('NEON_HTTP_ENDPOINT must start with https:// or http://.');
  }
  if (!/\/sql$/i.test(url.pathname.replace(/\/+$/, ''))) {
    url.pathname = url.pathname.replace(/\/+$/, '') + '/sql';
  }
  url.hash = '';
  url.search = '';
  return url.toString();
}

function safeHost(endpoint) {
  try {
    return new URL(endpoint).host;
  } catch (err) {
    return '';
  }
}

/* ── Auth ──────────────────────────────────────────────── */
async function tokensMatch(a, b) {
  const [left, right] = await Promise.all([sha256(a), sha256(b)]);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i++) {
    diff |= (left.charCodeAt(i) || 0) ^ (right.charCodeAt(i) || 0);
  }
  return diff === 0;
}

async function workspaceFor(token) {
  const digest = await sha256('padanam-workspace:' + token);
  return digest.slice(0, 32);
}

async function sha256(value) {
  const data = new TextEncoder().encode(String(value));
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash)).map(function (byte) {
    return byte.toString(16).padStart(2, '0');
  }).join('');
}

/* ── Neon client ───────────────────────────────────────── */
let schemaReady = null;

async function ensureSchema(config) {
  if (schemaReady) return schemaReady;
  schemaReady = neonBatch(config, [
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_sets (' +
        'workspace TEXT NOT NULL, id TEXT NOT NULL, updated_ms BIGINT NOT NULL, ' +
        'payload JSONB NOT NULL, PRIMARY KEY (workspace, id))'
    },
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_attempts (' +
        'workspace TEXT NOT NULL, id TEXT NOT NULL, finished_ms BIGINT NOT NULL, ' +
        'payload JSONB NOT NULL, PRIMARY KEY (workspace, id))'
    },
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_profile (' +
        'workspace TEXT PRIMARY KEY, profile JSONB NOT NULL, profile_updated_ms BIGINT NOT NULL, ' +
        'attempts_cleared_ms BIGINT NOT NULL DEFAULT 0, epoch_ms BIGINT NOT NULL DEFAULT 0)'
    },
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_tombstones (' +
        'workspace TEXT NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, deleted_ms BIGINT NOT NULL, ' +
        'PRIMARY KEY (workspace, kind, id))'
    },
    {
      query: 'CREATE INDEX IF NOT EXISTS padanam_attempts_finished_idx ' +
        'ON padanam_attempts (workspace, finished_ms DESC)'
    }
  ]).catch(function (err) {
    schemaReady = null;
    throw err;
  });
  return schemaReady;
}

async function neonQuery(config, sql, params) {
  const data = await neonPost(config, { query: sql, params: params || [] });
  return data;
}

async function neonBatch(config, queries) {
  const data = await neonPost(config, { queries: queries });
  return data && Array.isArray(data.results) ? data.results : [];
}

async function neonPost(config, body) {
  const controller = new AbortController();
  const timer = setTimeout(function () { controller.abort(); }, NEON_TIMEOUT_MS);
  let response;
  let text = '';

  try {
    response = await fetch(config.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Neon-Connection-String': config.connectionString,
        Accept: 'application/json'
      },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    text = await response.text();
  } catch (err) {
    if (err && err.name === 'AbortError') {
      throw new Error('Neon did not respond in time. Check the connection string and try again.');
    }
    throw new Error('Could not reach Neon. Check the connection string and your internet connection.');
  } finally {
    clearTimeout(timer);
  }

  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (err) { data = null; }
  }

  if (!response.ok) {
    throw new Error(neonErrorMessage(response.status, data));
  }
  if (!data || typeof data !== 'object') {
    throw new Error('Neon returned an unexpected response.');
  }
  if (data.message && !data.results && !data.rows && !data.command) {
    throw new Error('Neon rejected the query' + (data.message ? ': ' + String(data.message).slice(0, 180) : '.'));
  }
  return data;
}

function neonErrorMessage(status, data) {
  const detail = data && (data.message || data.error) ? String(data.message || data.error).slice(0, 180) : '';
  if (status === 400) {
    return 'Neon rejected the request' + (detail ? ': ' + detail : '.');
  }
  if (status === 401 || status === 403) {
    return 'Neon rejected the connection string. Copy a fresh one from the Neon dashboard.';
  }
  if (status === 404) {
    return 'The Neon SQL endpoint was not found. Check NEON_HTTP_ENDPOINT or the connection string host.';
  }
  if (status === 429) {
    return 'Neon is rate-limiting requests. Wait a minute and sync again.';
  }
  if (status >= 500) {
    return 'Neon is having trouble right now. Try again in a moment.';
  }
  return 'Neon returned HTTP ' + status + (detail ? ': ' + detail : '.');
}

/* ── Workspace operations ──────────────────────────────── */
async function pullWorkspace(config, workspace) {
  const results = await neonBatch(config, [
    {
      query: 'SELECT payload FROM padanam_sets WHERE workspace = $1 ORDER BY updated_ms DESC',
      params: [workspace]
    },
    {
      query: 'SELECT payload FROM padanam_attempts WHERE workspace = $1 ORDER BY finished_ms DESC',
      params: [workspace]
    },
    {
      query: 'SELECT profile, profile_updated_ms, attempts_cleared_ms, epoch_ms ' +
        'FROM padanam_profile WHERE workspace = $1',
      params: [workspace]
    },
    {
      query: 'SELECT kind, id, deleted_ms FROM padanam_tombstones WHERE workspace = $1',
      params: [workspace]
    }
  ]);

  const sets = rows(results[0]).map(function (row) { return payloadValue(row.payload); }).filter(Boolean);
  const attempts = rows(results[1]).map(function (row) { return payloadValue(row.payload); }).filter(Boolean);
  const profileRow = rows(results[2])[0] || null;
  const tombstones = rows(results[3]).map(function (row) {
    return {
      kind: String(row.kind || ''),
      id: String(row.id || ''),
      deletedMs: Number(row.deleted_ms || 0)
    };
  });

  return {
    ok: true,
    sets: sets,
    attempts: attempts,
    profile: profileRow ? payloadValue(profileRow.profile) : null,
    profileUpdatedMs: profileRow ? Number(profileRow.profile_updated_ms || 0) : 0,
    attemptsClearedMs: profileRow ? Number(profileRow.attempts_cleared_ms || 0) : 0,
    epochMs: profileRow ? Number(profileRow.epoch_ms || 0) : 0,
    tombstones: tombstones
  };
}

async function pushWorkspace(config, workspace, snapshot) {
  const setItems = snapshot.sets.map(function (set) {
    return {
      id: set.id,
      updatedMs: Date.parse(set.updatedAt || set.createdAt),
      payload: set
    };
  });
  const attemptItems = snapshot.attempts.map(function (attempt) {
    return {
      id: attempt.id,
      finishedMs: Date.parse(attempt.finishedAt),
      payload: attempt
    };
  });
  const tombstoneItems = snapshot.tombstones.map(function (item) {
    return {
      kind: item.kind,
      id: item.id,
      deletedMs: Date.parse(item.deletedAt)
    };
  });
  const profileUpdatedMs = Date.parse(snapshot.profileUpdatedAt) || 0;
  const attemptsClearedMs = Date.parse(snapshot.attemptsClearedAt) || 0;

  const results = await neonBatch(config, [
    {
      query: 'INSERT INTO padanam_sets (workspace, id, updated_ms, payload) ' +
        'SELECT $1, item->>\'id\', (item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (workspace, id) DO UPDATE SET updated_ms = EXCLUDED.updated_ms, payload = EXCLUDED.payload ' +
        'WHERE EXCLUDED.updated_ms > padanam_sets.updated_ms',
      params: [workspace, JSON.stringify(setItems)]
    },
    {
      query: 'INSERT INTO padanam_attempts (workspace, id, finished_ms, payload) ' +
        'SELECT $1, item->>\'id\', (item->>\'finishedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (workspace, id) DO NOTHING',
      params: [workspace, JSON.stringify(attemptItems)]
    },
    {
      query: 'INSERT INTO padanam_tombstones (workspace, kind, id, deleted_ms) ' +
        'SELECT $1, item->>\'kind\', item->>\'id\', (item->>\'deletedMs\')::bigint ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (workspace, kind, id) DO UPDATE SET deleted_ms = GREATEST(padanam_tombstones.deleted_ms, EXCLUDED.deleted_ms)',
      params: [workspace, JSON.stringify(tombstoneItems)]
    },
    {
      query: 'DELETE FROM padanam_sets s USING padanam_tombstones t ' +
        'WHERE s.workspace = $1 AND t.workspace = $1 AND t.kind = \'set\' AND t.id = s.id ' +
        'AND s.updated_ms <= t.deleted_ms',
      params: [workspace]
    },
    {
      query: 'DELETE FROM padanam_attempts a USING padanam_tombstones t ' +
        'WHERE a.workspace = $1 AND t.workspace = $1 AND t.kind = \'attempt\' AND t.id = a.id ' +
        'AND a.finished_ms <= t.deleted_ms',
      params: [workspace]
    },
    {
      query: 'DELETE FROM padanam_attempts WHERE workspace = $1 AND finished_ms <= $2',
      params: [workspace, attemptsClearedMs]
    },
    {
      query: 'DELETE FROM padanam_tombstones t USING padanam_sets s ' +
        'WHERE t.workspace = $1 AND s.workspace = $1 AND t.kind = \'set\' AND t.id = s.id ' +
        'AND s.updated_ms > t.deleted_ms',
      params: [workspace]
    },
    {
      query: 'INSERT INTO padanam_profile (workspace, profile, profile_updated_ms, attempts_cleared_ms, epoch_ms) ' +
        'VALUES ($1, $2::jsonb, $3, $4, 0) ' +
        'ON CONFLICT (workspace) DO UPDATE SET ' +
        'profile = CASE WHEN EXCLUDED.profile_updated_ms >= padanam_profile.profile_updated_ms ' +
        'THEN EXCLUDED.profile ELSE padanam_profile.profile END, ' +
        'profile_updated_ms = GREATEST(padanam_profile.profile_updated_ms, EXCLUDED.profile_updated_ms), ' +
        'attempts_cleared_ms = GREATEST(padanam_profile.attempts_cleared_ms, EXCLUDED.attempts_cleared_ms)',
      params: [workspace, JSON.stringify(snapshot.profile), profileUpdatedMs, attemptsClearedMs]
    },
    {
      query: 'SELECT COUNT(*)::int AS count FROM padanam_sets WHERE workspace = $1',
      params: [workspace]
    },
    {
      query: 'SELECT COUNT(*)::int AS count FROM padanam_attempts WHERE workspace = $1',
      params: [workspace]
    }
  ]);

  return {
    sets: countOf(results[results.length - 2]),
    attempts: countOf(results[results.length - 1])
  };
}

async function replaceWorkspace(config, workspace, snapshot) {
  const now = Date.now();
  const setItems = snapshot.sets.map(function (set) {
    return {
      id: set.id,
      updatedMs: Date.parse(set.updatedAt || set.createdAt) || now,
      payload: set
    };
  });
  const attemptItems = snapshot.attempts.map(function (attempt) {
    return {
      id: attempt.id,
      finishedMs: Date.parse(attempt.finishedAt) || now,
      payload: attempt
    };
  });
  const profileUpdatedMs = Date.parse(snapshot.profileUpdatedAt) || now;
  const attemptsClearedMs = Date.parse(snapshot.attemptsClearedAt) || 0;

  const results = await neonBatch(config, [
    { query: 'DELETE FROM padanam_sets WHERE workspace = $1', params: [workspace] },
    { query: 'DELETE FROM padanam_attempts WHERE workspace = $1', params: [workspace] },
    { query: 'DELETE FROM padanam_tombstones WHERE workspace = $1', params: [workspace] },
    {
      query: 'INSERT INTO padanam_sets (workspace, id, updated_ms, payload) ' +
        'SELECT $1, item->>\'id\', (item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item',
      params: [workspace, JSON.stringify(setItems)]
    },
    {
      query: 'INSERT INTO padanam_attempts (workspace, id, finished_ms, payload) ' +
        'SELECT $1, item->>\'id\', (item->>\'finishedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item',
      params: [workspace, JSON.stringify(attemptItems)]
    },
    {
      query: 'INSERT INTO padanam_profile (workspace, profile, profile_updated_ms, attempts_cleared_ms, epoch_ms) ' +
        'VALUES ($1, $2::jsonb, $3, $4, $5) ' +
        'ON CONFLICT (workspace) DO UPDATE SET profile = EXCLUDED.profile, ' +
        'profile_updated_ms = EXCLUDED.profile_updated_ms, ' +
        'attempts_cleared_ms = EXCLUDED.attempts_cleared_ms, epoch_ms = EXCLUDED.epoch_ms',
      params: [workspace, JSON.stringify(snapshot.profile), profileUpdatedMs, attemptsClearedMs, now]
    }
  ]);

  return {
    epochMs: now,
    sets: setItems.length,
    attempts: attemptItems.length,
    results: results
  };
}

/* ── Validation ────────────────────────────────────────── */
function validateSnapshot(body) {
  const sets = Array.isArray(body.sets) ? body.sets : [];
  const attempts = Array.isArray(body.attempts) ? body.attempts : [];
  const tombstones = Array.isArray(body.tombstones) ? body.tombstones : [];

  if (sets.length > MAX_SETS) throw new Error('Too many sets to sync at once.');
  if (attempts.length > MAX_ATTEMPTS) throw new Error('Too many attempts to sync at once.');
  if (tombstones.length > MAX_TOMBSTONES) throw new Error('Too many deleted items to sync at once.');

  const cleanSets = sets.map(function (set) {
    if (!set || typeof set !== 'object' || !set.id) throw new Error('One of the sets is missing an id.');
    if (typeof set.id !== 'string' || set.id.length > 100) throw new Error('A set id is invalid.');
    if (!Date.parse(set.updatedAt || set.createdAt)) throw new Error('A set has an invalid updated date.');
    return set;
  });

  const cleanAttempts = attempts.map(function (attempt) {
    if (!attempt || typeof attempt !== 'object' || !attempt.id) throw new Error('One of the attempts is missing an id.');
    if (typeof attempt.id !== 'string' || attempt.id.length > 100) throw new Error('An attempt id is invalid.');
    if (!Date.parse(attempt.finishedAt)) throw new Error('An attempt has an invalid finished date.');
    return attempt;
  });

  const cleanTombstones = tombstones.map(function (item) {
    if (!item || (item.kind !== 'set' && item.kind !== 'attempt') || !item.id) {
      throw new Error('A deleted-item marker is invalid.');
    }
    if (!Date.parse(item.deletedAt)) throw new Error('A deleted-item marker has an invalid date.');
    return { kind: item.kind, id: String(item.id).slice(0, 100), deletedAt: item.deletedAt };
  });

  const profile = body.profile && typeof body.profile === 'object' && !Array.isArray(body.profile)
    ? body.profile
    : {};
  const profileUpdatedAt = Date.parse(body.profileUpdatedAt) ? body.profileUpdatedAt : new Date().toISOString();
  const attemptsClearedAt = Date.parse(body.attemptsClearedAt) ? body.attemptsClearedAt : '';

  const payload = {
    sets: cleanSets,
    attempts: cleanAttempts,
    profile: profile,
    profileUpdatedAt: profileUpdatedAt,
    attemptsClearedAt: attemptsClearedAt,
    tombstones: cleanTombstones
  };

  const encoded = JSON.stringify(payload);
  if (encoded.length > MAX_BODY_BYTES) {
    throw new Error('The sync payload is too large. Try syncing fewer sets at once.');
  }
  return payload;
}

/* ── Helpers ───────────────────────────────────────────── */
async function readBody(request) {
  const length = parseInt(request.headers.get('content-length') || '0', 10);
  if (length > MAX_BODY_BYTES) throw new Error('The sync payload is too large.');

  const text = await request.text();
  if (!text) throw new Error('Send a JSON request body.');
  if (text.length > MAX_BODY_BYTES) throw new Error('The sync payload is too large.');

  let body;
  try {
    body = JSON.parse(text);
  } catch (err) {
    throw new Error('Send a valid JSON request body.');
  }
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new Error('Send a JSON object.');
  }
  return body;
}

function sameOrigin(request) {
  const origin = request.headers.get('Origin');
  if (!origin) return true;
  try {
    return origin === new URL(request.url).origin;
  } catch (err) {
    return false;
  }
}

function rows(result) {
  return result && Array.isArray(result.rows) ? result.rows : [];
}

function countOf(result) {
  const row = rows(result)[0];
  return row ? Number(row.count || 0) : 0;
}

function payloadValue(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') {
    try { return JSON.parse(value); } catch (err) { return null; }
  }
  return value;
}

function json(body, status, extraHeaders) {
  const headers = Object.assign({
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  }, extraHeaders || {});

  return new Response(JSON.stringify(body), { status: status || 200, headers });
}
