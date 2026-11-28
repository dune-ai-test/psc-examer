/* =========================================================
   Padanam — shared helpers for Cloudflare Pages Functions
   Neon access, signed sessions, password hashing and small
   HTTP utilities used by auth, admin and sync.
   ========================================================= */

const NEON_TIMEOUT_MS = 45000;
const MIN_AUTH_SECRET = 32;
const MIN_ADMIN_PASSWORD = 8;

export const MIN_USERNAME_LENGTH = 3;
export const MAX_USERNAME_LENGTH = 32;
export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 200;
export const PBKDF2_ITERATIONS = 100000;
export const SESSION_TTL_SECONDS = 7 * 24 * 60 * 60;

const USERNAME_PATTERN = /^[a-z0-9][a-z0-9._-]{2,31}$/;
const RESERVED_USERNAMES = ['admin', 'administrator', 'local', 'owner', 'root', 'system', 'padanam'];

/* ── Configuration ─────────────────────────────────────── */
function neonConfig(env, connectionName, endpointName) {
  const connectionString = String((env && env[connectionName]) || '').trim();
  const rawEndpoint = String((env && env[endpointName]) || '').trim();

  let endpoint = '';
  let host = '';
  let error = '';

  if (!connectionString) {
    error = 'Set the ' + connectionName + ' secret in Cloudflare Pages.';
  } else {
    try {
      const url = new URL(connectionString);
      if (url.protocol !== 'postgres:' && url.protocol !== 'postgresql:') {
        throw new Error(connectionName + ' must start with postgres:// or postgresql://.');
      }
      host = url.hostname;
      endpoint = rawEndpoint ? normalizeEndpoint(rawEndpoint, endpointName) : deriveEndpoint(host);
    } catch (err) {
      error = /must start with/.test(err && err.message ? err.message : '')
        ? err.message
        : connectionName + ' must be a valid postgres:// or postgresql:// connection string.';
    }
  }

  return {
    connectionString,
    endpoint,
    host: safeHost(endpoint) || host,
    error,
    configured: !!connectionString && !error && !!endpoint
  };
}

export function databaseConfig(env) {
  return neonConfig(env, 'NEON_CONNECTION_STRING', 'NEON_HTTP_ENDPOINT');
}

/**
 * Optional second Neon database for scanned swipe-deck cards. When it is not
 * configured the main library still feeds decks, so this stays opt-in.
 */
export function deckDatabaseConfig(env) {
  const config = neonConfig(env, 'DECK_NEON_CONNECTION_STRING', 'DECK_NEON_HTTP_ENDPOINT');
  if (!config.connectionString) {
    return Object.assign({}, config, {
      configured: false,
      error: '',
      optional: true
    });
  }
  return config;
}

export function authConfig(env) {
  const secret = String((env && env.AUTH_SECRET) || '').trim();
  const adminUsername = String((env && env.ADMIN_USERNAME) || '').trim().toLowerCase();
  const adminPassword = String((env && env.ADMIN_PASSWORD) || '');

  let error = '';
  if (!secret) error = 'Set the AUTH_SECRET secret in Cloudflare Pages.';
  else if (secret.length < MIN_AUTH_SECRET) {
    error = 'AUTH_SECRET must be at least ' + MIN_AUTH_SECRET + ' characters.';
  }

  if (!adminUsername) {
    error = error || 'Set the ADMIN_USERNAME secret in Cloudflare Pages.';
  } else if (!USERNAME_PATTERN.test(adminUsername)) {
    error = error || 'ADMIN_USERNAME must be 3–32 characters using letters, numbers, dots, dashes or underscores.';
  }

  if (!adminPassword) {
    error = error || 'Set the ADMIN_PASSWORD secret in Cloudflare Pages.';
  } else if (adminPassword.length < MIN_ADMIN_PASSWORD) {
    error = error || 'ADMIN_PASSWORD must be at least ' + MIN_ADMIN_PASSWORD + ' characters.';
  } else if (adminPassword.length > MAX_PASSWORD_LENGTH) {
    error = error || 'ADMIN_PASSWORD is too long.';
  }

  return {
    secret,
    adminUsername,
    adminPassword,
    error,
    configured: !error
  };
}

