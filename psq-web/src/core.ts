export type Difficulty = 'EZ' | 'HD' | 'IN' | 'AT';
export interface Song { id: string; name: string; composer: string; illustrator: string; chartConstants: Partial<Record<Lowercase<Difficulty>, number | null>>; chapter?: string; charts?: Record<string, { charter: string; notes: number | null }> }
export interface Score { songId: string; songName: string; difficulty: string; chartConstant: number | null; score: number; accuracy: number; rks: number; isFullCombo: boolean; pushAcc?: number | null; pushAccHint?: any; position?: number; section?: string; scoreChanged?: boolean; accuracyChanged?: boolean; fullComboChanged?: boolean }
export interface Snapshot { totalRks: number; items: Score[]; records: Score[]; updated: Score[]; comparison: boolean; gradeCounts: Record<string, { C: number; FC: number; P: number }>; nickname: string; avatar: string; challengeModeRank: number; cachedAt: number; saveUpdatedAt: string | null; unknownSongIds: string[] }
export const difficulties: Difficulty[] = ['EZ', 'HD', 'IN', 'AT'];
export const fmt = (value: number | null | undefined, places = 4) => value == null || !Number.isFinite(value) ? '—' : value.toFixed(places);
export const scoreText = (score: number) => Math.round(score).toLocaleString('en-US');
export function validAvatarName(value: unknown): string {
  if (typeof value !== 'string') return '';
  const name = value.trim();
  return !name || name === '...' || name.length > 1024 || /[\x00-\x1f\x7f-\x9f]/.test(name) ? '' : name;
}
export function gradeSummary(snapshot: Snapshot, difficulty?: string) {
  const records = snapshot.records.filter(r => !difficulty || r.difficulty === difficulty);
  if (records.length) return {
    clear: records.filter(r => r.score > 0 || r.accuracy > 0).length,
    fullCombo: records.filter(r => r.isFullCombo || r.score >= 1000000).length,
    allPerfect: records.filter(r => r.score >= 1000000 || r.accuracy >= 100).length,
  };
  const counts = difficulty ? [snapshot.gradeCounts[difficulty]].filter(Boolean) : Object.values(snapshot.gradeCounts);
  return counts.reduce((total, c) => ({ clear: total.clear + c.C + c.FC + c.P, fullCombo: total.fullCombo + c.FC + c.P, allPerfect: total.allPerfect + c.P }), { clear: 0, fullCombo: 0, allPerfect: 0 });
}
export function updatedScoreLabel(score: Score) {
  const parts = [];
  if (score.scoreChanged) parts.push('分数 ' + scoreText(score.score));
  if (score.accuracyChanged) parts.push('ACC ' + fmt(score.accuracy) + '%');
  if (score.fullComboChanged && !score.scoreChanged && !score.accuracyChanged) parts.push(score.isFullCombo ? 'FC' : 'FC 状态已更新');
  return parts.join(' · ') || '成绩已更新';
}
export function chartRks(constant: number, accuracy: number) {
  if (!Number.isFinite(constant) || constant <= 0) throw new Error('谱面定数必须大于 0');
  if (!Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100) throw new Error('ACC 必须在 0 到 100 之间');
  return accuracy < 70 ? 0 : Math.pow((accuracy - 55) / 45, 2) * constant;
}
export function grade(score: number, fc = false) {
  if (score === 1000000) return 'AP';
  if (fc) return 'FC';
  return score >= 960000 ? 'V' : score >= 920000 ? 'S' : score >= 880000 ? 'A' : score >= 820000 ? 'B' : score >= 700000 ? 'C' : 'F';
}
export function challengeLabel(rank: number) {
  if (!rank) return '—';
  return (['', '绿', '蓝', '红', '金', '彩'][Math.floor(rank / 100)] || '') + (rank % 100);
}
export const best = (records: Score[], limit = 30) => [...records].sort((a, b) => b.rks - a.rks || b.score - a.score || a.songName.localeCompare(b.songName) || difficulties.indexOf(a.difficulty as Difficulty) - difficulties.indexOf(b.difficulty as Difficulty)).slice(0, Math.max(0, limit));
export const perfect = (records: Score[], limit = 30) => best(records.filter(r => r.score === 1000000), limit);
export const p30Rks = (records: Score[]) => { const p27 = perfect(records, 27); return (p27.reduce((s, r) => s + r.rks, 0) + p27.slice(0, 3).reduce((s, r) => s + r.rks, 0)) / 30; };
export function makeSnapshot(response: any, songs: Song[], previous?: Snapshot | null): Snapshot {
  const byId = new Map(songs.map(song => [song.id, song]));
  const unknown = new Set<string>(response.unknownSongIds || []);
  const records: Score[] = [];
  for (const [id, raw] of Object.entries<any[]>(response.save.game_record || {})) {
    const song = byId.get(id);
    if (!song || unknown.has(id)) continue;
    for (const record of raw || []) {
      const constant = record.chart_constant ?? song.chartConstants[record.difficulty.toLowerCase() as Lowercase<Difficulty>] ?? null;
      records.push({ songId: id, songName: song.name, difficulty: record.difficulty.toUpperCase(), chartConstant: constant, score: record.score, accuracy: record.accuracy, isFullCombo: !!record.is_full_combo, rks: constant ? chartRks(constant, record.accuracy) : 0, pushAcc: record.push_acc, pushAccHint: record.push_acc_hint });
    }
  }
  const lookup = new Map(records.map(r => [r.songId + ':' + r.difficulty, r]));
  const items = (response.rks.b30Charts || []).flatMap((r: any, i: number) => {
    const record = lookup.get(r.songId + ':' + r.difficulty.toUpperCase());
    return record ? [{ ...record, rks: r.rks, section: i < 27 ? 'BEST' : 'AP', position: i < 27 ? i + 1 : i - 26 }] : [];
  });
  const old = new Map(previous?.records.map(r => [r.songId + ':' + r.difficulty, r]));
  const updated = previous ? records.map(r => {
    const o = old.get(r.songId + ':' + r.difficulty);
    return { ...r, scoreChanged: !o || r.score !== o.score, accuracyChanged: !o || Math.abs(r.accuracy - o.accuracy) > .00001, fullComboChanged: !o || r.isFullCombo !== o.isFullCombo };
  }).filter(r => r.scoreChanged || r.accuracyChanged || r.fullComboChanged).sort((a, b) => b.rks - a.rks) : [];
  return { totalRks: response.rks.totalRks, items, records, updated, comparison: !!previous, gradeCounts: response.gradeCounts || {}, nickname: response.playerNickname || 'Phigros Player', avatar: validAvatarName(response.save.user?.avatar) || validAvatarName(response.save.summaryParsed?.avatar), challengeModeRank: response.save.summaryParsed?.challenge_mode_rank || 0, cachedAt: Date.now(), saveUpdatedAt: response.save.updatedAt || null, unknownSongIds: [...unknown] };
}
export function solveRks(constant: number | null, accuracy: number | null, rks: number | null) {
  if ([constant, accuracy, rks].filter(v => v !== null).length !== 2) throw new Error('请填写任意两项并留空一项');
  if (constant === null) {
    if (accuracy! < 70 || accuracy! > 100 || rks! < 0) throw new Error('反推定数时 ACC 需在 70 到 100 之间');
    return { constant: rks! / Math.pow((accuracy! - 55) / 45, 2), accuracy: accuracy!, rks: rks! };
  }
  if (accuracy === null) {
    if (constant <= 0 || rks! < 0 || rks! > constant) throw new Error('单曲 RKS 必须在 0 到谱面定数之间');
    return { constant, accuracy: rks === 0 ? 70 : 45 * Math.sqrt(rks! / constant) + 55, rks: rks! };
  }
  return { constant, accuracy, rks: chartRks(constant, accuracy) };
}
export function playScore(notes: number, p: number, g: number, b: number, m: number, combo: number) {
  if (![notes, p, g, b, m, combo].every(Number.isInteger) || notes <= 0 || [p, g, b, m, combo].some(n => n < 0)) throw new Error('请输入有效的物量和判定数量');
  if (p + g + b + m !== notes) throw new Error('Perfect、Good、Bad、Miss 数量之和必须等于谱面物量');
  if (combo > p + g || combo > notes) throw new Error('Max Combo 不能超过 Perfect 与 Good 数量之和');
  return { score: Math.ceil((combo * 100000 + p * 900000 + g * 585000) / notes), accuracy: (p + g * .65) / notes * 100 };
}
export function projectRks(snapshot: Snapshot, constant: number, current: number, targetAccuracy: number, currentIsRks = false) {
  if (!snapshot.items.length) throw new Error('暂无真实 B30，请先刷新成绩');
  const value = currentIsRks ? current : chartRks(constant, current);
  if (value < 0 || value > constant) throw new Error('当前单曲 RKS 必须在 0 到谱面定数之间');
  const target = Math.max(value, chartRks(constant, targetAccuracy));
  const simulate = (items: Score[], eligible: boolean) => {
    if (!eligible) return { delta: 0, enters: false };
    const candidates = items.map(r => r.rks);
    const matching = items.map((r, i) => ({ r, i })).filter(({ r }) => Math.abs(r.rks - value) <= .005001 && (r.chartConstant == null || Math.abs(r.chartConstant - constant) <= .005001)).sort((a, b) => Math.abs(a.r.rks - value) - Math.abs(b.r.rks - value))[0];
    if (matching) candidates.splice(matching.i, 1);
    candidates.push(target);
    const limit = items[0]?.section === 'AP' ? 3 : 27;
    const selected = candidates.sort((a, b) => b - a).slice(0, limit);
    const delta = Math.max(0, selected.reduce((s, n) => s + n, 0) - items.reduce((s, n) => s + n.rks, 0)) / 30;
    return { delta, enters: !!matching || selected.filter(n => Math.abs(n - target) <= .005001).length > items.filter(r => Math.abs(r.rks - target) <= .005001).length };
  };
  const b27 = snapshot.items.filter(r => r.section === 'BEST').slice(0, 27);
  const ap3 = snapshot.items.filter(r => r.section === 'AP').slice(0, 3);
  const a = simulate(b27, true), p = simulate(ap3, targetAccuracy >= 100 || (currentIsRks ? Math.abs(value - constant) <= .005001 : current >= 100));
  return { target, increase: a.delta + p.delta, projected: snapshot.totalRks + a.delta + p.delta, b27Increase: a.delta, ap3Increase: p.delta, floor: b27.at(-1)?.rks || 0, entersBest: a.enters, entersAp: p.enters };
}
export function beijingDate() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}
