/* =========================================================
   Padanam — Neon cloud sync for Cloudflare Pages
   Signed-in accounts sync with a Bearer session. The legacy
   SYNC_TOKEN path stays available for older self-hosted
   deployments. Sets, questions, attempts, deck progress and
   profile sync; photos and AI keys never leave the browser.
   ========================================================= */

import {
  authConfig,
  countOf,
  databaseConfig,
  ensureSchema,
  json,
  neonBatch,
  neonQuery,
  payloadValue,
  readBody,
  rows,
  sameOrigin,
  sessionFromRequest,
  sha256,
  tokensMatch
} from '../_shared.js';

const MIN_TOKEN_LENGTH = 16;
const MAX_SETS = 1000;
const MAX_ATTEMPTS = 5000;
const MAX_TOMBSTONES = 5000;
const MAX_DECK_CARDS = 20000;
const MAX_BODY_BYTES = 6 * 1024 * 1024;

export function onRequest(context) {
  if (context.request.method === 'GET') {
    return json(statusPayload(context.env), 200);
  }
  if (context.request.method === 'POST') {
    return handleSync(context);
  }
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

function legacyConfig(env) {
  const token = String((env && env.SYNC_TOKEN) || '').trim();
  return {
    token,
    configured: token.length >= MIN_TOKEN_LENGTH,
    error: token && token.length < MIN_TOKEN_LENGTH
      ? 'SYNC_TOKEN must be at least ' + MIN_TOKEN_LENGTH + ' characters.'
      : ''
  };
}

function statusPayload(env) {
  const database = databaseConfig(env);
  const auth = authConfig(env);
  const legacy = legacyConfig(env);
  const accounts = database.configured && auth.configured;
  const configured = database.configured && (accounts || legacy.configured);

  let error = database.error || '';
  if (!error && !accounts && !legacy.configured) {
    error = legacy.error ||
      'Set AUTH_SECRET, ADMIN_USERNAME and ADMIN_PASSWORD for accounts, or SYNC_TOKEN for legacy sync.';
  }

  return {
    ok: true,
    provider: 'neon',
    configured: configured,
    accounts: accounts,
    host: database.host,
    error: error
  };
}

async function handleSync(context) {
  const database = databaseConfig(context.env);
  if (!database.configured) {
    return json({ error: database.error || 'Cloud sync is not configured.' }, 503);
  }
  if (!sameOrigin(context.request)) {
    return json({ error: 'This endpoint only accepts requests from the app.' }, 403);
  }

  let body;
  try {
    body = await readBody(context.request, MAX_BODY_BYTES, 'The sync payload');
  } catch (err) {
    return json({ error: err.message || 'Send a JSON request body.' }, 400);
  }

  const action = String(body.action || '').trim();
  if (['test', 'pull', 'push', 'replace'].indexOf(action) < 0) {
    return json({ error: 'Unknown action. Use test, pull, push or replace.' }, 400);
  }

  const authorization = context.request.headers.get('Authorization') || '';
  let workspace = '';
  let actor = 'legacy';
  let user = null;

  if (authorization) {
    const session = await sessionFromRequest(context.request, context.env);
    if (!session.user) {
      return json({ error: session.error || 'Sign in to sync your account.' }, session.status || 401);
    }
    user = session.user;
    actor = 'account';
    workspace = user.role === 'admin'
      ? 'admin:' + user.username
      : 'user:' + user.id;

    if (action === 'replace' && user.role !== 'admin') {
      return json({ error: 'Only the administrator can replace the cloud copy.' }, 403);
    }
  } else {
    const legacy = legacyConfig(context.env);
    if (!legacy.configured) {
      return json({
        error: 'Sign in to sync your account. Legacy sync is not configured on this server.'
      }, 401);
    }

    const token = String(body.token || '').trim();
    if (!token) {
      return json({ error: 'Sign in to sync your account.' }, 401);
    }
    if (token.length < MIN_TOKEN_LENGTH) {
      return json({ error: 'The sync code must be at least ' + MIN_TOKEN_LENGTH + ' characters.' }, 400);
    }
    if (!(await tokensMatch(token, legacy.token))) {
      return json({ error: 'That sync code is not correct.' }, 401);
    }
    workspace = await workspaceFor(token);
  }

  try {
    if (action === 'test') {
      await ensureSchema(database);
      await neonQuery(database, 'SELECT 1 AS ok');
      return json({
        ok: true,
        provider: 'neon',
        host: database.host,
        mode: actor,
        user: user,
        message: 'Connected to Neon.'
      });
    }

    if (action === 'pull') {
      await ensureSchema(database);
      return json(await pullWorkspace(database, workspace));
    }

    if (action === 'push') {
      let payload;
      try {
        payload = validateSnapshot(body);
      } catch (err) {
        return json({ error: err.message || 'The sync payload is invalid.' }, 400);
      }
      await ensureSchema(database);
      const counts = await pushWorkspace(database, workspace, payload);
      return json({ ok: true, sets: counts.sets, attempts: counts.attempts, deckCards: counts.deckCards });
    }

    if (action === 'replace') {
      let payload;
      try {
        payload = validateSnapshot(body);
      } catch (err) {
        return json({ error: err.message || 'The sync payload is invalid.' }, 400);
      }
      await ensureSchema(database);
      const result = await replaceWorkspace(database, workspace, payload);
      return json({
        ok: true,
        epochMs: result.epochMs,
        sets: result.sets,
        attempts: result.attempts,
        deckCards: result.deckCards
      });
    }

    return json({ error: 'Unknown action.' }, 400);
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'Cloud sync failed.' }, 502);
  }
}

