const CACHE_NAME = 'fanqie-23';
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
  '/static/css/compat.css',
  '/static/js/compat.js',
  '/static/js/icons.js',
  '/static/js/app.js',
  '/static/js/cache.js',
  '/static/js/views.js',
  '/static/js/reader.js',
  '/static/js/main.js',
];

self.addEventListener('install', e => {
  // cache: 'reload' 强制绕过 HTTP 缓存取最新文件——否则文件久未更新时
  // 浏览器启发式缓存会把旧内容灌进新 CACHE_NAME（换名也白换）
  e.waitUntil(
    caches.open(CACHE_NAME)
      .then(c => c.addAll(STATIC_ASSETS.map(u => new Request(u, { cache: 'reload' }))))
      .catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    const stale = keys.filter(k => k !== CACHE_NAME && k !== IMG_CACHE);
    await Promise.all(stale.map(k => caches.delete(k)));
    await self.clients.claim();
    // Cache version bumped → force open tabs onto the new assets
    if (stale.length) {
      const clients = await self.clients.matchAll({ type: 'window' });
      for (let i = 0; i < clients.length; i++) {
        const c = clients[i];
        if (typeof c.navigate === 'function') {
          try { c.navigate(c.url); } catch (err) {}
        } else {
          try { c.postMessage({ type: 'SW_UPDATED' }); } catch (err) {}
        }
      }
    }
  })());
});

self.addEventListener('message', e => {
  const data = e.data || {};
  if (data.type === 'SKIP_WAITING') {
    self.skipWaiting();
    return;
  }
  if (data.type === 'CLEAR_CACHES') {
    e.waitUntil(
      caches.keys().then(keys => Promise.all(keys.map(k => caches.delete(k))))
    );
  }
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  // Version / health: always network, never cache — used for update detection
  if (url.pathname === '/api/version' || url.pathname === '/api/health') {
    e.respondWith(fetch(e.request));
    return;
  }

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

  // Static assets: stale-while-revalidate — serve cache instantly, refresh in background
  e.respondWith(
    caches.open(CACHE_NAME).then(cache =>
      cache.match(e.request).then(cached => {
        const fetched = fetch(e.request).then(resp => {
          if (resp && resp.ok) cache.put(e.request, resp.clone()).catch(() => {});
          return resp;
        }).catch(() => cached);
        return cached || fetched;
      })
    )
  );
});
