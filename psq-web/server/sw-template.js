const BUILD = '__BUILD_ID__';
const SHELL = 'psq-shell-' + BUILD;
const ART = 'psq-art-v1';
const PRECACHE = __PRECACHE__;
const AUTO_ACTIVATE = __AUTO_ACTIVATE__;
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL);
    try { await cache.addAll(PRECACHE.map(url => new Request(url, { cache: 'reload' }))); if (AUTO_ACTIVATE) await self.skipWaiting(); }
    catch (error) { await caches.delete(SHELL); throw error; }
  })());
});
self.addEventListener('activate', event => {
    event.waitUntil((async () => {
      for (const name of await caches.keys()) if (name.startsWith('psq-shell-') && name !== SHELL) await caches.delete(name);
      await self.clients.claim();
      if (!AUTO_ACTIVATE) return;
      const status = await fetch('/api/auth/status', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(8000) }).then(reply => reply.ok ? reply.json() : null).catch(() => null);
      for (const client of await self.clients.matchAll({ type: 'window', includeUncontrolled: true })) {
        const url = new URL(client.url);
        // Refresh cached public content; keep an existing user's open app untouched.
        if (url.origin !== self.location.origin) continue;
        // Navigation must not delay activation: its fetch needs this worker active.
        if (url.pathname === '/') client.navigate(client.url).catch(() => {});
        else if (/^\/app(?:\/|$)/.test(url.pathname) && status?.loggedIn === false && status?.loginEnabled === false) client.navigate('/').catch(() => {});
      }
    })());
});
self.addEventListener('message', event => { if (event.data?.type === 'ACTIVATE') self.skipWaiting(); if (event.data?.type === 'CLEAR_ART') event.waitUntil(caches.delete(ART)); });
self.addEventListener('fetch', event => {
  const req = event.request, url = new URL(req.url);
  if (url.origin !== self.location.origin || req.method !== 'GET') return;
  if (url.pathname === '/api/assets' || url.pathname === '/api/avatar') {
    const path = url.searchParams.get('path') || '';
    if (url.pathname === '/api/assets' && !path.startsWith('/_ill/illLow/')) return;
    event.respondWith((async () => {
      const cache = await caches.open(ART), saved = await cache.match(req);
      if (saved) return saved;
      const reply = await fetch(req);
      if (reply.ok && reply.headers.get('content-type')?.startsWith('image/')) { await cache.put(req, reply.clone()); const keys = await cache.keys(); for (const old of keys.slice(0, Math.max(0, keys.length - 80))) await cache.delete(old); }
      return reply;
    })()); return;
  }
  // Authenticated API responses and credentials are never stored in CacheStorage.
  if (url.pathname.startsWith('/api/') || url.pathname === '/release.json' || url.pathname === '/sw.js') return;
  if (req.mode === 'navigate') {
    event.respondWith((async () => {
      try { return await fetch(req); }
      catch { return await caches.open(SHELL).then(cache => cache.match('/index.html')) || Response.error(); }
    })()); return;
  }
  if (PRECACHE.includes(url.pathname)) event.respondWith(caches.open(SHELL).then(cache => cache.match(url.pathname)).then(reply => reply || fetch(req)));
});
