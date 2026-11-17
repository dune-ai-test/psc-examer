/* =========================================================
   Padanam web — light, dark and system appearance
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam = window.Padanam || {};
  var KEY = 'padanam.theme.v1';
  var VALID = { light: true, dark: true, system: true };
  var media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  var preference = 'system';

  function readPreference() {
    try {
      var saved = window.localStorage.getItem(KEY);
      if (VALID[saved]) preference = saved;
    } catch (err) {
      /* localStorage can be blocked; keep the default */
    }
    return preference;
  }

  function resolved(pref) {
    if (pref === 'dark') return 'dark';
    if (pref === 'light') return 'light';
    return media && media.matches ? 'dark' : 'light';
  }

  function paint(pref) {
    var theme = resolved(pref);
    var root = document.documentElement;
    root.setAttribute('data-theme', theme);
    root.style.colorScheme = theme;

    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#0B0F1A' : '#F2F4F9');
  }

  function save(pref) {
    try {
      window.localStorage.setItem(KEY, pref);
    } catch (err) {
      /* the choice still applies to this page view */
    }
  }

  A.theme = {
    KEY: KEY,
    preference: function () {
      return preference;
    },
    resolved: function () {
      return resolved(preference);
    },
    set: function (pref, announce) {
      preference = VALID[pref] ? pref : 'system';
      save(preference);
      paint(preference);
      if (announce && typeof A.live === 'function') {
        A.live('Appearance set to ' + preference);
      }
      return preference;
    },
    toggle: function () {
      return A.theme.set(resolved(preference) === 'dark' ? 'light' : 'dark', true);
    }
  };

  readPreference();
  paint(preference);

  if (media) {
    var onChange = function () {
      if (preference === 'system') paint(preference);
    };
    if (typeof media.addEventListener === 'function') media.addEventListener('change', onChange);
    else if (typeof media.addListener === 'function') media.addListener(onChange);
  }
})();
