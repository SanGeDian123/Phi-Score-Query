import { createHash } from 'node:crypto';

export function avatarAssetPath(value) {
  if (typeof value !== 'string') return null;
  const name = value.trim();
  if (!name || name === '...' || name.length > 1024 || /[\x00-\x1f\x7f-\x9f]/.test(name)) return null;
  // Android and the backend use the UTF-8 avatar identifier, not its display name, as the asset key.
  return '/avatar/' + createHash('sha256').update(name, 'utf8').digest('hex') + '.png';
}
