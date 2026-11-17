/* =========================================================
   Padanam web — admin console client
   Talks to /api/admin with the signed-in admin session.
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  if (!A || !A.auth) return;

  var ENDPOINT = './api/admin';
  var state = {
    checked: false,
    loading: false,
    error: '',
    users: [],
    sets: []
  };
  var current = null;

  function allowed() {
    return !!(A.auth && A.auth.isAdmin());
  }

  function info() {
    return {
      checked: state.checked,
      loading: state.loading,
      error: state.error,
      users: state.users,
      sets: state.sets
    };
  }

  function load(force) {
    if (!allowed()) return Promise.reject(new Error('Admin access is required.'));
    if (state.loading && current) return current;
    if (state.checked && !force) return Promise.resolve(info());

    state.loading = true;
    state.error = '';

    current = A.auth.api(ENDPOINT, { action: 'users' }).then(function (data) {
      state.users = Array.isArray(data.users) ? data.users : [];
      state.sets = Array.isArray(data.sets) ? data.sets : [];
      state.checked = true;
      state.error = '';
      return info();
    }).catch(function (err) {
      state.error = err && err.message ? err.message : 'Could not load accounts.';
      state.checked = true;
      throw err;
    }).finally(function () {
      state.loading = false;
      current = null;
    });

    return current;
  }

  function createUser(payload) {
    if (!allowed()) return Promise.reject(new Error('Admin access is required.'));
    return A.auth.api(ENDPOINT, {
      action: 'create-user',
      username: payload.username,
      displayName: payload.displayName,
      password: payload.password
    }).then(function (data) {
      state.checked = false;
      return data.user;
    });
  }

  function assignSets(userId, setIds) {
    if (!allowed()) return Promise.reject(new Error('Admin access is required.'));
    return A.auth.api(ENDPOINT, {
      action: 'assign-sets',
      userId: userId,
      setIds: setIds
    }).then(function (data) {
      state.checked = false;
      return data;
    });
  }

  A.admin = {
    allowed: allowed,
    info: info,
    load: load,
    createUser: createUser,
    assignSets: assignSets
  };
})();
