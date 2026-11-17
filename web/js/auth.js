/* =========================================================
   Padanam web — accounts and sessions
   The deployed app signs in against /api/auth. On file:// and
   a local development origin without Functions it falls back
   to a local admin so the offline app still works.
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  if (!A) return;

  var SESSION_KEY = 'padanam.auth.v1';
  var PROBE_TIMEOUT = 8000;
  var LOCAL_USER = {
    id: 'local',
    username: 'local',
    displayName: 'Local admin',
    role: 'admin'
  };

  var auth = {
    mode: 'checking',
    user: null,
    token: '',
    expiresAt: '',
    error: '',
    offline: false,
    checked: false
  };

  var stored = readStored();

  function readStored() {
    try {
      var raw = window.localStorage.getItem(SESSION_KEY);
      var data = raw ? JSON.parse(raw) : null;
      if (!data || typeof data !== 'object') return null;
      var token = typeof data.token === 'string' ? data.token : '';
      var user = data.user && typeof data.user === 'object' ? data.user : null;
      if (!token || !user || !user.id || !user.role) return null;
      return {
        token: token,
        user: user,
        expiresAt: String(data.expiresAt || '')
      };
    } catch (err) {
      return null;
    }
  }

  function writeStored(data) {
    try {
      if (!data) window.localStorage.removeItem(SESSION_KEY);
      else window.localStorage.setItem(SESSION_KEY, JSON.stringify(data));
    } catch (err) {
      /* The session simply won't survive a reload if storage is blocked. */
    }
  }

  function isLocalHost() {
    var host = String(window.location.hostname || '').toLowerCase();
    return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '[::1]';
  }

  function localMode() {
    auth.mode = 'local';
    auth.user = LOCAL_USER;
    auth.token = '';
    auth.error = '';
    auth.offline = false;
    auth.checked = true;
    activateState(LOCAL_USER);
    return auth;
  }

  function setupMode(message) {
    auth.mode = 'setup';
    auth.user = null;
    auth.token = '';
    auth.error = String(message || '');
    auth.offline = false;
    auth.checked = true;
    A.setStorageScope('local');
    return auth;
  }

  function activateState(user) {
    var scope = auth.mode === 'local'
      ? 'local'
      : user.role === 'admin'
        ? 'admin'
        : String(user.id || 'user');
    A.setStorageScope(scope);
    if (scope === 'admin') A.migrateLegacyState('admin');
    A.load({ seedDemos: auth.mode === 'local' });
  }

  function activateAccount(user, token, expiresAt, offline) {
    auth.mode = 'account';
    auth.user = user;
    auth.token = token;
    auth.expiresAt = expiresAt || '';
    auth.error = '';
    auth.offline = !!offline;
    auth.checked = true;
    activateState(user);
    return auth;
  }

  function clearSession() {
    auth.user = null;
    auth.token = '';
    auth.expiresAt = '';
    writeStored(null);
  }

  function request(path, body, token) {
    if (typeof fetch !== 'function') {
      return Promise.reject(new Error('This browser cannot reach the account server.'));
    }

    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, PROBE_TIMEOUT * 3);
    var headers = { Accept: 'application/json' };
    var init = {
      method: body ? 'POST' : 'GET',
      headers: headers,
      cache: 'no-store',
      signal: controller.signal
    };
    if (body) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    if (token) headers.Authorization = 'Bearer ' + token;

    return fetch(path, init).then(function (response) {
      return response.text().then(function (text) {
        var data = null;
        try { data = text ? JSON.parse(text) : {}; } catch (err) { data = null; }
        if (!data || typeof data !== 'object') {
          throw new Error(response.status === 404 || !response.ok
            ? 'The account server is not available on this site.'
            : 'The account server returned an unexpected response.');
        }
        if (!response.ok || data.error) {
          var error = new Error(data.error || 'The account request failed.');
          error.status = response.status;
          throw error;
        }
        return data;
      });
    }).catch(function (err) {
      if (err && err.name === 'AbortError') {
        throw new Error('The account server did not respond. Check your connection and try again.');
      }
      throw err;
    }).finally(function () {
      window.clearTimeout(timer);
    });
  }

  function probe() {
    if (!/^https?:$/.test(window.location.protocol)) {
      return Promise.resolve({ mode: 'local' });
    }

    return request('./api/auth').then(function (data) {
      if (data.configured) return { mode: 'account', data: data };
      if (isLocalHost()) return { mode: 'local', data: data };
      return { mode: 'setup', data: data };
    }).catch(function (err) {
      var message = err && err.message ? err.message : 'The account server is not available.';
      if (isLocalHost()) return { mode: 'local', error: message };
      return { mode: 'offline', error: message };
    });
  }

  async function boot() {
    if (auth.checked) return auth;
    auth.checked = true;

    var probeResult = await probe();

    if (probeResult.mode === 'local') {
      if (probeResult.error) auth.error = '';
      return localMode();
    }

    if (probeResult.mode === 'setup') {
      var detail = probeResult.data && probeResult.data.error ? probeResult.data.error : '';
      return setupMode(detail || 'Accounts are not configured on this site.');
    }

    if (probeResult.mode === 'account') {
      auth.mode = 'account';

      if (!stored) {
        A.setStorageScope('local');
        return auth;
      }

      try {
        var data = await request('./api/auth', { action: 'session' }, stored.token);
        var user = data.user;
        if (!user || !user.role) throw new Error('The session did not include an account.');
        return activateAccount(user, stored.token, stored.expiresAt);
      } catch (err) {
        if (err && err.status === 401) {
          clearSession();
          A.setStorageScope('local');
          return auth;
        }

        /* A temporary server or network failure should not lock an
           offline-first user out of the local copy they already have. */
        auth.mode = 'account';
        auth.user = stored.user;
        auth.token = stored.token;
        auth.expiresAt = stored.expiresAt;
        auth.error = err && err.message ? err.message : 'Could not verify the session.';
        auth.offline = true;
        activateState(stored.user);
        return auth;
      }
    }

    /* Deployed site, but /api/auth could not be reached. */
    auth.mode = 'account';
    if (stored) {
      auth.user = stored.user;
      auth.token = stored.token;
      auth.expiresAt = stored.expiresAt;
      auth.error = probeResult.error || 'Working offline. Sign-in will resume when the server is reachable.';
      auth.offline = true;
      activateState(stored.user);
      return auth;
    }

    auth.error = probeResult.error || 'The account server is not available on this site.';
    A.setStorageScope('local');
    return auth;
  }

  async function login(username, password) {
    if (!username || !password) throw new Error('Enter your username and password.');
    var data = await request('./api/auth', {
      action: 'login',
      username: String(username),
      password: String(password)
    });
    if (!data.token || !data.user) throw new Error('The server did not return a session.');
    stored = { token: data.token, user: data.user, expiresAt: data.expiresAt || '' };
    writeStored(stored);
    activateAccount(data.user, data.token, data.expiresAt, false);
    return data.user;
  }

  function api(path, body) {
    if (!auth.token) {
      return Promise.reject(new Error('Sign in to continue.'));
    }
    return request(path, body || {}, auth.token).catch(function (err) {
      if (err && err.status === 401) {
        clearSession();
        window.setTimeout(function () {
          window.location.reload();
        }, 60);
      }
      throw err;
    });
  }

  function logout() {
    clearSession();
    auth.user = null;
    auth.token = '';
    auth.expiresAt = '';
    auth.mode = 'account';
    A.setStorageScope('local');
    window.location.hash = '';
    window.location.reload();
  }

  function expire() {
    clearSession();
    auth.user = null;
    auth.token = '';
    auth.expiresAt = '';
    window.setTimeout(function () {
      window.location.reload();
    }, 60);
  }

  A.auth = {
    boot: boot,
    login: login,
    logout: logout,
    expire: expire,
    api: api,
    mode: function () { return auth.mode; },
    user: function () { return auth.user; },
    token: function () { return auth.token; },
    error: function () { return auth.error; },
    isAccount: function () { return auth.mode === 'account'; },
    signedIn: function () { return auth.mode === 'local' || !!auth.user; },
    requiresLogin: function () { return auth.mode === 'account' && !auth.user; },
    isAdmin: function () { return !!(auth.user && auth.user.role === 'admin'); },
    isOffline: function () { return !!auth.offline; },
    displayName: function () {
      return auth.user ? (auth.user.displayName || auth.user.username || 'Account') : '';
    },
    clear: function () { clearSession(); }
  };
})();
