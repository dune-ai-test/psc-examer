/* =========================================================
   Padanam web — optional Neon cloud sync
   The Neon connection string stays in Cloudflare Pages
   secrets. The browser keeps only the sync code the owner
   typed in, and syncs sets, questions, attempts and profile.
   Photos and AI keys are never sent.
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  if (!A) return;

  var ENDPOINT = './api/sync';
  var CONFIG_KEY = 'padanam.sync.v1';
  var MIN_TOKEN = 16;
  var MAX_TOMBSTONES = 5000;
  var SYNC_TIMEOUT = 60000;
  var AUTO_DELAY = 12000;

  var state = {
    checked: false,
    checking: false,
    available: false,
    configured: false,
    accounts: false,
    host: '',
    error: '',
    syncing: false,
    lastSyncAt: '',
    lastResult: null
  };

  var config = readConfig();
  var tombstones = [];
  var tombstonesLoaded = false;
  var pending = false;
  var current = null;
  var scheduleTimer = null;
  var generation = 0;

  function accountMode() {
    return !!(A.auth && A.auth.isAccount());
  }

  function signedIn() {
    return !!(A.auth && A.auth.signedIn());
  }

  /* ── Local sync settings ─────────────────────────────── */
  function fingerprint(value) {
    var text = String(value || '');
    var left = 0x811c9dc5;
    var right = 0x01000193;
    for (var i = 0; i < text.length; i++) {
      var code = text.charCodeAt(i);
      left = Math.imul(left ^ code, 0x01000193) >>> 0;
      right = Math.imul(right + code, 0x85ebca6b) >>> 0;
    }
    return ('00000000' + left.toString(16)).slice(-8) + ('00000000' + right.toString(16)).slice(-8);
  }

  function readConfig() {
    try {
      var raw = window.localStorage.getItem(CONFIG_KEY);
      var data = raw ? JSON.parse(raw) : null;
      var token = data && typeof data.token === 'string' ? data.token : '';
      var stored = data && typeof data.identity === 'string' ? data.identity : '';
      return {
        token: token,
        auto: !!(data && data.auto),
        identity: token ? fingerprint(token) : stored
      };
    } catch (err) {
      return { token: '', auto: false, identity: '' };
    }
  }

  function saveConfig(next) {
    var token = String((next && next.token) || '').trim();
    var previousToken = config.token;
    var previousIdentity = config.identity || (previousToken ? fingerprint(previousToken) : '');
    var identity = token ? fingerprint(token) : previousIdentity;
    var tokenChanged = token !== previousToken;
    var changed = !!previousIdentity && !!token && previousIdentity !== identity;
    config = {
      token: token,
      auto: !!(next && next.auto),
      identity: identity
    };
    try {
      window.localStorage.setItem(CONFIG_KEY, JSON.stringify(config));
    } catch (err) {
      /* Private-mode storage failures are surfaced by the sync test. */
    }
    if (!config.token || !config.auto) cancelScheduled();
    if (tokenChanged) {
      generation += 1;
      state.lastSyncAt = '';
      state.lastResult = null;
    }
    if (changed) {
      writeTombstones([]);
      A.state.meta.syncEpochMs = 0;
      A.state.meta.lastSyncAt = '';
      A.save();
    }
    return { token: config.token, auto: config.auto };
  }

  function readTombstones() {
    try {
      var raw = window.localStorage.getItem(A.tombstoneKey());
      var list = raw ? JSON.parse(raw) : [];
      return Array.isArray(list) ? list.filter(function (item) {
        return item && (item.kind === 'set' || item.kind === 'attempt') && item.id;
      }) : [];
    } catch (err) {
      return [];
    }
  }

  function ensureTombstones() {
    if (!tombstonesLoaded) {
      tombstones = readTombstones();
      tombstonesLoaded = true;
    }
    return tombstones;
  }

  function writeTombstones(list) {
    tombstones = (Array.isArray(list) ? list : []).slice(-MAX_TOMBSTONES);
    tombstonesLoaded = true;
    try {
      window.localStorage.setItem(A.tombstoneKey(), JSON.stringify(tombstones));
    } catch (err) {
      /* Best effort; the next successful sync still uses the in-memory list. */
    }
  }

  function addTombstone(kind, id) {
    if (!id) return;
    var at = new Date().toISOString();
    var next = ensureTombstones().filter(function (item) {
      return !(item.kind === kind && item.id === id);
    });
    next.push({ kind: kind, id: String(id), deletedAt: at });
    writeTombstones(next);
  }

  function noteDeletedSet(id) {
    addTombstone('set', id);
  }

  function noteDeletedAttempt(id) {
    addTombstone('attempt', id);
  }

  function noteRestoredSet(id) {
    if (!id) return;
    var list = ensureTombstones();
    var next = list.filter(function (item) {
      return !(item.kind === 'set' && item.id === id);
    });
    if (next.length !== list.length) writeTombstones(next);
  }

  function clearTombstones() {
    writeTombstones([]);
  }

  /* ── HTTP ────────────────────────────────────────────── */
  function parseJson(text) {
    if (!text) return {};
    try {
      return JSON.parse(text);
    } catch (err) {
      return { raw: text };
    }
  }

  function request(body, allowError) {
    if (typeof fetch !== 'function') {
      return Promise.reject(new Error('This browser cannot reach the cloud sync endpoint.'));
    }

    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, SYNC_TIMEOUT);
    var init = {
      method: body ? 'POST' : 'GET',
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal: controller.signal
    };
    if (accountMode() && A.auth && A.auth.token()) {
      init.headers.Authorization = 'Bearer ' + A.auth.token();
    }
    if (body) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }

    return fetch(ENDPOINT, init).then(function (response) {
      return response.text().then(function (text) {
        var data = parseJson(text);
        if (response.status === 401 && accountMode() && A.auth) A.auth.expire();
        if (!response.ok || !data || data.ok === false || (!allowError && data.error)) {
          var message = data && data.error
            ? String(data.error)
            : response.status === 404
              ? 'No cloud sync function was found at /api/sync. Deploy the functions folder with the site.'
              : 'Cloud sync returned HTTP ' + response.status + '.';
          throw new Error(message);
        }
        return data;
      });
    }).catch(function (err) {
      if (err && err.name === 'AbortError') {
        throw new Error('Cloud sync timed out. Try again with a smaller library.');
      }
      throw err;
    }).finally(function () {
      window.clearTimeout(timer);
    });
  }

  /* ── Status ──────────────────────────────────────────── */
  function refreshStatus(force) {
    if (!/^https?:$/.test(window.location.protocol)) {
      state.checked = true;
      state.available = false;
      state.configured = false;
      state.error = 'Cloud sync only works on the deployed http(s) site.';
      return Promise.resolve(info());
    }
    if (state.checking) return state.promise;
    if (state.checked && !force) return Promise.resolve(info());

    state.checking = true;
    state.error = '';

    state.promise = request(null, true).then(function (data) {
      state.available = true;
      state.configured = !!data.configured;
      state.accounts = !!data.accounts;
      state.host = String(data.host || '');
      state.error = String(data.error || '');
      state.checked = true;
      return info();
    }).catch(function (err) {
      state.available = false;
      state.configured = false;
      state.accounts = false;
      state.error = err && err.message ? err.message : 'Could not reach the cloud sync function.';
      state.checked = true;
      return info();
    }).then(function (result) {
      state.checking = false;
      return result;
    });

    return state.promise;
  }

  async function test(token) {
    if (accountMode()) throw new Error('Signed-in accounts sync automatically; use Sync now instead.');
    var code = String(token || config.token || '').trim();
    if (!code) throw new Error('Enter your sync code first.');
    if (code.length < MIN_TOKEN) throw new Error('The sync code must be at least ' + MIN_TOKEN + ' characters.');

    var data = await request({ action: 'test', token: code });
    state.checked = true;
    state.available = true;
    state.configured = true;
    state.host = String(data.host || state.host || '');
    state.error = '';
    return data;
  }

  /* ── Merge ───────────────────────────────────────────── */
  function timeOf(value) {
    var ms = Date.parse(value);
    return isNaN(ms) ? 0 : ms;
  }

  function tombstoneMap(list) {
    var map = {};
    (list || []).forEach(function (item) {
      if (!item || !item.kind || !item.id) return;
      var ms = Number(item.deletedMs) || timeOf(item.deletedAt);
      var key = item.kind + ':' + item.id;
      if (!map[key] || ms > map[key]) map[key] = ms;
    });
    return map;
  }

  function mergeTimestamps(localList, remoteList) {
    var map = {};
    (localList || []).forEach(function (item) {
      if (!item || !item.id) return;
      map[item.kind + ':' + item.id] = Math.max(
        map[item.kind + ':' + item.id] || 0,
        timeOf(item.deletedAt)
      );
    });
    (remoteList || []).forEach(function (item) {
      if (!item || !item.id) return;
      map[item.kind + ':' + item.id] = Math.max(
        map[item.kind + ':' + item.id] || 0,
        Number(item.deletedMs) || timeOf(item.deletedAt)
      );
    });

    return Object.keys(map).map(function (key) {
      var split = key.indexOf(':');
      return {
        kind: key.slice(0, split),
        id: key.slice(split + 1),
        deletedAt: new Date(map[key]).toISOString()
      };
    });
  }

  function merge(local, remote) {
    var localEpoch = Number(local.meta && local.meta.syncEpochMs) || 0;
    var remoteEpoch = Number(remote.epochMs) || 0;
    var adopt = remoteEpoch > localEpoch;

    var mergedTombstones = mergeTimestamps(
      adopt ? [] : ensureTombstones(),
      remote.tombstones
    );
    var deleted = tombstoneMap(mergedTombstones);

    var sets = [];
    var attempts = [];
    var profile = local.profile;
    var profileUpdatedAt = (local.meta && (local.meta.profileUpdatedAt || local.meta.updatedAt)) || new Date().toISOString();
    var attemptsClearedAt = (local.meta && local.meta.attemptsClearedAt) || '';

    if (adopt) {
      sets = Array.isArray(remote.sets) ? remote.sets : [];
      attempts = Array.isArray(remote.attempts) ? remote.attempts : [];
      profile = remote.profile || {};
      profileUpdatedAt = remote.profileUpdatedMs
        ? new Date(Number(remote.profileUpdatedMs)).toISOString()
        : new Date().toISOString();
      attemptsClearedAt = Number(remote.attemptsClearedMs) > 0
        ? new Date(Number(remote.attemptsClearedMs)).toISOString()
        : '';
    } else {
      var setMap = {};
      var attemptMap = {};
      var clearedMs = Math.max(timeOf(attemptsClearedAt), Number(remote.attemptsClearedMs) || 0);

      function considerSet(set) {
        if (!set || !set.id) return;
        var ms = timeOf(set.updatedAt || set.createdAt);
        if (deleted['set:' + set.id] && ms <= deleted['set:' + set.id]) return;
        var previous = setMap[set.id];
        if (!previous || ms > timeOf(previous.updatedAt || previous.createdAt)) setMap[set.id] = set;
      }

      (local.sets || []).forEach(considerSet);
      (remote.sets || []).forEach(considerSet);
      sets = Object.keys(setMap).map(function (id) { return setMap[id]; });

      function considerAttempt(attempt) {
        if (!attempt || !attempt.id) return;
        var ms = timeOf(attempt.finishedAt);
        if (ms <= clearedMs) return;
        if (deleted['attempt:' + attempt.id] && ms <= deleted['attempt:' + attempt.id]) return;
        var previous = attemptMap[attempt.id];
        if (!previous || ms > timeOf(previous.finishedAt)) attemptMap[attempt.id] = attempt;
      }

      (local.attempts || []).forEach(considerAttempt);
      (remote.attempts || []).forEach(considerAttempt);
      attempts = Object.keys(attemptMap).map(function (id) { return attemptMap[id]; });

      if (Number(remote.profileUpdatedMs) > timeOf(profileUpdatedAt) && remote.profile) {
        profile = remote.profile;
        profileUpdatedAt = new Date(Number(remote.profileUpdatedMs)).toISOString();
      }
      if (clearedMs > 0) attemptsClearedAt = new Date(clearedMs).toISOString();
    }

    var meta = Object.assign({}, local.meta || {});
    meta.profileUpdatedAt = profileUpdatedAt;
    meta.attemptsClearedAt = attemptsClearedAt;
    meta.syncEpochMs = Math.max(localEpoch, remoteEpoch);
    meta.lastSyncAt = new Date().toISOString();

    sets = sets.slice().sort(function (a, b) {
      return timeOf(b.updatedAt || b.createdAt) - timeOf(a.updatedAt || a.createdAt);
    });
    attempts = attempts.slice().sort(function (a, b) {
      return timeOf(b.finishedAt) - timeOf(a.finishedAt);
    });

    var mergedState = Object.assign({}, local, {
      profile: profile,
      sets: sets,
      attempts: attempts,
      meta: meta
    });

    writeTombstones(mergedTombstones);
    return {
      state: A.normalizeState(mergedState),
      adopted: adopt,
      epochMs: meta.syncEpochMs
    };
  }

  /* ── Sync ────────────────────────────────────────────── */
  function snapshot() {
    return {
      sets: A.state.sets || [],
      attempts: A.state.attempts || [],
      profile: A.state.profile || {},
      profileUpdatedAt: A.state.meta.profileUpdatedAt || A.state.meta.updatedAt,
      attemptsClearedAt: A.state.meta.attemptsClearedAt || '',
      tombstones: ensureTombstones().map(function (item) {
        return { kind: item.kind, id: item.id, deletedAt: item.deletedAt };
      })
    };
  }

  function run(options) {
    options = options || {};
    if (state.syncing) {
      pending = true;
      return current;
    }

    var useAccount = accountMode();
    if (useAccount && !signedIn()) {
      return Promise.reject(new Error('Sign in to sync your account.'));
    }
    if (!useAccount && !config.token) {
      return Promise.reject(new Error('Enter your sync code in Settings first.'));
    }

    state.syncing = true;
    state.error = '';

    var runGeneration = generation;
    var token = config.token;

    current = (async function () {
      var status = await refreshStatus();
      if (!status.configured) {
        throw new Error(status.error || 'Cloud sync is not configured on this server.');
      }

      var pullBody = { action: 'pull' };
      if (!useAccount) pullBody.token = token;
      var remote = await request(pullBody);
      if (runGeneration !== generation) {
        throw new Error('The sync account changed before this sync finished. Sync again.');
      }
      var merged = merge(A.state, remote);
      A.state = merged.state;
      A.save();

      var payload = snapshot();
      payload.action = 'push';
      if (!useAccount) payload.token = token;
      var pushed = await request(payload);

      if (runGeneration !== generation) {
        return {
          sets: Number(pushed.sets) || 0,
          attempts: Number(pushed.attempts) || 0,
          adopted: merged.adopted
        };
      }

      A.state.meta.lastSyncAt = new Date().toISOString();
      A.save();
      state.lastSyncAt = A.state.meta.lastSyncAt;
      state.lastResult = {
        sets: Number(pushed.sets) || A.state.sets.length,
        attempts: Number(pushed.attempts) || A.state.attempts.length,
        adopted: merged.adopted
      };
      state.error = '';
      return state.lastResult;
    })().catch(function (err) {
      state.error = err && err.message ? err.message : 'Cloud sync failed.';
      throw err;
    }).finally(function () {
      state.syncing = false;
      current = null;
      if (pending) {
        pending = false;
        window.setTimeout(function () {
          run({ silent: true }).catch(function () {});
        }, 800);
      }
    });

    return current;
  }

  function schedule() {
    if (accountMode()) {
      if (!signedIn()) return;
    } else if (!config.auto || !config.token) {
      return;
    }
    if (scheduleTimer) window.clearTimeout(scheduleTimer);
    scheduleTimer = window.setTimeout(function () {
      scheduleTimer = null;
      run({ silent: true }).catch(function () {});
    }, AUTO_DELAY);
  }

  function cancelScheduled() {
    if (scheduleTimer) {
      window.clearTimeout(scheduleTimer);
      scheduleTimer = null;
    }
    pending = false;
  }

  function waitForIdle() {
    if (!current) return Promise.resolve();
    return current.catch(function () {});
  }

  function start() {
    state.lastSyncAt = (A.state.meta && A.state.meta.lastSyncAt) || '';
    refreshStatus().then(function (status) {
      if (A.refresh && document.getElementById('cloudBackup')) A.refresh();
      if (!status.configured) return;
      if (accountMode()) {
        if (signedIn()) run({ silent: true }).catch(function () {});
        return;
      }
      if (config.token && config.auto) {
        run({ silent: true }).catch(function () {});
      }
    });
    if (typeof window.addEventListener === 'function') {
      window.addEventListener('online', function () { schedule(); });
    }
  }

  function reload() {
    cancelScheduled();
    tombstonesLoaded = false;
    tombstones = [];
    state.checked = false;
    state.checking = false;
    state.error = '';
    state.lastSyncAt = '';
    state.lastResult = null;
    generation += 1;
  }

  async function replaceCloud() {
    if (accountMode()) {
      if (!signedIn()) throw new Error('Sign in to replace the cloud copy.');
      if (!A.auth.isAdmin()) throw new Error('Only the administrator can replace the cloud copy.');
    } else if (!config.token) {
      return null;
    }
    cancelScheduled();
    await waitForIdle();
    var body = snapshot();
    body.action = 'replace';
    if (!accountMode()) body.token = config.token;
    var result = await request(body);
    if (result && result.epochMs) {
      A.state.meta.syncEpochMs = Number(result.epochMs) || 0;
      A.state.meta.lastSyncAt = new Date().toISOString();
      A.save();
    }
    writeTombstones([]);
    return result;
  }

  function info() {
    return {
      checked: state.checked,
      checking: state.checking,
      available: state.available,
      configured: state.configured,
      accounts: state.accounts || accountMode(),
      host: state.host,
      error: state.error,
      syncing: state.syncing,
      lastSyncAt: state.lastSyncAt || (A.state.meta && A.state.meta.lastSyncAt) || '',
      lastResult: state.lastResult
    };
  }

  A.sync = {
    config: function () { return { token: config.token, auto: config.auto }; },
    saveConfig: saveConfig,
    status: refreshStatus,
    info: info,
    test: test,
    sync: run,
    schedule: schedule,
    start: start,
    reload: reload,
    noteDeletedSet: noteDeletedSet,
    noteDeletedAttempt: noteDeletedAttempt,
    noteRestoredSet: noteRestoredSet,
    clearTombstones: clearTombstones,
    replaceCloud: replaceCloud,
    merge: merge
  };
})();
