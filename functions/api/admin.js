/* =========================================================
   Padanam — admin console API for Cloudflare Pages
   Only the bootstrap admin can list accounts, create users
   and assign libraries. There is deliberately no delete or
   password-reset endpoint.
   ========================================================= */

import {
  databaseConfig,
  ensureSchema,
  hashPassword,
  json,
  neonBatch,
  neonQuery,
  PBKDF2_ITERATIONS,
  randomBase64,
  randomId,
  readBody,
  requireAdmin,
  reservedUsername,
  rows,
  sameOrigin,
  userFromRow,
  validUsername,
  MAX_PASSWORD_LENGTH,
  MIN_PASSWORD_LENGTH,
  MIN_USERNAME_LENGTH,
  MAX_USERNAME_LENGTH
} from '../_shared.js';

const MAX_BODY_BYTES = 256 * 1024;
const MAX_ASSIGNED_SETS = 500;

export async function onRequest(context) {
  const request = context.request;
  if (request.method === 'GET') {
    const guard = await requireAdmin(request, context.env);
    if (guard.error) return json({ error: guard.error }, guard.status);
    return json({ ok: true, admin: guard.user });
  }

  if (request.method !== 'POST') {
    return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
  }

  if (!sameOrigin(request)) {
    return json({ error: 'This endpoint only accepts requests from the app.' }, 403);
  }

  const guard = await requireAdmin(request, context.env);
  if (guard.error) return json({ error: guard.error }, guard.status);

  const database = databaseConfig(context.env);
  if (!database.configured) {
    return json({ error: database.error || 'The account database is not configured.' }, 503);
  }

  let body;
  try {
    body = await readBody(request, MAX_BODY_BYTES, 'The admin request');
  } catch (err) {
    return json({ error: err.message || 'Send a JSON request body.' }, 400);
  }

  const action = String(body.action || '').trim();

  try {
    await ensureSchema(database);

    if (action === 'users') {
      return json(await listUsers(database, guard.user));
    }
    if (action === 'create-user') {
      return await createUser(database, guard.user, body, context.env);
    }
    if (action === 'assign-sets') {
      return await assignSets(database, guard.user, body);
    }
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'The admin action failed.' }, 502);
  }

  return json({ error: 'Unknown action. Use users, create-user or assign-sets.' }, 400);
}

/* ── Accounts ──────────────────────────────────────────── */
async function listUsers(database, admin) {
  const adminWorkspace = workspaceFor(admin);
  const results = await neonBatch(database, [
    {
      query: 'SELECT id, username, display_name, role, created_ms FROM padanam_users ORDER BY created_ms ASC',
      params: []
    },
    {
      query: 'SELECT workspace, id FROM padanam_sets WHERE workspace LIKE \'user:%\''
    },
    {
      query: 'SELECT id, payload->>\'title\' AS title, payload->>\'subject\' AS subject, ' +
        'jsonb_array_length(COALESCE(payload->\'questions\', \'[]\'::jsonb)) AS question_count ' +
        'FROM padanam_sets WHERE workspace = $1 ORDER BY updated_ms DESC',
      params: [adminWorkspace]
    }
  ]);

  const assignments = {};
  rows(results[1]).forEach(function (row) {
    const workspace = String(row.workspace || '');
    if (workspace.indexOf('user:') !== 0) return;
    const userId = workspace.slice(5);
    if (!assignments[userId]) assignments[userId] = [];
    assignments[userId].push(String(row.id || ''));
  });

  return {
    ok: true,
    users: rows(results[0]).map(function (row) {
      const user = userFromRow(row);
      user.setIds = assignments[user.id] || [];
      return user;
    }),
    sets: rows(results[2]).map(function (row) {
      return {
        id: String(row.id || ''),
        title: String(row.title || 'Untitled set'),
        subject: String(row.subject || 'General Knowledge'),
        questions: Number(row.question_count || 0)
      };
    })
  };
}