export function validUsername(value) {
  return USERNAME_PATTERN.test(String(value || ''));
}

export function reservedUsername(value, env) {
  const name = String(value || '').trim().toLowerCase();
  if (RESERVED_USERNAMES.indexOf(name) >= 0) return true;
  const auth = authConfig(env);
  return !!(auth.adminUsername && auth.adminUsername === name);
}

function deriveEndpoint(hostname) {
  const host = String(hostname || '').trim();
  if (!host) throw new Error('Could not derive the Neon SQL endpoint.');
  return 'https://' + host + '/sql';
}

function normalizeEndpoint(value, endpointName) {
  const label = endpointName || 'NEON_HTTP_ENDPOINT';
  let url;
  try {
    url = new URL(value);
  } catch (err) {
    throw new Error(label + ' is not a valid URL.');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new Error(label + ' must start with https:// or http://.');
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

/* ── Schema ────────────────────────────────────────────── */
let schemaReady = null;

export async function ensureSchema(config) {
  if (schemaReady) return schemaReady;
  schemaReady = neonBatch(config, [
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_users (' +
        'id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, ' +
        'role TEXT NOT NULL DEFAULT \'user\', password_hash TEXT NOT NULL, salt TEXT NOT NULL, ' +
        'iterations INTEGER NOT NULL, created_ms BIGINT NOT NULL)'
    },
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_sets (' +
        'workspace TEXT NOT NULL, id TEXT NOT NULL, main_topic TEXT NOT NULL DEFAULT \'\', ' +
        'sub_topic TEXT NOT NULL DEFAULT \'\', updated_ms BIGINT NOT NULL, ' +
        'payload JSONB NOT NULL, PRIMARY KEY (workspace, id))'
    },
    {
      query: 'ALTER TABLE padanam_sets ADD COLUMN IF NOT EXISTS main_topic TEXT NOT NULL DEFAULT \'\''
    },
    {
      query: 'ALTER TABLE padanam_sets ADD COLUMN IF NOT EXISTS sub_topic TEXT NOT NULL DEFAULT \'\''
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
      query: 'CREATE TABLE IF NOT EXISTS padanam_deck_progress (' +
        'workspace TEXT NOT NULL, card_key TEXT NOT NULL, status TEXT NOT NULL DEFAULT \'seen\', ' +
        'updated_ms BIGINT NOT NULL, payload JSONB NOT NULL, PRIMARY KEY (workspace, card_key))'
    },
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_ai_settings (' +
        'id TEXT PRIMARY KEY, payload JSONB NOT NULL, updated_ms BIGINT NOT NULL)'
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

/**
 * Schema for the optional scanned-deck database. It holds only deck cards, so
 * it can live in a different Neon project from the account library.
 */
let deckSchemaReady = null;

export async function ensureDeckSchema(config) {
  if (deckSchemaReady) return deckSchemaReady;
  deckSchemaReady = neonBatch(config, [
    {
      query: 'CREATE TABLE IF NOT EXISTS padanam_deck_cards (' +
        'deck_id TEXT NOT NULL, card_id TEXT NOT NULL, ' +
        'main_topic TEXT NOT NULL DEFAULT \'\', sub_topic TEXT NOT NULL DEFAULT \'\', ' +
        'language TEXT NOT NULL DEFAULT \'en\', stem TEXT NOT NULL, answer TEXT NOT NULL, ' +
        'explanation TEXT NOT NULL DEFAULT \'\', updated_ms BIGINT NOT NULL, ' +
        'payload JSONB NOT NULL DEFAULT \'{}\'::jsonb, PRIMARY KEY (deck_id, card_id))'
    },
    {
      query: 'CREATE INDEX IF NOT EXISTS padanam_deck_cards_updated_idx ' +
        'ON padanam_deck_cards (updated_ms DESC)'
    }
  ]).catch(function (err) {
    deckSchemaReady = null;
    throw err;
  });
  return deckSchemaReady;
}

/* ── Site AI settings ──────────────────────────────────── */
/**
 * The administrator saves the scanner provider, model, endpoint and key in
 * Neon, so changing the model no longer needs a redeploy. The key is read only
 * by the Functions and is never included in a browser or API response.
 */
export async function readAiSettings(config) {
  const result = await neonQuery(
    config,
    'SELECT payload, updated_ms FROM padanam_ai_settings WHERE id = \'ai\' LIMIT 1',
    []
  );
  const row = rows(result)[0];
  const payload = row ? payloadValue(row.payload) : null;
  if (!payload || typeof payload !== 'object') return null;
  if (!payload.updatedMs && row && row.updated_ms) {
    payload.updatedMs = Number(row.updated_ms) || 0;
  }
  return payload;
}

export async function writeAiSettings(config, payload) {
  const updatedMs = Number(payload && payload.updatedMs) || Date.now();
  const body = Object.assign({}, payload, { updatedMs: updatedMs });
  await neonQuery(
    config,
    'INSERT INTO padanam_ai_settings (id, payload, updated_ms) VALUES ($1, $2::jsonb, $3) ' +
      'ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, updated_ms = EXCLUDED.updated_ms',
    ['ai', JSON.stringify(body), updatedMs]
  );
  return body;
}

export async function deleteAiSettings(config) {
  await neonQuery(config, 'DELETE FROM padanam_ai_settings WHERE id = \'ai\'', []);
}

/* ── Neon client ───────────────────────────────────────── */
export async function neonQuery(config, sql, params) {
  return neonPost(config, { query: sql, params: params || [] });
}

export async function neonBatch(config, queries) {
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
    return 'Neon is rate-limiting requests. Wait a minute and try again.';
  }
  if (status >= 500) {
    return 'Neon is having trouble right now. Try again in a moment.';
  }
  return 'Neon returned HTTP ' + status + (detail ? ': ' + detail : '.');
}

/* ── Crypto ────────────────────────────────────────────── */
export async function sha256(value) {
  const data = new TextEncoder().encode(String(value));
  const hash = await crypto.subtle.digest('SHA-256', data);
  return bytesToHex(new Uint8Array(hash));
}

export async function tokensMatch(a, b) {
  const [left, right] = await Promise.all([sha256(a), sha256(b)]);
  return safeEqual(left, right);
}

export function safeEqual(a, b) {
  const left = String(a);
  const right = String(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i++) {
    diff |= (left.charCodeAt(i) || 0) ^ (right.charCodeAt(i) || 0);
  }
  return diff === 0;
}

export function randomBase64(bytes) {
  const buffer = new Uint8Array(bytes || 16);
  crypto.getRandomValues(buffer);
  return bytesToBase64(buffer);
}

export function randomId(prefix) {
  return (prefix || 'id') + '_' + crypto.randomUUID().replace(/-/g, '');
}

export async function hashPassword(password, saltBase64, iterations) {
  const salt = base64ToBytes(saltBase64);
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(String(password)),
    'PBKDF2',
    false,
    ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits({
    name: 'PBKDF2',
    hash: 'SHA-256',
    salt: salt,
    iterations: iterations || PBKDF2_ITERATIONS
  }, key, 256);
  return bytesToBase64(new Uint8Array(bits));
}

/* ── Sessions ──────────────────────────────────────────── */
export async function signSession(payload, secret) {
  const body = base64UrlEncode(JSON.stringify(payload));
  const signature = await hmacBase64Url(secret, body);
  return body + '.' + signature;
}

export async function verifySessionToken(token, secret) {
  const parts = String(token || '').split('.');
  if (parts.length !== 2) return null;
  const expected = await hmacBase64Url(secret, parts[0]);
  if (!safeEqual(parts[1], expected)) return null;

  let payload;
  try {
    payload = JSON.parse(base64UrlDecode(parts[0]));
  } catch (err) {
    return null;
  }
  if (!payload || typeof payload !== 'object') return null;
  if (!payload.exp || Number(payload.exp) * 1000 <= Date.now()) return null;
  return payload;
}

export async function sessionFromRequest(request, env) {
  const header = request.headers.get('Authorization') || '';
  if (!header) return { user: null };
  if (!/^Bearer\s+/i.test(header)) {
    return { user: null, error: 'Send a Bearer session token.', status: 401 };
  }

  const auth = authConfig(env);
  if (!auth.configured) {
    return { user: null, error: auth.error || 'Accounts are not configured.', status: 503 };
  }

  const payload = await verifySessionToken(header.replace(/^Bearer\s+/i, '').trim(), auth.secret);
  if (!payload) {
    return { user: null, error: 'Your session expired. Sign in again.', status: 401 };
  }

  if (payload.r === 'admin') {
    if (String(payload.u || '') !== auth.adminUsername) {
      return { user: null, error: 'This admin session is no longer valid. Sign in again.', status: 401 };
    }
    return {
      user: {
        id: 'admin',
        username: auth.adminUsername,
        displayName: String(payload.n || 'Administrator'),
        role: 'admin'
      }
    };
  }

  const config = databaseConfig(env);
  if (!config.configured) {
    return { user: null, error: config.error || 'The account database is not configured.', status: 503 };
  }

  try {
    await ensureSchema(config);
    const result = await neonQuery(
      config,
      'SELECT id, username, display_name, role, created_ms FROM padanam_users WHERE id = $1',
      [String(payload.sub || '')]
    );
    const row = rows(result)[0];
    if (!row) {
      return { user: null, error: 'This account no longer exists. Ask the admin for a new one.', status: 401 };
    }
    return { user: userFromRow(row) };
  } catch (err) {
    return {
      user: null,
      error: err && err.message ? err.message : 'The account could not be verified.',
      status: 502
    };
  }
}

export async function requireAdmin(request, env) {
  const session = await sessionFromRequest(request, env);
  if (!session.user) {
    return {
      error: session.error || 'Sign in to continue.',
      status: session.status || 401
    };
  }
  if (session.user.role !== 'admin') {
    return { error: 'Admin access is required.', status: 403 };
  }
  return { user: session.user };
}

export function userFromRow(row) {
  return {
    id: String(row.id || ''),
    username: String(row.username || ''),
    displayName: String(row.display_name || row.username || ''),
    role: row.role === 'admin' ? 'admin' : 'user',
    createdAt: row.created_ms ? new Date(Number(row.created_ms)).toISOString() : ''
  };
}

/* ── HTTP helpers ──────────────────────────────────────── */
export async function readBody(request, maxBytes, label) {
  const limit = maxBytes || 6 * 1024 * 1024;
  const length = parseInt(request.headers.get('content-length') || '0', 10);
  if (length > limit) throw new Error((label || 'The request') + ' is too large.');

  const text = await request.text();
  if (!text) throw new Error('Send a JSON request body.');
  if (text.length > limit) throw new Error((label || 'The request') + ' is too large.');

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

export function sameOrigin(request) {
  const origin = request.headers.get('Origin');
  if (!origin) return true;
  try {
    return origin === new URL(request.url).origin;
  } catch (err) {
    return false;
  }
}

export function json(body, status, extraHeaders) {
  const headers = Object.assign({
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  }, extraHeaders || {});

  return new Response(JSON.stringify(body), { status: status || 200, headers });
}

export function rows(result) {
  return result && Array.isArray(result.rows) ? result.rows : [];
}

export function countOf(result) {
  const row = rows(result)[0];
  return row ? Number(row.count || 0) : 0;
}

export function payloadValue(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') {
    try { return JSON.parse(value); } catch (err) { return null; }
  }
  return value;
}

/* ── Encoding ──────────────────────────────────────────── */
function bytesToHex(bytes) {
  return Array.from(bytes).map(function (byte) {
    return byte.toString(16).padStart(2, '0');
  }).join('');
}

function bytesToBase64(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

function base64ToBytes(value) {
  const binary = atob(String(value || ''));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function base64UrlEncode(value) {
  return bytesToBase64(new TextEncoder().encode(String(value)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function base64UrlDecode(value) {
  const normalized = String(value || '').replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '==='.slice((normalized.length + 3) % 4);
  return new TextDecoder().decode(base64ToBytes(padded));
}

async function hmacBase64Url(secret, value) {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(String(secret)),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(String(value)));
  return bytesToBase64(new Uint8Array(signature))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}
