import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { once } from 'node:events';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

test('upstream streaming failures stay within a request', { timeout: 20000 }, async t => {
  const fixture = await mkdtemp(join(process.env.PSQ_TEST_DIR || process.env.TEMP, 'psq-streaming-'));
  const dist = join(fixture, 'dist');
  await mkdir(dist);
  await writeFile(join(dist, 'index.html'), '<!doctype html><title>Isolated streaming fixture</title>');
  await writeFile(join(dist, 'release.json'), JSON.stringify({ productVersion: 'Pre-0.9.7.12', webBuild: 'streaming-test', apiVersion: 2 }));
  await writeFile(join(fixture, 'access.json'), JSON.stringify({ newWebLogins: true, openAfterCountdown: false, websitePreviewPath: '/preview/' }));
  // Shorten only upstream fetch deadlines in this disposable child process.
  // Production keeps the existing 90-second deadline and configuration.
  const preload = join(fixture, 'preload.mjs');
  await writeFile(preload, `const nativeFetch = globalThis.fetch;
globalThis.fetch = (input, options) => nativeFetch(input, { ...options, signal: AbortSignal.timeout(450) });
`);
  const received = new Map();
  const backend = createServer((req, res) => {
    const name = req.url.split('/').pop();
    received.get(name)?.();
    if (name === 'no-body.png') { res.writeHead(204); res.end(); return; }
    if (name.startsWith('before-headers')) {
      const timer = setTimeout(() => { res.writeHead(200, { 'Content-Type': 'image/png' }); res.end('late'); }, 900);
      res.once('close', () => clearTimeout(timer));
      return;
    }
    res.writeHead(name === 'range.png' ? 206 : 200, {
      'Content-Type': 'image/png', ETag: '"stream-fixture"', 'Last-Modified': 'Sat, 10 Oct 2026 00:00:00 GMT',
      'Accept-Ranges': 'bytes', ...(name === 'range.png' ? { 'Content-Range': 'bytes 0-4/5' } : {}),
    });
    if (name.startsWith('body-timeout')) { res.write('first chunk'); return; }
    if (name === 'reset.png') {
      res.write('first chunk');
      const timer = setTimeout(() => res.destroy(), 25);
      res.once('close', () => clearTimeout(timer));
      return;
    }
    res.end('image');
  });
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve));
  t.after(() => { backend.closeAllConnections(); backend.close(); });
  const child = spawn(process.execPath, ['--import', pathToFileURL(preload).href, 'server/index.mjs'], {
    cwd: new URL('..', import.meta.url),
    env: {
      ...process.env, PSQ_API_ORIGIN: 'http://127.0.0.1:' + backend.address().port,
      PSQ_HOST: '127.0.0.1', PSQ_PORT: '0', PSQ_LOCAL_HTTP: '1', PSQ_PUBLIC_ORIGIN: '',
      PSQ_COOKIE_SECRET: 'isolated-streaming-test-key-never-used-in-production',
      PSQ_BUILD_DIR: dist, PSQ_ACCESS_CONFIG: join(fixture, 'access.json'),
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stderr = '';
  child.stderr.on('data', chunk => { stderr += chunk; });
  const exit = once(child, 'exit');
  t.after(async () => { if (child.exitCode === null) child.kill(); await exit; });
  const port = await new Promise((resolve, reject) => {
    child.stdout.on('data', data => { const match = String(data).match(/ready: http:\/\/127\.0\.0\.1:(\d+)/); if (match) resolve(Number(match[1])); });
    child.once('error', reject);
    child.once('exit', code => reject(new Error(`Web fixture exited: ${code}\n${stderr}`)));
  });
  const base = 'http://127.0.0.1:' + port;
  const asset = name => base + '/api/assets?path=' + encodeURIComponent('/_ill/ill/' + name);
  const checkAlive = async () => {
    assert.equal(child.exitCode, null, stderr);
    assert.equal((await fetch(base + '/api/health').then(r => r.json())).ok, true);
    assert.equal((await fetch(base + '/app/')).status, 200);
    const normal = await fetch(asset('normal.png'));
    assert.equal(normal.status, 200);
    assert.equal(await normal.text(), 'image');
  };

  await t.test('timeout before headers returns 502 while the service keeps running', async () => {
    const response = await fetch(asset('before-headers.png'));
    assert.equal(response.status, 502);
    assert.equal((await response.json()).detail, '暂时无法连接服务器，请稍后重试');
    await checkAlive();
  });
  await t.test('timeout after headers fails the download while the service keeps running', async () => {
    const response = await fetch(asset('body-timeout.png'));
    assert.equal(response.status, 200);
    await assert.rejects(response.text());
    await checkAlive();
  });
  await t.test('an upstream connection reset fails only its own transfer', async () => {
    const response = await fetch(asset('reset.png'));
    assert.equal(response.status, 200);
    await assert.rejects(response.text());
    await checkAlive();
  });
  await t.test('a client leaving before headers does not leave an unhandled timeout', async () => {
    const name = 'before-headers-cancel.png', controller = new AbortController();
    const seen = new Promise(resolve => received.set(name, resolve));
    const request = fetch(asset(name), { signal: controller.signal });
    await seen;
    controller.abort();
    await assert.rejects(request);
    await delay(550);
    await checkAlive();
  });
  await t.test('client cancellation around upstream deadlines remains isolated', async () => {
    await Promise.all([0, 200, 440, 470].flatMap((ms, group) => Array.from({ length: 3 }, async (_, i) => {
      const controller = new AbortController();
      const response = await fetch(asset(`body-timeout-cancel-${group}-${i}.png`), { signal: controller.signal });
      const body = response.text().catch(() => {});
      await delay(ms);
      controller.abort();
      await body;
    })));
    await delay(550);
    await checkAlive();
  });
  await t.test('empty responses, range headers and cache policy remain intact', async () => {
    const empty = await fetch(asset('no-body.png'));
    assert.equal(empty.status, 204);
    assert.equal(await empty.text(), '');
    const range = await fetch(asset('range.png'), { headers: { Range: 'bytes=0-4' } });
    assert.equal(range.status, 206);
    assert.equal(range.headers.get('content-range'), 'bytes 0-4/5');
    assert.equal(range.headers.get('accept-ranges'), 'bytes');
    assert.equal(range.headers.get('etag'), '"stream-fixture"');
    assert.equal(range.headers.get('cache-control'), 'public, max-age=300');
    assert.equal(await range.text(), 'image');
  });
  assert.doesNotMatch(stderr, /Unhandled|uncaught|node:events/);
});
