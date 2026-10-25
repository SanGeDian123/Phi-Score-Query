import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, extname, join, resolve, sep } from 'node:path';
import { pipeline } from 'node:stream/promises';
import { accountKey, getSessionKey, openSession, sealSession, sessionCookie } from './session.mjs';
import { allowedAsset, allowedRoute } from './proxy-policy.mjs';
import { avatarAssetPath } from './avatar.mjs';
import { launchStatus } from './access-policy.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
try {
  for (const line of (await readFile(join(root, '.env'), 'utf8')).split(/\r?\n/)) {
    const pair = line.match(/^([A-Z_]+)\s*=\s*(.*?)\s*$/);
    if (pair && process.env[pair[1]] === undefined) process.env[pair[1]] = pair[2].replace(/^["']|["']$/g, '');
  }
} catch {}
const dev = process.argv.includes('--dev');
const origin = (process.env.PSQ_API_ORIGIN || 'https://api.plc-liangpi-cup.xyz').replace(/\/$/, '');
if (!/^https?:\/\//.test(origin)) throw new Error('PSQ_API_ORIGIN must be an HTTP(S) origin.');
const publicOrigin = process.env.PSQ_PUBLIC_ORIGIN?.replace(/\/$/, '');
const host = process.env.PSQ_HOST || '127.0.0.1', port = Number(process.env.PSQ_PORT || 4173);
const dist = resolve(process.env.PSQ_BUILD_DIR || join(root, 'dist'));
const access = JSON.parse(await readFile(process.env.PSQ_ACCESS_CONFIG || join(root, 'access.config.json'), 'utf8'));
const key = getSessionKey(root, process.env.PSQ_COOKIE_SECRET);
const secure = publicOrigin?.startsWith('https://') || (!dev && process.env.PSQ_LOCAL_HTTP !== '1');
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.xml': 'application/xml; charset=utf-8', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8' };
const vite = dev ? await (await import('vite')).createServer({ root, server: { middlewareMode: true, hmr: { port: port + 1 } }, appType: 'spa' }) : null;
const refreshing = new Map(), limits = new Map();
function json(res, status, data) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); }
async function body(req, limit = 24 * 1024 * 1024) {
  let size = 0; const chunks = [];
  for await (const chunk of req) { size += chunk.length; if (size > limit) throw Object.assign(new Error('上传内容过大'), { status: 413 }); chunks.push(chunk); }
  return Buffer.concat(chunks);
}
async function inputJson(req, limit) { try { return JSON.parse((await body(req, limit)).toString()); } catch (e) { if (e.status) throw e; throw Object.assign(new Error('请求内容无效'), { status: 400 }); } }
function cookie(req, name) { return (req.headers.cookie || '').split(';').map(v => v.trim()).find(v => v.startsWith(name + '='))?.slice(name.length + 1); }
function session(req) { const current = openSession(cookie(req, 'psq_session'), key); return current && typeof current.accessToken === 'string' && Number.isFinite(current.accessExpiresAt) && current.sessionToken !== 'qr' ? current : null; }
function saveSession(res, current) { res.setHeader('Set-Cookie', sessionCookie(sealSession(current, key), secure)); }
function rateLimit(req, res) {
  const id = req.socket.remoteAddress || 'local', now = Date.now(), bucket = limits.get(id);
  if (!bucket || now - bucket.at > 60000) { limits.set(id, { at: now, count: 1 }); return false; }
  if (++bucket.count <= 30) return false;
  res.setHeader('Retry-After', '60'); json(res, 429, { detail: '登录请求过于频繁，请稍后重试' }); return true;
}
setInterval(() => { for (const [id, bucket] of limits) if (Date.now() - bucket.at > 60000) limits.delete(id); }, 60000).unref();
async function backend(path, options = {}) { return fetch(origin + '/' + path, { ...options, headers: { 'X-PSQ-Moderation': '1', ...options.headers }, signal: AbortSignal.timeout(90000), redirect: 'error' }); }
function freshSession(sessionToken, tokens) {
  if (!tokens.accessToken || !Number.isFinite(tokens.expiresIn)) throw new Error('Invalid session response');
  const now = Date.now(); return { sessionToken, accessToken: tokens.accessToken, accessExpiresAt: now + tokens.expiresIn * 1000, expiresAt: now + 2592000000 };
}
async function exchange(token) { return backend('api/v2/auth/session/exchange', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionToken: token, taptapVersion: 'cn' }) }); }
async function requireAccess(req, res) {
  let current = session(req);
  if (!current) { json(res, 401, { detail: '请先登录' }); return null; }
  if (current.accessExpiresAt < Date.now() + 60000) {
    const id = accountKey(current);
    if (!refreshing.has(id)) {
      const job = (async () => {
        let reply = await backend('api/v2/auth/session/refresh', { method: 'POST', headers: { Authorization: 'Bearer ' + current.accessToken, 'Content-Type': 'application/json' }, body: '{}' });
        if (!reply.ok) reply = await exchange(current.sessionToken);
        if (!reply.ok) throw Object.assign(new Error('登录已失效，请重新登录'), { status: reply.status });
        return freshSession(current.sessionToken, await reply.json());
      })();
      refreshing.set(id, job); job.finally(() => { setTimeout(() => refreshing.delete(id), 3000).unref(); }).catch(() => {});
    }
    try { current = await refreshing.get(id); saveSession(res, current); }
    catch (e) { if (e.status === 401) res.setHeader('Set-Cookie', sessionCookie('', secure, 0)); throw e; }
  }
  return current;
}
async function copyResponse(upstream, res, cache = 'no-store') {
  // PSQ stream-timeout hotfix 2026-10-11: keep failures within this request.
  if (res.destroyed || res.writableEnded) {
    if (upstream.body) await upstream.body.cancel().catch(() => {});
    return;
  }
  const headers = { 'Content-Type': upstream.headers.get('Content-Type') || 'application/octet-stream', 'Cache-Control': cache };
  for (const name of ['etag', 'last-modified', 'content-range', 'accept-ranges']) if (upstream.headers.has(name)) headers[name] = upstream.headers.get(name);
  res.writeHead(upstream.status, headers);
  if (!upstream.body) return res.end();
  // Consume the Web stream directly, avoiding a Readable adapter whose late
  // abort error could escape the request. pipeline still provides backpressure.
  const reader = upstream.body.getReader();
  const cancelBody = () => { reader.cancel().catch(() => {}); };
  res.once('close', cancelBody);
  try {
    async function* chunks() {
      while (true) {
        const { done, value } = await reader.read();
        if (done) return;
        yield value;
      }
    }
    await pipeline(chunks(), res);
  } finally {
    res.off('close', cancelBody);
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
async function release() { try { return JSON.parse(await readFile(join(dist, 'release.json'), 'utf8')); } catch { return { productVersion: 'Pre-0.9.8.0', webBuild: 'Pre-0.9.8.0-web.1-dev', apiVersion: 2, builtAt: null }; } }

const server = createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff'); res.setHeader('Referrer-Policy', 'same-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  if (!dev) res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
  try {
    const url = new URL(req.url, 'http://' + req.headers.host);
    const launch = launchStatus(access);
    if (url.pathname.startsWith('/api/')) {
      const expected = publicOrigin || 'http://' + req.headers.host;
      if ((req.headers.origin && req.headers.origin !== expected) || (req.method !== 'GET' && req.headers['x-psq-request'] !== '1')) return json(res, 403, { detail: '请求来源无效' });
      if (url.pathname === '/api/health' && req.method === 'GET') return json(res, 200, { ok: true, ...await release() });
      if (url.pathname === '/api/version' && req.method === 'GET') return json(res, 200, await release());
      if (url.pathname === '/api/auth/status' && req.method === 'GET') { const current = session(req); return json(res, 200, { loggedIn: !!current, accountKey: current ? accountKey(current) : null, ...launch }); }
      if (!launch.loginEnabled && (url.pathname === '/api/auth/login' || url.pathname === '/api/auth/qr' || url.pathname.startsWith('/api/auth/qr/'))) return json(res, 403, { code: 'WEB_LOGIN_CLOSED', detail: 'PSQ-Web 内测期间暂不开放新登录' });
      if (url.pathname === '/api/auth/login' && req.method === 'POST') {
        if (rateLimit(req, res)) return;
        const { sessionToken } = await inputJson(req, 8192);
        if (typeof sessionToken !== 'string' || sessionToken.trim().length < 16 || sessionToken.length > 512) return json(res, 400, { detail: '请输入有效的 SessionToken' });
        const reply = await exchange(sessionToken.trim()), result = await reply.json();
        if (!reply.ok) return json(res, reply.status, result);
        const current = freshSession(sessionToken.trim(), result); saveSession(res, current);
        return json(res, 200, { loggedIn: true, accountKey: accountKey(current) });
      }
      if (url.pathname === '/api/auth/logout' && req.method === 'POST') {
        const current = session(req); res.setHeader('Set-Cookie', sessionCookie('', secure, 0));
        if (current) backend('api/v2/auth/session/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + current.accessToken, 'Content-Type': 'application/json' }, body: '{"scope":"current"}' }).catch(() => {});
        return json(res, 200, { ok: true });
      }
      if (url.pathname === '/api/auth/token' && req.method === 'POST') {
        const current = await requireAccess(req, res); if (!current) return;
        return json(res, 200, { sessionToken: current.sessionToken });
      }
      if (url.pathname === '/api/auth/qr' && req.method === 'POST') {
        if (rateLimit(req, res)) return;
        const reply = await backend('api/v2/auth/qrcode?taptapVersion=cn', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
        const data = await reply.json();
        if (reply.ok && data.qrId) res.setHeader('Set-Cookie', sessionCookie(sealSession({ sessionToken: 'qr', qrId: data.qrId, expiresAt: Date.now() + 600000 }, key), secure, 600).replace('psq_session=', 'psq_qr='));
        return json(res, reply.status, data);
      }
      if (url.pathname.startsWith('/api/auth/qr/') && req.method === 'GET') {
        const id = url.pathname.slice(13), challenge = openSession(cookie(req, 'psq_qr'), key);
        if (!/^[A-Za-z0-9_-]{1,160}$/.test(id) || challenge?.qrId !== id) return json(res, 400, { detail: '二维码已失效，请重新生成' });
        const reply = await backend('api/v2/auth/qrcode/' + id + '/status'), data = await reply.json();
        if (data.sessionToken && reply.ok) {
          const exchanged = await exchange(data.sessionToken);
          if (!exchanged.ok) return await copyResponse(exchanged, res);
          const current = freshSession(data.sessionToken, await exchanged.json()); saveSession(res, current);
          return json(res, 200, { status: 'confirmed', loggedIn: true, accountKey: accountKey(current) });
        }
        delete data.sessionToken; return json(res, reply.status, data);
      }
      if (url.pathname === '/api/catalog' && req.method === 'GET') {
        const headers = req.headers['if-none-match'] ? { 'If-None-Match': req.headers['if-none-match'] } : {};
        return await copyResponse(await backend('api/v2/songs/catalog', { headers }), res);
      }
      if (url.pathname === '/api/announcements' && req.method === 'GET') return await copyResponse(await backend('app-announcement/index.json'), res);
      if (url.pathname === '/api/app-update' && req.method === 'GET') return await copyResponse(await backend('app-update/latest.json'), res);
      if (((url.pathname === '/api/moderation' || url.pathname === '/api/moderation/appeal') && req.method === 'POST') || (url.pathname === '/api/moderation' && req.method === 'GET')) {
        const current = session(req); if (!current) return json(res, 401, { detail: '请先登录' });
        const appeal = url.pathname.endsWith('/appeal'), input = appeal ? await inputJson(req) : {};
        if (appeal && (!['public_hidden', 'account_suspended'].includes(input.restrictionType) || typeof input.body !== 'string' || !input.body.trim())) return json(res, 400, { detail: '请填写申诉内容' });
        return await copyResponse(await backend('api/v2/users/me/moderation/' + (appeal ? 'appeals' : 'status'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...input, sessionToken: current.sessionToken }) }), res);
      }
      if (url.pathname === '/api/avatar' && req.method === 'GET') {
        const path = avatarAssetPath(url.searchParams.get('name'));
        if (!path) return json(res, 400, { detail: '头像标识无效' });
        return await copyResponse(await backend(path.slice(1)), res, 'public, max-age=300');
      }
      if ((url.pathname === '/api/assets' || url.pathname === '/api/media') && req.method === 'GET') {
        const media = url.pathname === '/api/media', path = url.searchParams.get('path') || '';
        if (!allowedAsset(path, media)) return json(res, 400, { detail: '资源地址无效' });
        if (media && !session(req)) return json(res, 401, { detail: '请先登录' });
        const headers = req.headers.range ? { Range: req.headers.range } : {};
        return await copyResponse(await backend(path.slice(1), { headers }), res, media ? 'private, no-store' : 'public, max-age=300');
      }
      if (url.pathname.startsWith('/api/backend/')) {
        const path = url.pathname.slice(13);
        if (!allowedRoute(path, req.method)) return json(res, 404, { detail: '接口不存在' });
        const current = await requireAccess(req, res); if (!current) return;
        const headers = { Authorization: 'Bearer ' + current.accessToken };
        if (req.headers['content-type']) headers['Content-Type'] = req.headers['content-type'];
        const input = req.method === 'GET' ? undefined : await body(req);
        return await copyResponse(await backend('api/v2/' + path + url.search, { method: req.method, headers, body: input }), res);
      }
      return json(res, 404, { detail: '接口不存在' });
    }
    const privatePage = /^\/app(?:\/|$)/.test(url.pathname) || [access.websitePreviewPath, access.websitePreviewPath.replace(/\/$/, '')].includes(url.pathname);
    if (privatePage) {
      res.setHeader('X-Robots-Tag', 'noindex, nofollow');
      if (!launch.websiteOpen && !session(req)) { res.writeHead(302, { Location: '/', 'Cache-Control': 'no-store' }); return res.end(); }
    }
    if (vite) return vite.middlewares(req, res, () => json(res, 404, { detail: '页面不存在' }));
    if (!['GET', 'HEAD'].includes(req.method)) return json(res, 405, { detail: '不支持此请求方式' });
    let file = resolve(dist, '.' + decodeURIComponent(url.pathname));
    if (file !== dist && !file.startsWith(dist + sep)) return json(res, 403, { detail: '地址无效' });
    try { if (!(await stat(file)).isFile()) file = join(dist, 'index.html'); }
    catch { if (extname(url.pathname)) return json(res, 404, { detail: '文件不存在' }); file = join(dist, 'index.html'); }
    const bytes = await readFile(file), ext = extname(file);
    const cache = privatePage && file.endsWith('index.html') ? 'private, no-store' : ['index.html', 'sw.js', 'release.json', 'manifest.webmanifest'].some(name => file.endsWith(name)) ? 'no-cache' : file.includes(sep + 'assets' + sep) ? 'public, max-age=31536000, immutable' : 'public, max-age=3600';
    res.writeHead(200, { 'Content-Type': mime[ext] || 'application/octet-stream', 'Cache-Control': cache }); res.end(req.method === 'HEAD' ? undefined : bytes);
  } catch (error) {
    if (res.destroyed) return;
    if (!res.headersSent) json(res, error.status || 502, { detail: error.status ? error.message : '暂时无法连接服务器，请稍后重试' }); else res.destroy();
  }
});
server.listen(port, host, () => console.log('PSQ ' + (dev ? 'development' : 'production') + ' ready: http://' + host + ':' + server.address().port));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { server.close(); vite?.close().finally(() => process.exit(0)); if (!vite) setTimeout(() => process.exit(0), 500).unref(); });
