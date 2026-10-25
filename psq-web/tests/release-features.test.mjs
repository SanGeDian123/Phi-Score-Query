import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';
const dir = await mkdtemp(join(process.env.PSQ_TEST_DIR || process.env.TEMP, 'psq-release-'));
for (const name of ['core', 'release-features']) {
  let source = await readFile(new URL('../src/' + name + '.ts', import.meta.url), 'utf8');
  source = source.replace("from './core'", "from './core.mjs'").replace("import oldConstants from './time-rewind-constants.json';", 'const oldConstants = ' + await readFile(new URL('../src/time-rewind-constants.json', import.meta.url), 'utf8') + ';');
  await writeFile(join(dir, name + '.mjs'), ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText);
}
const { loadHistory, historyPoints, rewindSnapshot, rewindSongs } = await import(pathToFileURL(join(dir, 'release-features.mjs')));
const now = Date.parse('2026-10-25T10:00:00Z'), day = 86400000;
const item = i => ({ rks: 16.8 - i / 10000, rksJump: .0001, createdAt: new Date(now - i * day / 100).toISOString() });
function fetcher(total, calls) { return async cursor => { const start = Number(cursor || 0), end = Math.min(start + 200, total); calls.push(start); return { items: Array.from({ length: end - start }, (_, i) => item(start + i)), total, currentRks: 16.8, peakRks: 16.8, hasMore: end < total, nextCursor: end < total ? String(end) : undefined }; }; }
test('selected interval automatically crosses 200-item pages and includes the exact boundary', async () => {
  const calls = [], page = await loadHistory(7, now, fetcher(2000, calls));
  assert.deepEqual(calls, [0, 200, 400, 600]); assert.equal(historyPoints(page.items, 7, now).length, 701);
  const next = await loadHistory(7, now, () => { throw Error('covered interval must reuse cache'); }, page); assert.equal(next, page);
});
test('all history loads every page with no manual expansion; a wider range continues cached pagination', async () => {
  const calls = [], first = await loadHistory(7, now, fetcher(1250, calls));
  const all = await loadHistory(0, now, fetcher(1250, calls), first);
  assert.deepEqual(calls, [0, 200, 400, 600, 800, 1000, 1200]); assert.equal(all.items.length, 1250); assert.equal(all.hasMore, false);
  const points = historyPoints(all.items, 0, now); assert.ok(Date.parse(points[0].createdAt) < Date.parse(points.at(-1).createdAt));
});
test('non-progressing cursors and empty-more pages fail rather than loop forever', async () => {
  await assert.rejects(loadHistory(0, now, async () => ({ items: [item(0)], hasMore: true, nextCursor: 'same' })), /分页未前进/);
  await assert.rejects(loadHistory(0, now, async () => ({ items: [], hasMore: true, nextCursor: 'next' })), /空数据/);
  await assert.rejects(loadHistory(0, now, async () => ({ items: [item(0)], hasMore: true })), /分页未前进/);
});
test('failed and aborted page requests propagate; invalid numeric and date records are omitted', async () => {
  await assert.rejects(loadHistory(0, now, async () => { throw new DOMException('cancelled', 'AbortError'); }), { name: 'AbortError' });
  assert.equal(historyPoints([item(0), { ...item(1), rks: NaN }, { ...item(2), createdAt: 'invalid' }], 0, now).length, 1);
});
test('rewind uses the frozen old constants, P1 plus B19 formula, deterministic P1 ties and preserves normal data', async () => {
  const constants = JSON.parse(await readFile(new URL('../src/time-rewind-constants.json', import.meta.url), 'utf8'));
  const entries = Object.entries(constants).flatMap(([songId, ds]) => Object.entries(ds).map(([difficulty, chartConstant]) => ({ songId, difficulty, chartConstant })));
  const tied = entries.find(a => entries.some(b => b.songId !== a.songId && b.chartConstant === a.chartConstant));
  const pair = entries.filter(e => e.chartConstant === tied.chartConstant).slice(0, 2).reverse();
  const make = (e, i) => ({ ...e, songName: '曲目' + i, chartConstant: 99, rks: 99, accuracy: 100, score: 1000000, isFullCombo: true });
  const records = [...pair.map(make), ...entries.slice(0, 30).map((e, i) => ({ ...make(e, i), accuracy: 98, score: 980000 })), { ...make(pair[0], 100), songId: 'not-in-old-catalog' }];
  const normal = { records, updated: records, items: records, totalRks: 99, gradeCounts: {} }, before = structuredClone(normal);
  const result = rewindSnapshot(normal); assert.deepEqual(normal, before); assert.notEqual(result, normal); assert.equal(result.items.filter(r => r.section === 'BEST').length, 19);
  const p1 = result.items.find(r => r.section === 'AP'); assert.equal(p1.songId, [...pair].sort((a, b) => a.songId < b.songId ? -1 : 1)[0].songId);
  assert.equal(result.totalRks, result.items.reduce((sum, r) => sum + r.rks, 0) / 20);
  assert.equal(result.records.some(r => r.songId === 'not-in-old-catalog'), false);
  assert.ok(result.records.every(r => r.chartConstant === constants[r.songId][r.difficulty]));
  const songs = [{ id: pair[0].songId, chartConstants: { at: 99 } }], oldSongs = rewindSongs(songs); assert.equal(songs[0].chartConstants.at, 99); assert.notDeepEqual(oldSongs, songs);
  assert.equal(rewindSnapshot(null), null);
});
