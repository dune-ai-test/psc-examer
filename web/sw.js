/* =========================================================
   Padanam web — offline app shell
   Registers only on http(s); file:// keeps working directly.
   ========================================================= */
'use strict';

var CACHE = 'padanam-web-v14';
var ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/tokens.css',
  './css/app.css',
  './css/views.css',
  './css/account.css',
  './css/deck.css',
  './js/data.js',
  './js/core.js',
  './js/theme.js',
  './js/gemini.js',
  './js/sync.js',
  './js/auth.js',
  './js/admin.js',
  './js/deck.js',
  './js/views.js',
  './js/app.js',
  './assets/favicon.svg',
  './assets/logo.svg',
  './assets/fonts/bricolage_600.ttf',
  './assets/fonts/bricolage_700.ttf',
  './assets/fonts/dm_sans_400.ttf',
  './assets/fonts/dm_sans_500.ttf',
  './assets/fonts/dm_sans_600.ttf',
  './assets/fonts/dm_sans_700.ttf',
  './assets/fonts/noto_malayalam_400.ttf',
  './assets/fonts/noto_malayalam_600.ttf',
  './assets/fonts/plex_mono_400.ttf',
  './assets/fonts/plex_mono_500.ttf'
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE).then(function (cache) {
      return cache.addAll(ASSETS);
    }).then(function () {
      return self.skipWaiting();
    })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (key) {
        return key !== CACHE;
      }).map(function (key) {
        return caches.delete(key);
      }));
    }).then(function () {
      return self.clients.claim();
    })
  );
});

self.addEventListener('fetch', function (event) {
  var request = event.request;
  if (request.method !== 'GET') return;

  var url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (/\/api\//.test(url.pathname)) return;

  event.respondWith(
    caches.match(request).then(function (cached) {
      if (cached) return cached;
      return fetch(request).then(function (response) {
        if (response && response.ok) {
          var copy = response.clone();
          caches.open(CACHE).then(function (cache) {
            cache.put(request, copy);
          });
        }
        return response;
      }).catch(function () {
        if (request.mode === 'navigate') return caches.match('./index.html');
        throw new Error('Offline and not cached');
      });
    })
  );
});
