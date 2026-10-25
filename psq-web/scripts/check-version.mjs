import { readFile } from 'node:fs/promises';
const config = JSON.parse(await readFile(new URL('../release.config.json', import.meta.url), 'utf8'));
let android;
try { android = await readFile(new URL('../../app/build.gradle.kts', import.meta.url), 'utf8'); } catch {}
if (android) {
  const version = android.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
  if (version !== config.productVersion) throw new Error('Android ' + version + ' differs from PWA ' + config.productVersion + '. Update release.config.json before building.');
  console.log('Android / PWA release version aligned: ' + version);
}
