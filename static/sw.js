const CACHE_NAME = 'fanqie-11';
const IMG_CACHE = 'fanqie-imgs';
const STATIC_ASSETS = [
  '/',
  '/static/index.html',
  '/static/manifest.json',
  '/static/css/base.css',
  '/static/css/layout.css',
  '/static/css/home.css',
  '/static/css/detail.css',
  '/static/css/reader.css',
  '/static/css/components.css',
  '/static/js/app.js',
  '/static/js/cache.js',
  '/static/js/views.js',
  '/static/js/reader.js',
  '/static/js/main.js',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(STATIC_ASSETS)).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(k => k !== CACHE_NAME && k !== IMG_CACHE).map(k => caches.delete(k))
  )));
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  // Cache cover images from byteimg/fqnovelpic CDNs — ignore query signature
  if (url.hostname.includes('byteimg.com') || url.hostname.includes('fqnovelpic.com')) {
    e.respondWith(
      caches.open(IMG_CACHE).then(cache =>
        cache.match(e.request, { ignoreSearch: true }).then(cached => {
          if (cached) return cached;
          return fetch(e.request).then(resp => {
            if (resp.ok || resp.type === 'opaque') {
              cache.put(e.request, resp.clone()).catch(() => {});
            }
            return resp;
          });
        })
      )
    );
    return;
  }

  // Cache proxied images — cache-first with exact URL match
  if (url.pathname === '/api/img_proxy') {
    e.respondWith(
      caches.open(IMG_CACHE).then(cache =>
        cache.match(e.request).then(cached => {
          if (cached) return cached;
          return fetch(e.request).then(resp => {
            if (resp.ok) {
              cache.put(e.request, resp.clone()).catch(() => {});
            }
            return resp;
          });
        })
      )
    );
    return;
  }

  if (url.pathname.startsWith('/api/') && e.request.method === 'GET') {
    // Network-first with cache fallback for GET API requests
    e.respondWith(
      fetch(e.request).then(resp => {
        if (resp.ok) {
          const clone = resp.clone();
          caches.open(CACHE_NAME).then(c => c.put(e.request, clone)).catch(() => {});
        }
        return resp;
      }).catch(() => caches.match(e.request))
    );
    return;
  }

  if (url.pathname.startsWith('/api/')) {
    // POST/other methods: network-only, no caching
    e.respondWith(fetch(e.request));
    return;
  }

  // Static assets: cache-first
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request).then(resp => {
    const clone = resp.clone();
    caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
    return resp;
  })));
});
