import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { openSession, getSessionKey } from '../server/session.mjs';
import { createMockBackend } from './mock-backend.mjs';

test('a running server automatically opens page access, token login and QR login after the countdown', async t => {
  const fixtureDir = await mkdtemp(join(process.env.PSQ_TEST_DIR || process.env.TEMP, 'psq-launch-'));
  const { server, history } = createMockBackend();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const target = Date.now() + 3000;
  const accessConfig = join(fixtureDir, 'access.config.json');
  await writeFile(accessConfig, JSON.stringify({ newWebLogins: true, openAfterCountdown: true, websitePreviewPath: '/preview/', countdownTarget: new Date(target).toISOString() }));
  const secret = 'isolated-launch-test-key-not-a-production-credential';
  const child = spawn(process.execPath, ['server/index.mjs'], {
    cwd: new URL('..', import.meta.url),
    env: { ...process.env, PSQ_API_ORIGIN: 'http://127.0.0.1:' + server.address().port, PSQ_PORT: '0', PSQ_COOKIE_SECRET: secret, PSQ_LOCAL_HTTP: '1', PSQ_PUBLIC_ORIGIN: '', PSQ_ACCESS_CONFIG: accessConfig, PSQ_BUILD_DIR: process.env.PSQ_BUILD_DIR || 'D:\\PSQWebBuild\\dist' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  t.after(async () => { child.kill(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); });
  const base = await new Promise((resolve, reject) => {
    child.stdout.on('data', data => { const match = String(data).match(/ready: (http:\/\/\S+)/); if (match) resolve(match[1]); });
    child.once('exit', code => reject(new Error('Test server exited: ' + code)));
    child.stderr.on('data', data => reject(new Error(String(data))));
  });
  const headers = { 'X-PSQ-Request': '1', 'Content-Type': 'application/json' };
  const before = await fetch(base + '/api/auth/status').then(r => r.json());
  assert.equal(before.websiteOpen, false); assert.equal(before.loginEnabled, false);
  assert.equal(Date.parse(before.countdownTarget), target); assert.ok(before.serverTime < target);
  const historyLength = history.length;
  for (const path of ['/app', '/app/', '/preview', '/preview/']) {
    const reply = await fetch(base + path, { redirect: 'manual' });
    assert.equal(reply.status, 302); assert.equal(reply.headers.get('location'), '/');
  }
  assert.equal((await fetch(base + '/api/auth/login', { method: 'POST', headers, body: JSON.stringify({ sessionToken: 'psq-local-test-launch' }) })).status, 403);
  assert.equal((await fetch(base + '/api/auth/qr', { method: 'POST', headers, body: '{}' })).status, 403);
  assert.equal(history.length, historyLength);
  await new Promise(resolve => setTimeout(resolve, Math.max(0, target - Date.now()) + 30));
  const after = await fetch(base + '/api/auth/status').then(r => r.json());
  assert.equal(after.websiteOpen, true); assert.equal(after.loginEnabled, true);
  for (const path of ['/', '/app', '/app/', '/preview', '/preview/']) {
    const reply = await fetch(base + path, { redirect: 'manual' }); assert.equal(reply.status, 200);
    if (path !== '/') { assert.equal(reply.headers.get('cache-control'), 'private, no-store'); assert.match(reply.headers.get('x-robots-tag'), /noindex/); }
  }
  assert.equal((await fetch(base + '/api/backend/save', { method: 'POST', headers, body: '{}' })).status, 401);
  assert.equal((await fetch(base + '/api/media?path=' + encodeURIComponent('/suggestion-media/test.png'))).status, 401);
  const login = await fetch(base + '/api/auth/login', { method: 'POST', headers, body: JSON.stringify({ sessionToken: 'psq-local-test-launch' }) });
  assert.equal(login.status, 200); const state = await login.json(); assert.equal(state.loggedIn, true); assert.ok(state.accountKey);
  const cookie = login.headers.get('set-cookie').split(';')[0]; assert.match(login.headers.get('set-cookie'), /HttpOnly/);
  assert.equal(openSession(cookie.slice('psq_session='.length), getSessionKey(fixtureDir, secret)).sessionToken, 'psq-local-test-launch');
  assert.equal((await fetch(base + '/api/auth/status', { headers: { Cookie: cookie } }).then(r => r.json())).loggedIn, true);
  assert.equal((await fetch(base + '/api/backend/save', { method: 'POST', headers: { ...headers, Cookie: cookie }, body: '{}' })).status, 200);
  const qr = await fetch(base + '/api/auth/qr', { method: 'POST', headers, body: '{}' }); assert.equal(qr.status, 200);
  assert.equal((await qr.json()).qrId, 'test-qr'); const challenge = qr.headers.get('set-cookie').split(';')[0];
  const pending = await fetch(base + '/api/auth/qr/test-qr', { headers: { Cookie: challenge } }).then(r => r.json());
  assert.equal(pending.status, 'Pending'); assert.equal(pending.sessionToken, undefined);
  const confirmed = await fetch(base + '/api/auth/qr/test-qr', { headers: { Cookie: challenge } }); assert.equal(confirmed.status, 200);
  const confirmedState = await confirmed.json(); assert.equal(confirmedState.loggedIn, true); assert.equal(confirmedState.status, 'confirmed'); assert.equal(confirmedState.sessionToken, undefined);
  assert.equal((await fetch(base + '/api/auth/status', { headers: { Cookie: confirmed.headers.get('set-cookie').split(';')[0] } }).then(r => r.json())).loggedIn, true);
  assert.equal(child.exitCode, null, 'the deadline must not require a server restart');
});
