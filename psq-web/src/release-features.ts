import { chartRks, best, difficulties, type Score, type Snapshot, type Song } from './core';
import oldConstants from './time-rewind-constants.json';

export const releaseNotes = [
  '“谱面播放与练习”功能新增打击音效调整设置；',
  '支持Best N图片生成，上限200张谱面；',
  '新增“时间回溯”模式，开启后，可查看旧定数体系下的RKS及Best 19。',
  '新增RKS趋势图，在概览页面点击RKS数字即可进入。',
  '优化了使用体验。',
];
const constants = oldConstants as Record<string, Record<string, number>>;
export function rewindSongs(songs: Song[]): Song[] {
  return songs.map(song => ({ ...song, chartConstants: Object.fromEntries(Object.entries(constants[song.id] || {}).map(([d, value]) => [d.toLowerCase(), value])) }));
}
export function rewindSnapshot(source: Snapshot | null): Snapshot | null {
  if (!source) return null;
  const convert = (record: Score): Score[] => {
    const constant = constants[record.songId]?.[record.difficulty];
    return constant ? [{ ...record, chartConstant: constant, rks: chartRks(constant, record.accuracy), pushAcc: null, pushAccHint: null }] : [];
  };
  const records = source.records.flatMap(convert);
  const ap = records.filter(r => r.score === 1_000_000).sort((a, b) => (b.chartConstant || 0) - (a.chartConstant || 0) || (a.songId < b.songId ? -1 : a.songId > b.songId ? 1 : difficulties.indexOf(a.difficulty as any) - difficulties.indexOf(b.difficulty as any))).slice(0, 1);
  const top = best(records, 19);
  return { ...source, records, updated: source.updated.flatMap(convert),
    totalRks: (top.reduce((sum, r) => sum + r.rks, 0) + (ap[0]?.rks || 0)) / 20,
    items: [...ap.map(r => ({ ...r, section: 'AP', position: 1 })), ...top.map((r, i) => ({ ...r, section: 'BEST', position: i + 1 }))] };
}

export interface HistoryItem { rks: number; rksJump: number; createdAt: string }
export interface HistoryPage { items: HistoryItem[]; total: number; currentRks: number; peakRks: number; hasMore: boolean; nextCursor?: string }
export const historyRanges = [{ days: 7, label: '近7天' }, { days: 30, label: '近30天' }, { days: 90, label: '近90天' }, { days: 0, label: '全部' }];
export function historyPoints(items: HistoryItem[], days: number, now: number) {
  return [...items].reverse().filter(p => Number.isFinite(p.rks) && Number.isFinite(p.rksJump) && Number.isFinite(Date.parse(p.createdAt)) && (!days || Date.parse(p.createdAt) >= now - days * 86_400_000)).sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
}
export function coversHistory(page: HistoryPage, days: number, now: number) {
  return !page.hasMore || !!(days && Date.parse(page.items.at(-1)?.createdAt || '') < now - days * 86_400_000);
}
export async function loadHistory(days: number, now: number, fetchPage: (cursor?: string) => Promise<HistoryPage>, cached: HistoryPage | null = null, progress: (page: HistoryPage) => void = () => {}) {
  let result = cached; const cursors = new Set<string>();
  while (!result || !coversHistory(result, days, now)) {
    const cursor: string | undefined = result ? result.nextCursor : undefined;
    if (result && (!cursor || cursors.has(cursor))) throw new Error('历史记录分页未前进，请刷新重试');
    if (cursor) cursors.add(cursor);
    const page = await fetchPage(cursor);
    result = result ? { ...result, items: [...result.items, ...page.items], total: Math.max(result.total, page.total), hasMore: page.hasMore, nextCursor: page.nextCursor } : page;
    progress(result);
    if (!page.items.length && page.hasMore) throw new Error('历史记录分页返回空数据，请刷新重试');
  }
  return result;
}
