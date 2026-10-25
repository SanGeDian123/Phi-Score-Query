import test from 'node:test';
import assert from 'node:assert/strict';
import { avatarAssetPath } from '../server/avatar.mjs';

test('avatar filenames match the Android UTF-8 SHA-256 convention', () => {
  assert.equal(avatarAssetPath('Introduction'), '/avatar/b605350bc00209520b7cd8f546322663356ba630708f71bbf30aead6b3dd608e.png');
  assert.equal(avatarAssetPath('  Introduction  '), avatarAssetPath('Introduction'));
  assert.match(avatarAssetPath('测试头像'), /^\/avatar\/[a-f0-9]{64}\.png$/);
  assert.match(avatarAssetPath('../../private'), /^\/avatar\/[a-f0-9]{64}\.png$/);
  for (const value of [null, undefined, 3, '', ' ', '...', 'Intro\u0000duction', '\u0085', 'a'.repeat(1025)]) assert.equal(avatarAssetPath(value), null);
});
