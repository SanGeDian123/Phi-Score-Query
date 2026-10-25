import { best, chartRks, perfect, type Score, type Song } from './core';
export interface CustomChart { songId: string; difficulty: string; accuracy: string; score: string }
export interface CustomRow { sourceIndex: number; mirrored?: boolean }
export const emptyChart = (): CustomChart => ({ songId: '', difficulty: '', accuracy: '', score: '' });
export function customValues(charts: CustomChart[], songs: Song[], ranking: string) {
  return charts.map((c, i) => {
    const constant = songs.find(s => s.id === c.songId)?.chartConstants[c.difficulty.toLowerCase() as keyof Song['chartConstants']];
    if (!constant || (ranking !== 'p30' && i >= 3 && c.accuracy === '')) return null;
    try { return chartRks(constant, ranking === 'p30' || i < 3 ? 100 : Number(c.accuracy)); } catch { return null; }
  });
}
// Preserve the Android source slots while changing their presentation after input loses focus.
export function customRows(values: (number | null)[], keys?: (string | null)[]): CustomRow[] {
  if (values.length !== 36) throw new Error('需要 36 个输入槽位');
  const value = (row: CustomRow) => values[row.sourceIndex];
  const sort = (rows: CustomRow[]) => {
    const sorted = rows.filter(r => value(r) !== null).sort((a, b) => value(b)! - value(a)!);
    let i = 0; return rows.map(r => value(r) !== null ? sorted[i++] : r);
  };
  const inputs = values.map((_, sourceIndex) => ({ sourceIndex }));
  const top = sort(inputs.slice(0, 3)), main: CustomRow[] = inputs.slice(3);
  if (top.every(r => value(r) !== null)) for (const ap of top) {
    const duplicate = keys ? main.findIndex(r => !r.mirrored && keys[r.sourceIndex] && keys[r.sourceIndex] === keys[ap.sourceIndex]) : -1;
    const reserved = ap.sourceIndex;
    const free = (r: CustomRow) => value(r) === null && (!keys || !keys[r.sourceIndex]);
    const empty = main[reserved] && free(main[reserved]) ? reserved : main.findIndex(free);
    const copy = { ...ap, mirrored: true };
    if (duplicate >= 0) main[duplicate] = copy;
    else if (empty >= 0) main[empty] = copy;
    else main.push(copy);
  }
  return [...top, ...sort(main).slice(0, 33)];
}
export function customComposite(values: (number | null)[], rows: CustomRow[]) {
  return rows.slice(0, 30).reduce((n, row) => n + (values[row.sourceIndex] || 0), 0) / 30;
}
export function customScores(charts: CustomChart[], rows: CustomRow[], songs: Song[], ranking: string) {
  if (rows.length !== 36) throw new Error('请填满 36 张谱面');
  const resolved = rows.map(r => ({ ...charts[r.sourceIndex], ...(r.mirrored ? { accuracy: '100', score: '1000000' } : {}) }));
  const values = customValues(resolved, songs, ranking);
  const keys = resolved.map(c => c.songId + ':' + c.difficulty);
  for (const group of [keys.slice(0, 3), keys.slice(3)]) if (new Set(group).size !== group.length) throw new Error('同一区域内谱面不能重复');
  return resolved.map((c, i) => {
    if (!c.songId || !['EZ', 'HD', 'IN', 'AT'].includes(c.difficulty)) throw new Error('请选择第 ' + (i + 1) + ' 张谱面');
    if (values[i] === null) throw new Error('请补全第 ' + (i + 1) + ' 张谱面的 ACC');
    const isPerfect = ranking === 'p30' || i < 3;
    const score = isPerfect ? 1000000 : c.score === '' ? undefined : Number(c.score);
    if (score !== undefined && (!Number.isInteger(score) || score < 0 || score > 1000000)) throw new Error('第 ' + (i + 1) + ' 张谱面分数无效');
    return { song: c.songId, difficulty: c.difficulty, acc: isPerfect ? 100 : Number(c.accuracy), ...(score === undefined ? {} : { score }) };
  });
}
export function importRanking(records: Score[], ranking: string): CustomChart[] {
  const top = perfect(records, 3), eligible = ranking === 'p30' ? records.filter(r => r.score === 1000000) : records;
  if (!eligible.length) throw new Error('暂无可导入的 ' + ranking.toUpperCase() + ' 成绩');
  const keys = new Set(top.length === 3 ? top.map(r => r.songId + ':' + r.difficulty) : []);
  const main = best(eligible.filter(r => !keys.has(r.songId + ':' + r.difficulty)), 33);
  return Array.from({ length: 36 }, (_, i) => {
    const r = i < 3 ? top[i] : main[i - 3];
    return r ? { songId: r.songId, difficulty: r.difficulty, accuracy: String(r.accuracy), score: String(r.score) } : emptyChart();
  });
}
