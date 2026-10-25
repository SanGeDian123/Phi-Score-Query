import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';
import { isWebsiteOpen, areNewWebLoginsEnabled, launchStatus } from '../server/access-policy.mjs';
const dir = await mkdtemp(join(process.env.PSQ_TEST_DIR || process.env.TEMP, 'psq-core-'));
for (const name of ['core', 'custom', 'site', 'countdown']) {
  const source = (await readFile(new URL('../src/' + name + '.ts', import.meta.url), 'utf8')).replace("from './core'", "from './core.mjs'");
  await writeFile(join(dir, name + '.mjs'), ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText);
}
const core = await import(pathToFileURL(join(dir, 'core.mjs'))), custom = await import(pathToFileURL(join(dir, 'custom.mjs')));
const site = await import(pathToFileURL(join(dir, 'site.mjs')));
const { countdownParts } = await import(pathToFileURL(join(dir, 'countdown.mjs')));

test('website and new web login open at the exact UTC+8 deadline', () => {
  const config = { newWebLogins: true, openAfterCountdown: true, countdownTarget: '2026-10-11T12:00:00+08:00' };
  const target = Date.parse(config.countdownTarget);
  for (const now of [target - 1, target, target + 1]) {
    assert.equal(isWebsiteOpen(config, now), now >= target);
    assert.equal(areNewWebLoginsEnabled(config, now), now >= target);
    assert.equal(launchStatus(config, now).serverTime, now);
  }
  assert.equal(isWebsiteOpen({ ...config, countdownTarget: 'invalid' }, target + 1), false);
  assert.equal(areNewWebLoginsEnabled({ ...config, newWebLogins: false }, target + 1), false);
  assert.equal(isWebsiteOpen({ ...config, newWebLogins: false, openAfterCountdown: false }, target + 1), false);
  assert.equal(isWebsiteOpen({ ...config, openAfterCountdown: false }, target - 1), true);
});

