import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { sealSession, openSession, sessionCookie } from '../server/session.mjs';
import { allowedAsset, allowedRoute } from '../server/proxy-policy.mjs';
test('encrypted session survives a round trip and rejects tampering, another key and expiry', () => {
  const key = randomBytes(32), data = { sessionToken: 'test-only-session', accessToken: 'test-only-access', expiresAt: Date.now() + 10000 };
  const cookie = sealSession(data, key); assert.deepEqual(openSession(cookie, key), data); assert.equal(cookie.includes(data.sessionToken), false);
  const bytes = Buffer.from(cookie, 'base64url'); bytes[31] ^= 1;
  assert.equal(openSession(bytes.toString('base64url'), key), null); assert.equal(openSession(cookie, randomBytes(32)), null);
  assert.equal(openSession(sealSession({ ...data, expiresAt: 0 }, key), key), null);
  assert.match(sessionCookie(cookie, true), /HttpOnly; SameSite=Strict.*Secure/);
});
test('proxy only forwards implemented user APIs and safe artwork/media paths', () => {
  assert.ok(allowedRoute('image/bn/user', 'POST')); assert.ok(allowedRoute('suggestions/comments/abc-123', 'DELETE'));
  for (const path of ['admin/users', 'auth/session/exchange', 'songs/../../admin/users', 'users/me/moderation/status', 'save%2f..', 'games/rks-guess/a/../match']) assert.equal(allowedRoute(path, 'POST'), false);
  assert.equal(allowedRoute('save', 'GET'), false); assert.ok(allowedAsset('/avatar/Introduction.png')); assert.ok(allowedAsset('/_ill/illLow/Lethe.1.png'));
  assert.ok(allowedAsset('/suggestion-media/image-1.webp', true));
  for (const path of ['/avatar/../secret.png', '/_ill/ill/a%2fb.png', '/avatar/a\\b.png', 'https://evil.test/a.png', '/.env', '/suggestion-media/../../secret.png']) assert.equal(allowedAsset(path, true), false);
});
