import { readFile, readdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const lock = JSON.parse(await readFile(join(root, 'package-lock.json'), 'utf8'));
const sections = ['Phi Score Query PWA\nCopyright 2026 SanGeDian123\nOriginal web code: Apache-2.0. The independently hosted Next-Phi-Backend is AGPL-3.0.\nPhigros, TapTap, artwork and game metadata belong to their respective owners.'];
sections.push('PSQ Apache License 2.0\n' + await readFile(join(root, 'LICENSE'), 'utf8'));
sections.push('Source Han Sans / Saira hybrid font\n' + await readFile(join(root, '..', 'app/src/main/assets/licenses/OFL-1.1.txt'), 'utf8'));
sections.push('Manrope variable font - countdown fallback\n' + await readFile(join(root, 'public/fonts/countdown-manrope-license.txt'), 'utf8'));
for (const [path, pkg] of Object.entries(lock.packages)) {
  if (!path || pkg.dev) continue;
  const names = await readdir(join(root, path));
  const license = names.find(name => /^licen[sc]e(?:\.(?:md|txt))?$/i.test(name));
  const notice = names.find(name => /^notice(?:\.(?:md|txt))?$/i.test(name));
  let section = path.replace(/^node_modules\//, '') + ' ' + pkg.version + '\nLicense: ' + (pkg.license || 'see upstream package');
  if (license) section += '\n' + await readFile(join(root, path, license), 'utf8');
  if (notice) section += '\n' + await readFile(join(root, path, notice), 'utf8');
  sections.push(section);
}
await writeFile(join(root, 'public/licenses.txt'), sections.join('\n\n' + '='.repeat(64) + '\n\n') + '\n');
console.log('License notices prepared for ' + (sections.length - 4) + ' runtime dependencies.');