async function workspaceFor(token) {
  const digest = await sha256('padanam-workspace:' + token);
  return digest.slice(0, 32);
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
    },
    {
      query: 'SELECT card_key, status, updated_ms, payload FROM padanam_deck_progress WHERE workspace = $1',
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
  const deckProgress = rows(results[4]).map(function (row) {
    const payload = payloadValue(row.payload);
    if (!payload || typeof payload !== 'object') return null;
    return Object.assign({}, payload, {
      key: String(row.card_key || payload.key || ''),
      status: String(row.status || payload.status || 'seen')
    });
  }).filter(Boolean);

  return {
    ok: true,
    sets: sets,
    attempts: attempts,
    deckProgress: deckProgress,
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
  const deckItems = snapshot.deckProgress.map(function (entry) {
    return {
      key: entry.key,
      status: entry.status,
      updatedMs: Date.parse(entry.updatedAt),
      payload: entry
    };
  });
  const profileUpdatedMs = Date.parse(snapshot.profileUpdatedAt) || 0;
  const attemptsClearedMs = Date.parse(snapshot.attemptsClearedAt) || 0;

  const results = await neonBatch(config, [
    {
      query: 'INSERT INTO padanam_sets (workspace, id, main_topic, sub_topic, updated_ms, payload) ' +
        'SELECT $1, item->>\'id\', ' +
        'COALESCE(LEFT(item->\'payload\'->>\'mainTopic\', 80), \'\'), ' +
        'COALESCE(LEFT(item->\'payload\'->>\'subTopic\', 120), \'\'), ' +
        '(item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (workspace, id) DO UPDATE SET updated_ms = EXCLUDED.updated_ms, ' +
        'payload = EXCLUDED.payload, main_topic = EXCLUDED.main_topic, sub_topic = EXCLUDED.sub_topic ' +
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
      query: 'INSERT INTO padanam_deck_progress (workspace, card_key, status, updated_ms, payload) ' +
        'SELECT $1, item->>\'key\', item->>\'status\', (item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (workspace, card_key) DO UPDATE SET status = EXCLUDED.status, ' +
        'updated_ms = EXCLUDED.updated_ms, payload = EXCLUDED.payload ' +
        'WHERE EXCLUDED.updated_ms > padanam_deck_progress.updated_ms ' +
        'OR (EXCLUDED.updated_ms = padanam_deck_progress.updated_ms AND EXCLUDED.status = \'unseen\')',
      params: [workspace, JSON.stringify(deckItems)]
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
    },
    {
      query: 'SELECT COUNT(*)::int AS count FROM padanam_deck_progress WHERE workspace = $1',
      params: [workspace]
    }
  ]);

  return {
    sets: countOf(results[results.length - 3]),
    attempts: countOf(results[results.length - 2]),
    deckCards: countOf(results[results.length - 1])
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
  const deckItems = snapshot.deckProgress.map(function (entry) {
    return {
      key: entry.key,
      status: entry.status,
      updatedMs: Date.parse(entry.updatedAt) || now,
      payload: entry
    };
  });
  const profileUpdatedMs = Date.parse(snapshot.profileUpdatedAt) || now;
  const attemptsClearedMs = Date.parse(snapshot.attemptsClearedAt) || 0;

  await neonBatch(config, [
    { query: 'DELETE FROM padanam_sets WHERE workspace = $1', params: [workspace] },
    { query: 'DELETE FROM padanam_attempts WHERE workspace = $1', params: [workspace] },
    { query: 'DELETE FROM padanam_tombstones WHERE workspace = $1', params: [workspace] },
    { query: 'DELETE FROM padanam_deck_progress WHERE workspace = $1', params: [workspace] },
    {
      query: 'INSERT INTO padanam_sets (workspace, id, main_topic, sub_topic, updated_ms, payload) ' +
        'SELECT $1, item->>\'id\', ' +
        'COALESCE(LEFT(item->\'payload\'->>\'mainTopic\', 80), \'\'), ' +
        'COALESCE(LEFT(item->\'payload\'->>\'subTopic\', 120), \'\'), ' +
        '(item->>\'updatedMs\')::bigint, item->\'payload\' ' +
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
      query: 'INSERT INTO padanam_deck_progress (workspace, card_key, status, updated_ms, payload) ' +
        'SELECT $1, item->>\'key\', item->>\'status\', (item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item',
      params: [workspace, JSON.stringify(deckItems)]
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
    deckCards: deckItems.length
  };
}

/* ── Validation ────────────────────────────────────────── */
function validateSnapshot(body) {
  const sets = Array.isArray(body.sets) ? body.sets : [];
  const attempts = Array.isArray(body.attempts) ? body.attempts : [];
  const tombstones = Array.isArray(body.tombstones) ? body.tombstones : [];
  const deckSource = Array.isArray(body.deckProgress)
    ? body.deckProgress
    : body.deckProgress && typeof body.deckProgress === 'object'
      ? Object.keys(body.deckProgress).map(function (key) {
        return Object.assign({ key: key }, body.deckProgress[key]);
      })
      : [];

  if (sets.length > MAX_SETS) throw new Error('Too many sets to sync at once.');
  if (attempts.length > MAX_ATTEMPTS) throw new Error('Too many attempts to sync at once.');
  if (tombstones.length > MAX_TOMBSTONES) throw new Error('Too many deleted items to sync at once.');
  if (deckSource.length > MAX_DECK_CARDS) throw new Error('Too many deck cards to sync at once.');

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

  const cleanDeckProgress = deckSource.map(function (entry) {
    if (!entry || typeof entry !== 'object') throw new Error('A deck card entry is invalid.');
    const key = String(entry.key || '').trim();
    if (key.length < 3 || key.length > 320 || !/^[a-z][a-z0-9_-]*:.+$/.test(key)) {
      throw new Error('A deck card key is invalid.');
    }
    if (!Date.parse(entry.updatedAt)) throw new Error('A deck card has an invalid updated date.');
    const status = entry.status === 'known' || entry.status === 'unseen'
      ? entry.status
      : entry.lastResult === 'gotit' ? 'known' : 'seen';
    const seenAt = status === 'unseen'
      ? ''
      : Date.parse(entry.seenAt) ? entry.seenAt : entry.updatedAt;
    return {
      key: key,
      status: status,
      lastResult: status === 'known' ? 'gotit' : status === 'seen' ? 'again' : '',
      seenAt: seenAt,
      updatedAt: entry.updatedAt
    };
  });

  const profile = body.profile && typeof body.profile === 'object' && !Array.isArray(body.profile)
    ? body.profile
    : {};
  const profileUpdatedAt = Date.parse(body.profileUpdatedAt) ? body.profileUpdatedAt : new Date().toISOString();
  const attemptsClearedAt = Date.parse(body.attemptsClearedAt) ? body.attemptsClearedAt : '';

  const payload = {
    sets: cleanSets,
    attempts: cleanAttempts,
    deckProgress: cleanDeckProgress,
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