async function createUser(database, admin, body, env) {
  const username = String(body.username || '').trim().toLowerCase();
  const displayName = String(body.displayName || '').trim().slice(0, 80) || username;
  const password = String(body.password || '');

  if (username.length < MIN_USERNAME_LENGTH || username.length > MAX_USERNAME_LENGTH) {
    return json({
      error: 'The username must be ' + MIN_USERNAME_LENGTH + '–' + MAX_USERNAME_LENGTH + ' characters.'
    }, 400);
  }
  if (!validUsername(username)) {
    return json({ error: 'Use lowercase letters, numbers, dots, dashes or underscores. Start with a letter or number.' }, 400);
  }
  if (reservedUsername(username, env)) {
    return json({ error: 'That username is reserved. Choose another one.' }, 409);
  }
  if (!displayName) {
    return json({ error: 'Add a display name.' }, 400);
  }
  if (password.length < MIN_PASSWORD_LENGTH) {
    return json({ error: 'The password must be at least ' + MIN_PASSWORD_LENGTH + ' characters.' }, 400);
  }
  if (password.length > MAX_PASSWORD_LENGTH) {
    return json({ error: 'That password is too long.' }, 400);
  }

  const existing = await neonQuery(
    database,
    'SELECT id FROM padanam_users WHERE username = $1',
    [username]
  );
  if (rows(existing).length) {
    return json({ error: 'That username is already taken.' }, 409);
  }

  const salt = randomBase64(16);
  const passwordHash = await hashPassword(password, salt, PBKDF2_ITERATIONS);
  const id = randomId('usr');
  const createdMs = Date.now();

  try {
    await neonQuery(
      database,
      'INSERT INTO padanam_users (id, username, display_name, role, password_hash, salt, iterations, created_ms) ' +
        'VALUES ($1, $2, $3, \'user\', $4, $5, $6, $7)',
      [id, username, displayName, passwordHash, salt, PBKDF2_ITERATIONS, createdMs]
    );
  } catch (err) {
    if (/duplicate|unique/i.test(err && err.message ? err.message : '')) {
      return json({ error: 'That username is already taken.' }, 409);
    }
    throw err;
  }

  return json({
    ok: true,
    user: {
      id,
      username,
      displayName,
      role: 'user',
      createdAt: new Date(createdMs).toISOString(),
      setIds: []
    }
  }, 201);
}

/* ── Library assignment ────────────────────────────────── */
async function assignSets(database, admin, body) {
  const userId = String(body.userId || '').trim();
  if (!userId) return json({ error: 'Choose an account.' }, 400);

  const rawIds = Array.isArray(body.setIds) ? body.setIds : [];
  const setIds = [];
  rawIds.forEach(function (value) {
    const id = String(value || '').trim();
    if (id && id.length <= 100 && setIds.indexOf(id) < 0) setIds.push(id);
  });
  if (!setIds.length) {
    return json({ error: 'Select at least one set to assign.' }, 400);
  }
  if (setIds.length > MAX_ASSIGNED_SETS) {
    return json({ error: 'Assign at most ' + MAX_ASSIGNED_SETS + ' sets at once.' }, 400);
  }

  const userResult = await neonQuery(
    database,
    'SELECT id, username, display_name, role, created_ms FROM padanam_users WHERE id = $1',
    [userId]
  );
  const userRow = rows(userResult)[0];
  if (!userRow) return json({ error: 'That account no longer exists.' }, 404);

  const adminWorkspace = workspaceFor(admin);
  const selected = await neonQuery(
    database,
    'SELECT id, payload FROM padanam_sets WHERE workspace = $1 ' +
      'AND id IN (SELECT jsonb_array_elements_text($2::jsonb))',
    [adminWorkspace, JSON.stringify(setIds)]
  );

  const nowIso = new Date().toISOString();
  const nowMs = Date.now();
  const items = rows(selected).map(function (row) {
    let payload = row.payload;
    if (typeof payload === 'string') {
      try { payload = JSON.parse(payload); } catch (err) { payload = null; }
    }
    if (!payload || typeof payload !== 'object') return null;
    return {
      id: String(row.id || payload.id || ''),
      updatedMs: nowMs,
      payload: Object.assign({}, payload, {
        id: String(payload.id || row.id || ''),
        updatedAt: nowIso
      })
    };
  }).filter(function (item) {
    return item && item.id;
  });

  if (!items.length) {
    return json({ error: 'Those sets are not in the admin library yet. Press Sync now, then try again.' }, 404);
  }

  const workspace = 'user:' + userId;
  const results = await neonBatch(database, [
    {
      query: 'INSERT INTO padanam_sets (workspace, id, updated_ms, payload) ' +
        'SELECT $1, item->>\'id\', (item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (workspace, id) DO UPDATE SET updated_ms = EXCLUDED.updated_ms, payload = EXCLUDED.payload ' +
        'WHERE EXCLUDED.updated_ms > padanam_sets.updated_ms',
      params: [workspace, JSON.stringify(items)]
    },
    {
      query: 'SELECT COUNT(*)::int AS count FROM padanam_sets WHERE workspace = $1',
      params: [workspace]
    }
  ]);

  return json({
    ok: true,
    assigned: items.length,
    user: userFromRow(userRow),
    total: countOf(results[1])
  });
}

function workspaceFor(user) {
  return 'admin:' + user.username;
}

function countOf(result) {
  const row = rows(result)[0];
  return row ? Number(row.count || 0) : 0;
}
