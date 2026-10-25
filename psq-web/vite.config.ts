import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import release from './release.config.json';
import access from './access.config.json';
const buildId = process.env.PSQ_WEB_BUILD || release.productVersion + '-web.' + release.webRevision + '+' + new Date().toISOString().replace(/[-:.]/g, '');
if (!/^[A-Za-z0-9+_.-]+$/.test(buildId)) throw new Error('Invalid PSQ_WEB_BUILD identifier');
const output = resolve(process.env.PSQ_BUILD_DIR || 'dist');
async function files(dir: string, prefix = ''): Promise<string[]> {
  const result: string[] = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) { const path = prefix + '/' + entry.name; if (entry.isDirectory()) result.push(...await files(join(dir, entry.name), path)); else result.push(path); }
  return result;
}
export default defineConfig({
  plugins: [react(), { name: 'psq-pwa-release', async closeBundle() {
    const shell = (await files(output)).filter(p => !['/sw.js', '/release.json'].includes(p));
    const template = await readFile('server/sw-template.js', 'utf8');
    await writeFile(join(output, 'sw.js'), template.replace('__BUILD_ID__', buildId).replace('__PRECACHE__', JSON.stringify(shell)).replace('__AUTO_ACTIVATE__', JSON.stringify(!access.newWebLogins || access.openAfterCountdown)));
    await writeFile(join(output, 'release.json'), JSON.stringify({ productVersion: release.productVersion, webBuild: buildId, apiVersion: release.apiVersion, builtAt: new Date().toISOString() }));
  } }],
  define: { __PSQ_BUILD_ID__: JSON.stringify(buildId) },
  build: { outDir: process.env.PSQ_BUILD_DIR || 'dist', emptyOutDir: true, target: 'es2022' },
});
