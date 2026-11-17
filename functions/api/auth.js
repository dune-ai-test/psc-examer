/* =========================================================
   Padanam — accounts for Cloudflare Pages
   The admin account is bootstrapped from Pages secrets.
   Users are created from the admin console and stored in
   Neon with PBKDF2 password hashes. Sessions are stateless
   HMAC-signed tokens kept in the browser.
   ========================================================= */

import {
  authConfig,
  databaseConfig,
  ensureSchema,
  hashPassword,
  json,
  neonQuery,
  PBKDF2_ITERATIONS,
  readBody,
  rows,
  safeEqual,
  SESSION_TTL_SECONDS,
  sessionFromRequest,
  signSession,
  tokensMatch,
  userFromRow,
  MAX_PASSWORD_LENGTH,
  MIN_PASSWORD_LENGTH
} from '../_shared.js';

const MAX_BODY_BYTES = 16 * 1024;
const RATE_WINDOW_MS = 10 * 60 * 1000;
const MAX_FAILURES = 8;

/* Best-effort in-isolate rate limiting. Cloudflare may run more
   than one isolate, so this slows guessing without pretending
   to be a global account lock. */
const failures = new Map();

export function onRequest(context) {
  if (context.request.method === 'GET') {
    return json(statusPayload(context.env), 200);
  }
  if (context.request.method === 'POST') {
    return handlePost(context);
  }
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

function statusPayload(env) {
  const database = databaseConfig(env);
  const auth = authConfig(env);
  const errors = [];
  if (!database.configured) errors.push(database.error);
  if (!auth.configured) errors.push(auth.error);

  return {
    ok: true,
    provider: 'neon',
    accounts: true,
    configured: database.configured && auth.configured,
    host: database.host,
    error: errors.filter(Boolean).join(' ')
  };
}

async function handlePost(context) {
  if (!sameOriginRequest(context.request)) {
    return json({ error: 'This endpoint only accepts requests from the app.' }, 403);
  }

  const database = databaseConfig(context.env);
  const auth = authConfig(context.env);

  let body;
  try {
    body = await readBody(context.request, MAX_BODY_BYTES, 'The sign-in request');
  } catch (err) {
    return json({ error: err.message || 'Send a JSON request body.' }, 400);
  }

  const action = String(body.action || '').trim();

  if (action === 'session') {
    const session = await sessionFromRequest(context.request, context.env);
    if (!session.user) {
      return json({ error: session.error || 'Sign in to continue.' }, session.status || 401);
    }
    return json({ ok: true, user: session.user });
  }

  if (action === 'logout') {
    return json({ ok: true });
  }

  if (action !== 'login') {
    return json({ error: 'Unknown action. Use login, session or logout.' }, 400);
  }

  if (!auth.configured) {
    return json({ error: auth.error || 'Accounts are not configured on this site.' }, 503);
  }
  if (!database.configured) {
    return json({ error: database.error || 'The account database is not configured.' }, 503);
  }

  const username = String(body.username || '').trim().toLowerCase();
  const password = String(body.password || '');
  if (!username || !password) {
    return json({ error: 'Enter your username and password.' }, 400);
  }
  if (password.length > MAX_PASSWORD_LENGTH) {
    return json({ error: 'That password is too long.' }, 400);
  }

  const key = rateKey(context.request, username);
  if (isRateLimited(key)) {
    return json({ error: 'Too many attempts. Wait a few minutes and try again.' }, 429);
  }

  let user = null;
  try {
    if (username === auth.adminUsername) {
      const ok = await tokensMatch(password, auth.adminPassword);
      if (ok) {
        user = {
          id: 'admin',
          username: auth.adminUsername,
          displayName: 'Administrator',
          role: 'admin'
        };
      }
    } else {
      await ensureSchema(database);
      const result = await neonQuery(
        database,
        'SELECT id, username, display_name, role, password_hash, salt, iterations, created_ms ' +
          'FROM padanam_users WHERE username = $1',
        [username]
      );
      const row = rows(result)[0];
      if (row) {
        const hash = await hashPassword(password, row.salt, Number(row.iterations) || PBKDF2_ITERATIONS);
        if (safeEqual(hash, String(row.password_hash || ''))) user = userFromRow(row);
      } else {
        /* Burn a similar amount of time so unknown usernames are not
           obviously faster to reject. */
        await hashPassword(password, 'cGFkYW5hbS1kdW1teS1zYWx0', PBKDF2_ITERATIONS);
      }
    }
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'Sign-in could not be completed.' }, 502);
  }

  if (!user) {
    recordFailure(key);
    return json({ error: 'That username or password is not correct.' }, 401);
  }

  if (user.role !== 'admin' && password.length < MIN_PASSWORD_LENGTH) {
    /* Created accounts are already validated; this only guards odd rows. */
    recordFailure(key);
    return json({ error: 'That username or password is not correct.' }, 401);
  }

  clearFailures(key);

  const now = Math.floor(Date.now() / 1000);
  const token = await signSession({
    sub: user.id,
    u: user.username,
    n: user.displayName,
    r: user.role,
    iat: now,
    exp: now + SESSION_TTL_SECONDS
  }, auth.secret);

  return json({
    ok: true,
    token,
    expiresAt: new Date((now + SESSION_TTL_SECONDS) * 1000).toISOString(),
    user
  });
}

/* ── Rate limiting ─────────────────────────────────────── */
function rateKey(request, username) {
  const ip = String(
    request.headers.get('CF-Connecting-IP') ||
    request.headers.get('X-Forwarded-For') ||
    'unknown'
  ).split(',')[0].trim();
  return ip + '|' + username;
}

function isRateLimited(key) {
  const entry = failures.get(key);
  if (!entry) return false;
  if (Date.now() - entry.first > RATE_WINDOW_MS) {
    failures.delete(key);
    return false;
  }
  return entry.count >= MAX_FAILURES;
}

function recordFailure(key) {
  const now = Date.now();
  const entry = failures.get(key);
  if (!entry || now - entry.first > RATE_WINDOW_MS) {
    failures.set(key, { first: now, count: 1 });
  } else {
    entry.count += 1;
  }

  if (failures.size > 500) {
    failures.forEach(function (value, name) {
      if (now - value.first > RATE_WINDOW_MS) failures.delete(name);
    });
  }
}

function clearFailures(key) {
  failures.delete(key);
}

function sameOriginRequest(request) {
  const origin = request.headers.get('Origin');
  if (!origin) return true;
  try {
    return origin === new URL(request.url).origin;
  } catch (err) {
    return false;
  }
}
