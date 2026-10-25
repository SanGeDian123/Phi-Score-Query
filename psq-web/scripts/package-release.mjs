import { cp, mkdir, readFile, writeFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(process.env.PSQ_BUILD_DIR || join(root, 'dist'));
const config = JSON.parse(await readFile(join(root, 'release.config.json'), 'utf8'));
const release = JSON.parse(await readFile(join(dist, 'release.json'), 'utf8'));
if (release.productVersion !== config.productVersion) throw new Error('Build product version differs from release configuration. Rebuild first.');
if (!release.webBuild.startsWith(config.productVersion + '-web.' + config.webRevision + '+')) throw new Error('Build web revision differs from release configuration. Rebuild first.');
const parent = resolve(process.env.PSQ_PACKAGE_DIR || join(root, 'packages'));
const target = join(parent, 'PSQ-PWA-' + release.webBuild.replace(/\+/g, '-'));
await mkdir(parent, { recursive: true });
await mkdir(target); // Do not replace an earlier artifact.
for (const name of ['README.md', 'DEPLOY.md', 'LICENSE', 'NOTICE', '.env.example', 'release.config.json', 'access.config.json', 'ROUTES.md', 'Start-PSQWeb.ps1', 'Start-PSQWeb.cmd', 'Start-PSQWeb.sh', 'deploy']) await cp(join(root, name), join(target, name), { recursive: true });
await cp(dist, join(target, 'dist'), { recursive: true });
await mkdir(join(target, 'server'));
for (const name of ['index.mjs', 'session.mjs', 'proxy-policy.mjs', 'avatar.mjs', 'access-policy.mjs']) await cp(join(root, 'server', name), join(target, 'server', name));
const access = JSON.parse(await readFile(join(root, 'access.config.json'), 'utf8'));
await writeFile(join(target, 'RELEASE.json'), JSON.stringify({ ...release, websiteIncluded: true, publicEntry: '/', publicPage: 'countdown-then-website', websiteEntry: '/', websitePreviewEntry: access.websitePreviewPath, appEntry: '/app/', requiresExistingSessionBeforeLaunch: true, newWebLogins: access.newWebLogins, openAfterCountdown: access.openAfterCountdown, countdownTarget: access.countdownTarget, practiceIncluded: false, runtime: 'Node.js >=22.13.0', dependenciesRequired: false }, null, 2) + '\n');
async function inspect(dir) {
  for (const item of await readdir(dir, { withFileTypes: true })) {
    if (['.env', '.runtime', 'node_modules', 'tests', 'cookie.key'].includes(item.name)) throw new Error('Private or non-distribution file in package: ' + item.name);
    if (item.isDirectory()) await inspect(join(dir, item.name));
  }
}
await inspect(target);
console.log('Deployment directory: ' + target);