test('countdown uses UTC+8, rolls over units and stays at zero after the target', () => {
  const target = Date.parse('2026-10-11T12:00:00+08:00');
  assert.equal(target, Date.parse('2026-10-11T04:00:00Z'));
  assert.deepEqual(countdownParts(target, target - 90061000), [1, 1, 1, 1]);
  assert.deepEqual(countdownParts(target, target - 86400000), [1, 0, 0, 0]);
  assert.deepEqual(countdownParts(target, target - 1), [0, 0, 0, 1]);
  assert.deepEqual(countdownParts(target, target), [0, 0, 0, 0]);
  assert.deepEqual(countdownParts(target, target + 10000), [0, 0, 0, 0]);
});
test('website entry preserves installed PWA launches and old app bookmarks', async () => {
  assert.equal(site.isAppEntry('/', '', false), false);
  assert.equal(site.isAppEntry('/', '#download', false), false);
  assert.equal(site.isAppEntry('/app/', '', false), true);
  assert.equal(site.isAppEntry('/app', '#settings', false), true);
  assert.equal(site.isAppEntry('/', '', true), true);
  assert.equal(site.isAppEntry('/', '#song/Chronostasis.%E9%BB%92%E7%9A%87%E5%B8%9D', false), true);
  assert.equal(site.isAppEntry('/', '#scores', false), true);
  assert.equal(site.isAppEntry('/application', '', false), false);
  assert.equal(site.isWebsitePreview('/preview'), true);
  assert.equal(site.isWebsitePreview('/preview/'), true);
  assert.equal(site.isWebsitePreview('/preview/anything'), false);
  const manifest = JSON.parse(await readFile(new URL('../public/manifest.webmanifest', import.meta.url), 'utf8'));
  assert.equal(manifest.id, '/');
  assert.equal(manifest.scope, '/');
  assert.equal(manifest.start_url, '/app/');
});
test('Android download comes from a valid published APK and tolerates unavailable metadata', () => {
  const release = { versionName: 'Pre-0.9.7.12', apkUrl: 'https://example.com/app-update/PSQ.apk', sizeBytes: 10485760, changelog: ['更新内容', null] };
  assert.equal(site.androidRelease(release).sizeBytes, 10485760);
  assert.deepEqual(site.androidRelease(release).changelog, ['更新内容']);
  for (const apkUrl of ['javascript:alert(1)', 'http://example.com/PSQ.apk', 'https://example.com/PSQ.html', 'https://user:secret@example.com/PSQ.apk']) assert.equal(site.androidRelease({ ...release, apkUrl }), null);
  assert.equal(site.androidRelease(null), null);
  assert.equal(site.androidRelease({ versionName: 'Pre-0.9.7.12' }), null);
});
test('RKS threshold, inverse, perfect and invalid values', () => {
  assert.equal(core.chartRks(16, 100), 16); assert.equal(core.chartRks(16, 69.99), 0);
  assert.ok(Math.abs(core.chartRks(16, 95) - 12.641975308641975) < 1e-10);
  assert.equal(core.solveRks(16, null, 16).accuracy, 100);
  assert.equal(core.solveRks(null, 100, 16).constant, 16);
  assert.throws(() => core.chartRks(16, 101)); assert.throws(() => core.solveRks(16, 100, 16));
});
test('score ceiling and judgement validation', () => {
  assert.deepEqual(core.playScore(3, 2, 1, 0, 0, 3), { score: 895000, accuracy: 2.65 / 3 * 100 });
  assert.equal(core.playScore(7, 3, 1, 2, 1, 4).score, 526429);
  assert.throws(() => core.playScore(10, 8, 1, 0, 0, 9)); assert.throws(() => core.playScore(10, 8, 1, 1, 0, 10));
});
test('P30 account RKS uses 27 perfect plus top three a second time', () => {
  const records = Array.from({ length: 30 }, (_, i) => ({ rks: 18 - i * .1, score: 1000000, songName: String(i), difficulty: 'AT' }));
  const expected = (records.slice(0, 27).reduce((n, r) => n + r.rks, 0) + 18 + 17.9 + 17.8) / 30;
  assert.ok(Math.abs(core.p30Rks(records) - expected) < 1e-10);
  assert.notEqual(core.p30Rks(records), records.reduce((n, r) => n + r.rks, 0) / 30);
  assert.equal(core.p30Rks(records.map(r => ({ ...r, score: 999999 }))), 0);
});
test('custom partial rows reserve copies, retain source identity and calculate first 30', () => {
  const values = Array(36).fill(null); values[0] = 16; values[1] = 18; values[2] = 17;
  const rows = custom.customRows(values);
  assert.deepEqual(rows.slice(0, 3).map(r => r.sourceIndex), [1, 2, 0]);
  assert.equal(rows.filter(r => r.mirrored).length, 3); assert.equal(rows.length, 36);
  assert.equal(custom.customComposite(values, rows), 102 / 30);
  const partial = [...values]; partial[2] = null; assert.equal(custom.customRows(partial).some(r => r.mirrored), false);
});
test('custom chart mapping removes duplicate main AP and submits exactly 36 records', () => {
  const songs = Array.from({ length: 36 }, (_, i) => ({ id: 's' + i, name: 's' + i, chartConstants: { at: 18 - i * .05 } }));
  const charts = songs.map(s => ({ songId: s.id, difficulty: 'AT', accuracy: '100', score: '1000000' }));
  const values = custom.customValues(charts, songs, 'p30'), keys = charts.map(c => c.songId + ':AT'), rows = custom.customRows(values, keys);
  const scores = custom.customScores(charts, rows, songs, 'p30');
  assert.equal(scores.length, 36); assert.ok(scores.every(s => s.acc === 100 && s.score === 1000000));
  assert.equal(new Set(scores.slice(3).map(s => s.song)).size, 33);
  const duplicate = charts.map(c => ({ ...c })); duplicate[4] = { ...charts[0] }; const mapped = custom.customRows(custom.customValues(duplicate, songs, 'b30'), duplicate.map(c => c.songId + ':AT'));
  assert.equal(mapped.filter(r => r.mirrored && r.sourceIndex === 0).length, 1);
  assert.throws(() => custom.customScores(Array.from({ length: 36 }, custom.emptyChart), custom.customRows(Array(36).fill(null)), songs, 'b30'));
});
test('unknown songs stay out of all snapshot score lists and account changes compare correctly', () => {
  const songs = [{ id: 'known', name: '曲目', chartConstants: { in: 16 } }];
  const raw = { save: { game_record: { known: [{ difficulty: 'IN', score: 1000000, accuracy: 100, is_full_combo: true }], unknown: [{ difficulty: 'IN', score: 1000000, accuracy: 100 }] } }, rks: { totalRks: 16, b30Charts: [{ songId: 'known', difficulty: 'IN', rks: 16 }, { songId: 'unknown', difficulty: 'IN', rks: 18 }] } };
  const first = core.makeSnapshot(raw, songs); assert.equal(first.records.length, 1); assert.equal(first.items.length, 1); assert.equal(first.comparison, false);
  const next = core.makeSnapshot(raw, songs, first); assert.equal(next.updated.length, 0); assert.equal(next.comparison, true);
  assert.equal(core.challengeLabel(413), '金13');
});

test('invalid primary avatars fall back to summary avatars, and AP counts in clear and FC totals', () => {
  const songs = [{ id: 'known', name: '曲目', chartConstants: { in: 16 } }];
  const raw = { playerNickname: '验证', save: { user: { avatar: '...' }, summaryParsed: { avatar: ' Introduction ' }, game_record: { known: [{ difficulty: 'IN', score: 1000000, accuracy: 100, is_full_combo: false }] } }, rks: { totalRks: 16, b30Charts: [] } };
  const snapshot = core.makeSnapshot(raw, songs);
  assert.equal(snapshot.avatar, 'Introduction');
  assert.deepEqual(core.gradeSummary(snapshot), { clear: 1, fullCombo: 1, allPerfect: 1 });
  assert.deepEqual(core.gradeSummary({ ...snapshot, records: [], gradeCounts: { IN: { C: 10, FC: 4, P: 2 } } }), { clear: 16, fullCombo: 6, allPerfect: 2 });
  const nextRaw = structuredClone(raw); nextRaw.save.game_record.known[0].is_full_combo = true;
  const next = core.makeSnapshot(nextRaw, songs, snapshot);
  assert.equal(core.updatedScoreLabel(next.updated[0]), 'FC');
  assert.equal(next.updated[0].scoreChanged, false);
});
