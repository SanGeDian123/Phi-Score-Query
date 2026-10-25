import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

export function getSessionKey(root, configured) {
  if (configured) {
    if (configured.length < 32) throw new Error('PSQ_COOKIE_SECRET must contain at least 32 characters.');
    return createHash('sha256').update(configured).digest();
  }
  mkdirSync(join(root, '.runtime'), { recursive: true });
  const file = join(root, '.runtime', 'cookie.key');
  try { return readFileSync(file); }
  catch {
    const key = randomBytes(32);
    writeFileSync(file, key, { flag: 'wx', mode: 0o600 });
    return key;
  }
}
export function sealSession(session, key) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const data = Buffer.concat([cipher.update(JSON.stringify(session), 'utf8'), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), data]).toString('base64url');
}
export function openSession(value, key) {
  try {
    const bytes = Buffer.from(value || '', 'base64url');
    if (bytes.length < 29) return null;
    const decipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(0, 12));
    decipher.setAuthTag(bytes.subarray(12, 28));
    const data = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(28)), decipher.final()]).toString('utf8'));
    return data.expiresAt > Date.now() && typeof data.sessionToken === 'string' ? data : null;
  } catch { return null; }
}
export function sessionCookie(value, secure, maxAge = 2592000) {
  return 'psq_session=' + value + '; Path=/; HttpOnly; SameSite=Strict; Max-Age=' + maxAge + (secure ? '; Secure' : '');
}
export function accountKey(session) {
  return createHash('sha256').update(session.sessionToken).digest('hex').slice(0, 24);
}
