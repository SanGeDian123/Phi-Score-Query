import { Fragment, useDeferredValue, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Download, Image as ImageIcon, Search, Share2, ChevronRight, ChevronDown, ChevronUp, History } from 'lucide-react';
import { api, artwork, imageRequest } from '../api';
import { localRead, localWrite } from '../storage';
import './rks-trend.css';
import { best, challengeLabel, difficulties, fmt, grade, gradeSummary, perfect, p30Rks, scoreText, updatedScoreLabel, type Score, type Song } from '../core';
import { Art, Avatar, Collapse, DragScroll, Empty, ImageViewer, Loading, Modal, PageHeader, ScoreRow, Tabs, saveBlob, useBlobUrl } from '../components';
import { usePSQ } from '../state';
import { renderSong } from '../renderers';
import { reducedMotion, useContentMotion } from '../motion';

function AnimatedNumber({ value, digits = 4, className = '' }: { value: number; digits?: number; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null), current = useRef(value);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element || current.current === value || reducedMotion()) { current.current = value; return; }
    const from = current.current, started = performance.now(); let id = 0;
    element.textContent = fmt(from, digits);
    const frame = (time: number) => { const t = reducedMotion() ? 1 : Math.min(1, (time - started) / 500); current.current = from + (value - from) * (1 - Math.pow(1 - t, 3)); element.textContent = fmt(current.current, digits); if (t < 1) id = requestAnimationFrame(frame); };
    id = requestAnimationFrame(frame); return () => cancelAnimationFrame(id);
  }, [value, digits]);
  return <span ref={ref} className={'number ' + className}>{fmt(value, digits)}</span>;
}
export function Overview({ go }: { go: (page: string) => void }) {
  const { snapshot, offline, loading, refreshScores, images, generating, timeRewind } = usePSQ();
  const imageUrl = useBlobUrl(images.B30);
  const [gradesExpanded, setGradesExpanded] = useState(true);
  const [guide] = useState(() => !localRead('rks-trend-guide-shown', false));
  const totals = snapshot ? gradeSummary(snapshot) : null;
  const savedTime = snapshot ? new Date(snapshot.saveUpdatedAt || snapshot.cachedAt) : null;
  const updatedAt = savedTime && Number.isFinite(savedTime.getTime()) ? new Intl.DateTimeFormat('sv-SE', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(savedTime) : '—';
  return <section className="page overview-page">
    <PageHeader title="概览" subtitle={timeRewind ? '时间回溯 · 2025.02.14' : offline ? '离线缓存' : 'TapTap · 大陆版'} refresh={() => refreshScores()} loading={loading} />
    {!snapshot ? (loading ? <Loading text="正在读取最新存档…" /> : <Empty title="暂无成绩" detail="刷新后显示最新存档" action={() => refreshScores()} />) : <>
      <section className="player-overview">
        <div className="player-line"><Avatar avatar={snapshot.avatar} name={snapshot.nickname} /><div><h2>{snapshot.nickname}</h2><p className="muted">{offline ? '离线数据' : '已同步'}</p></div></div>
        <div className="rks-main"><p className="muted small">Ranking Score</p><button className={"rks-open" + (guide && !timeRewind ? " guided" : "")} disabled={timeRewind} aria-label="查看RKS趋势图" onClick={() => { localWrite("rks-trend-guide-shown", true); go("rks-trend"); }}><span className="rks-guide-number"><AnimatedNumber value={snapshot.totalRks} className="rks-large accent" /></span>{guide && !timeRewind && <small>点击查看RKS趋势图</small>}</button></div>
        <div className="overview-secondary">{!timeRewind && <div><p className="muted small">P30 RKS</p><AnimatedNumber value={p30Rks(snapshot.records)} className="rks-secondary" /></div>}<div><p className="muted small">课题模式</p><span className="rks-secondary">{challengeLabel(snapshot.challengeModeRank)}</span></div></div>
        <div className="overview-save-time muted">{updatedAt} 更新</div>
      </section>
      {snapshot.comparison && !timeRewind && <section className="stack updated-section"><div className="section-heading"><h2><History size={21} className="accent" />{snapshot.updated.length ? '更新了' + snapshot.updated.length + '份成绩' : '未发现新的成绩'}</h2></div>
        {snapshot.updated.length ? <div className="updated-grid">{snapshot.updated.map((score: Score) => <button className="updated-score" key={score.songId + score.difficulty} onClick={() => go('song/' + encodeURIComponent(score.songId))}><Art songId={score.songId} /><div className="updated-body"><div className="updated-copy"><h3>{score.songName}</h3><div className="updated-change"><span className={'difficulty ' + score.difficulty}>{score.difficulty}</span><strong>{updatedScoreLabel(score)}</strong></div><p className="muted">{score.isFullCombo ? 'FC · ' : ''}定数 {fmt(score.chartConstant, 1)}</p></div><div className={'updated-rks difficulty-text ' + score.difficulty}><strong>{fmt(score.rks)}</strong><small className="muted">RKS</small></div></div></button>)}</div> : <div className="card unchanged-score muted">本次读取的存档与上次一致。</div>}
      </section>}
      <section className="card grade-overview"><button className="grade-toggle" aria-expanded={gradesExpanded} onClick={() => setGradesExpanded(v => !v)}><span><strong>成绩统计</strong>{!gradesExpanded && <small className="muted">C {totals!.clear} · FC {totals!.fullCombo} · AP {totals!.allPerfect}</small>}</span>{gradesExpanded ? <ChevronUp className="accent" size={22} /> : <ChevronDown className="accent" size={22} />}</button><Collapse open={gradesExpanded}><div className="grade-details"><div className="grade-metrics">{[['Clear', totals!.clear], ['FC', totals!.fullCombo], ['AP', totals!.allPerfect]].map(([label, count]) => <div key={label}><strong className="accent">{count}</strong><span className="muted">{label}</span></div>)}</div>{difficulties.map(d => { const counts = gradeSummary(snapshot, d); return <div className="grade-table-row" key={d}><strong className={'difficulty-text ' + d}>{d}</strong><span>C <b>{counts.clear}</b></span><span>FC <b>{counts.fullCombo}</b></span><span>AP <b>{counts.allPerfect}</b></span></div>; })}</div></Collapse></section>
      {(imageUrl || generating) && <section className="stack home-image-section"><h2>{timeRewind ? 'B19' : 'B30'} 成绩图</h2>{generating && <Loading text={'正在后台生成 ' + generating + ' 成绩图…'} />}{imageUrl && <><button className="image-preview-button" onClick={() => go('image')}><img alt="B30 成绩图" src={imageUrl} /></button><p className="muted tiny center">点击成绩图进入图片页面</p></>}</section>}
    </>}
  </section>;
}
export function Scores({ go }: { go: (page: string) => void }) {
  const { snapshot, loading, refreshScores, timeRewind } = usePSQ();
  const [tab, setTab] = useState('B30'), [n, setN] = useState('30'), [visible, setVisible] = useState(60);
  const contentMotion = useContentMotion<HTMLElement>(tab, '.ranking-section,.p30-summary');
  useEffect(() => setVisible(60), [tab, n]);
  const rows: Score[] = useMemo(() => !snapshot ? [] : timeRewind ? snapshot.items : tab === 'P30' ? perfect(snapshot.records, 10000) : tab === 'Best N' ? best(snapshot.records, Math.min(10000, Number(n) || 0)) : snapshot.items, [snapshot, tab, n, timeRewind]);
  const groups: { title: string; rows: Score[] }[] = tab === 'B30' || timeRewind ? [{ title: timeRewind ? 'P1' : 'P3', rows: rows.filter(r => r.section === 'AP') }, { title: timeRewind ? 'Best 19' : 'Best 27', rows: rows.filter(r => r.section === 'BEST') }] : [{ title: tab, rows: rows.slice(0, visible) }];
  return <section ref={contentMotion} className="page scores-page">
    <PageHeader title="成绩" subtitle={'RKS ' + fmt(snapshot?.totalRks)} refresh={() => refreshScores()} loading={loading} />
    {!timeRewind && <Tabs values={['B30', 'Best N', 'P30']} value={tab} onChange={setTab} />}
    <button className="button primary full" onClick={() => go('image')}><ImageIcon size={18} />{timeRewind ? '查看 B19 图片' : '查看图片BP30'}<ChevronRight size={19} /></button>
    {!timeRewind && tab === 'Best N' && <label className="field"><span>谱面数量</span><input type="number" inputMode="numeric" min="1" max="10000" value={n} onChange={e => setN(e.target.value)} /><small className="muted">共 {rows.length} 张谱面</small></label>}
    {!timeRewind && tab === 'P30' && snapshot && <div className="card p30-summary"><strong>P30 综合 RKS</strong><AnimatedNumber value={p30Rks(snapshot.records)} className="accent" /></div>}
    {!snapshot ? <Empty title="暂无成绩" detail="刷新后显示最新存档" action={() => refreshScores()} /> : groups.map(group => <section className="stack ranking-section" key={group.title}>
      {(tab === 'B30' || timeRewind) && <h2 className="section-label">{group.title}</h2>}
      <div className="scores-grid">{group.rows.map((r, i) => <Fragment key={r.songId + r.difficulty + i}>{tab === 'P30' && i === 27 && <div className="ranking-overflow muted">超出 P30 计算范围</div>}<ScoreRow score={{ ...r, pushAcc: tab === 'P30' ? null : r.pushAcc, position: tab === 'B30' ? r.position : i + 1 }} onClick={timeRewind ? undefined : () => go('song/' + encodeURIComponent(r.songId))} /></Fragment>)}</div>
      {!group.rows.length && <Empty title={tab === 'P30' ? '暂无 All Perfect 谱面' : '暂无符合条件的成绩'} />}
    </section>)}
    {tab !== 'B30' && rows.length > visible && <button className="text-button" onClick={() => setVisible(v => v + 60)}>加载更多</button>}
  </section>;
}
export function SongSearch({ go }: { go: (page: string) => void }) {
  const { songs, notify } = usePSQ();
  const [query, setQuery] = useState(''), [remote, setRemote] = useState<Song[]>([]), [searching, setSearching] = useState(false), [limit, setLimit] = useState(60);
  const seq = useRef(0);
  useEffect(() => {
    setLimit(60); setRemote([]); setSearching(false); const id = ++seq.current;
    if (!query.trim()) { setRemote([]); return; }
    const timer = setTimeout(async () => {
      setSearching(true);
      try { const data = await api('songs/search?q=' + encodeURIComponent(query)); if (id === seq.current) setRemote(data.items || []); }
      catch (e) { if (id === seq.current) notify((e as Error).message); }
      finally { if (id === seq.current) setSearching(false); }
    }, 450);
    return () => clearTimeout(timer);
  }, [query]);
  const filtered = useMemo(() => {
    if (!query.trim()) return [];
    const q = query.toLowerCase(), map = new Map<string, Song>();
    for (const song of remote) { const known = songs.find((s: Song) => s.id === song.id); if (known) map.set(song.id, known); }
    for (const song of songs) if ([song.name, song.id, song.composer, song.chapter].some(value => value?.toLowerCase().includes(q))) map.set(song.id, song);
    return [...map.values()];
  }, [songs, query, remote]);
  return <section className="page"><PageHeader title="单曲查询" /><div className="search-field"><Search size={20} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="输入曲名、别名、曲师或曲目 ID" aria-label="搜索曲目" /><span className="muted small">{searching ? '搜索中' : ''}</span></div>{!query ? <Empty title="查找一首曲目" detail="输入曲名、别名、曲师或曲目 ID" /> : filtered.length ? <><p className="muted small">找到 {filtered.length} 首曲目</p><div className="song-list">{filtered.slice(0, limit).map(song => <SongTile key={song.id} song={song} onClick={() => go('song/' + encodeURIComponent(song.id))} />)}</div>{filtered.length > limit && <button className="text-button" onClick={() => setLimit(v => v + 60)}>加载更多</button>}</> : !searching && <Empty title="没有找到相关曲目" />}</section>;
}
function SongTile({ song, onClick }: { song: Song; onClick: () => void }) {
  const { snapshot } = usePSQ();
  return <button className="song-tile" aria-label={song.name + ' · ' + song.composer} onClick={onClick}><div className="song-tile-heading"><Art songId={song.id} /><div><h3>{song.name}</h3><p className="muted">{[song.composer, song.illustrator].filter(Boolean).join(' · ')}</p></div></div><div className="song-tile-records">{difficulties.map(d => { const constant = song.chartConstants[d.toLowerCase() as keyof typeof song.chartConstants]; if (constant == null) return null; const record: Score | undefined = snapshot?.records.find((r: Score) => r.songId === song.id && r.difficulty === d); return <div className="song-difficulty-row" key={d}><span className={'difficulty-text ' + d}>{d} {fmt(constant, 1)}</span><span>{record ? scoreText(record.score) : '暂无成绩'}</span><span className="muted">{record ? fmt(record.accuracy) + '%' : '—'}</span><strong className="accent">{record ? fmt(record.rks) : '—'}</strong></div>; })}</div></button>;
}
export function SongDetail({ id, go }: { id: string; go: (page: string) => void }) {
  const { songs, snapshot, preferences, notify } = usePSQ();
  const song = songs.find((s: Song) => s.id === id), records: Score[] = snapshot?.records.filter((r: Score) => r.songId === id) || [];
  const [image, setImage] = useState<Blob | null>(null), [busy, setBusy] = useState(false), [viewer, setViewer] = useState(false), [artView, setArtView] = useState(false);
  const url = useBlobUrl(image);
  useEffect(() => { setImage(null); setViewer(false); }, [id]);
  const render = async () => {
    setBusy(true);
    try { setImage(await renderSong(song, records, preferences.songStyle)); }
    catch (e) { notify((e as Error).message); } finally { setBusy(false); }
  };
  if (!song) return <section className="page"><PageHeader title="单曲" onBack={() => go('song')} /><Empty title="曲目暂未收录" /></section>;
  return <section className="page"><PageHeader title={song.name} subtitle={song.composer} onBack={() => go('song')} /><button className="art-banner" onClick={() => setArtView(true)}><Art songId={song.id} full /></button><section className="card metadata-card"><p><span>曲师</span><strong>{song.composer || '—'}</strong></p><p><span>曲绘</span><strong>{song.illustrator || '—'}</strong></p><p><span>章节</span><strong>{song.chapter || '—'}</strong></p></section><button className="button primary" onClick={render} disabled={busy}><ImageIcon size={19} />{busy ? '正在生成单曲成绩图…' : '生成单曲成绩图'}</button>{url && <button className="image-preview-button" onClick={() => setViewer(true)}><img alt="单曲成绩图" src={url} /></button>}<div className="chart-details">{difficulties.map(d => { const constant = song.chartConstants[d.toLowerCase() as keyof typeof song.chartConstants]; if (constant == null) return null; const score = records.find(r => r.difficulty === d), detail = song.charts?.[d]; return <section className="card chart-detail" key={d}><div className="section-heading"><span className={'difficulty ' + d}>{d} {fmt(constant, 1)}</span>{score && <span className="accent">{grade(score.score, score.isFullCombo)}</span>}</div><div className="metadata-card"><p><span>谱师</span><strong>{detail?.charter || '—'}</strong></p><p><span>物量</span><strong>{detail?.notes ?? '—'}</strong></p></div>{score ? <div className="detail-score"><strong>{scoreText(score.score)}</strong><span>ACC {fmt(score.accuracy)}%</span><span className="accent">RKS {fmt(score.rks)}</span>{score.pushAcc != null && <span className="muted small">推分 ACC {fmt(score.pushAcc)}%</span>}</div> : <p className="muted small">暂无成绩</p>}</section>; })}</div>{viewer && image && <ImageViewer blob={image} title="单曲成绩图" onClose={() => setViewer(false)} />}{artView && <ImageViewer url={artwork(id, true)} title={song.name} onClose={() => setArtView(false)} />}</section>;
}
export function ConstantTable({ go }: { go: (page: string) => void }) {
  const { songs, snapshot, timeRewind } = usePSQ();
  const [level, setLevel] = useState('全部'), [difficulty, setDifficulty] = useState('全部'), [query, setQuery] = useState(''), [limit, setLimit] = useState(160);
  const deferredQuery = useDeferredValue(query);
  const contentMotion = useContentMotion<HTMLElement>(level + ':' + difficulty, '.constant-grid');
  useEffect(() => setLimit(160), [level, difficulty, query]);
  const charts = useMemo(() => {
    const result: { song: Song; difficulty: string; constant: number }[] = [];
    const search = deferredQuery.toLowerCase();
    for (const song of songs) for (const d of difficulties) { const c = song.chartConstants[d.toLowerCase() as keyof typeof song.chartConstants]; if (c != null && (level === '全部' || Math.floor(c) === Number(level)) && (difficulty === '全部' || difficulty === d) && (!search || [song.name, song.id, song.composer].some(v => v.toLowerCase().includes(search)))) result.push({ song, difficulty: d, constant: c }); }
    return result.sort((a, b) => b.constant - a.constant || a.song.name.localeCompare(b.song.name));
  }, [songs, level, difficulty, deferredQuery]);
  const groups = new Map<number, typeof charts>();
  for (const c of charts.slice(0, limit)) { const entries = groups.get(c.constant) || []; entries.push(c); groups.set(c.constant, entries); }
  return <section ref={contentMotion} className="page"><PageHeader title="定数表" subtitle={timeRewind ? "时间回溯 · 2025.02.14" : "曲目与定数实时同步"} /><DragScroll label="选择等级">{['全部', ...Array.from({ length: 18 }, (_, i) => String(18 - i))].map(v => <button className={'chip ' + (level === v ? 'selected' : '')} key={v} onClick={() => setLevel(v)}>{v}</button>)}</DragScroll><DragScroll label="选择难度">{['全部', ...difficulties].map(v => <button className={'chip ' + (difficulty === v ? 'selected' : '')} key={v} onClick={() => setDifficulty(v)}>{v}</button>)}</DragScroll><div className="search-field"><Search size={19} /><input placeholder="搜索曲目" aria-label="搜索定数表" value={query} onChange={e => setQuery(e.target.value)} /></div>{[...groups.entries()].map(([constant, entries]) => <section className="stack" key={constant}><h3 className="constant-header">{fmt(constant, 1)}</h3><div className="constant-grid">{entries.map(c => { return <button className="constant-card" aria-label={c.song.name + ' ' + c.difficulty + ' ' + fmt(c.constant, 1)} key={c.song.id + c.difficulty} disabled={timeRewind} onClick={() => go('song/' + encodeURIComponent(c.song.id))}><Art songId={c.song.id} /><div><h3>{c.song.name}</h3><div className="constant-meta"><span className={'difficulty ' + c.difficulty}>{c.difficulty}</span><strong className={'difficulty-text ' + c.difficulty}>{fmt(c.constant, 1)}</strong><ChevronRight size={18} className="muted" /></div></div></button>; })}</div></section>)}{!charts.length && <Empty title="没有匹配的谱面" />}{charts.length > limit && <button className="text-button" onClick={() => setLimit(v => v + 160)}>加载更多</button>}</section>;
}
export function Images() {
  const { images, generating, generateImage, notify, timeRewind } = usePSQ();
  const [tab, setTab] = useState('B30'), [n, setN] = useState('30'), [viewer, setViewer] = useState(false);
  const kind = timeRewind ? 'B30' : tab, label = timeRewind ? 'B19' : kind;
  const blob: Blob | undefined = images[kind], url = useBlobUrl(blob);
  const valid = /^\d+$/.test(n) && Number(n) >= 1 && Number(n) <= 200;
  const download = async (share = false) => { if (blob) try { await saveBlob(blob, 'Phi-Score-Query-' + label + '.png', share); } catch (e) { if ((e as Error).name !== 'AbortError') notify('保存失败，请重试'); } };
  return <section className="page"><PageHeader title={timeRewind ? 'B19 图片' : '成绩图片'} />
    {!timeRewind && <Tabs values={['B30', 'P30', 'Best N']} value={tab} onChange={setTab} />}
    {kind === 'Best N' && <label className="field"><span>谱面数量</span><input type="number" inputMode="numeric" min="1" max="200" value={n} onChange={e => setN(e.target.value)} /><small className="muted">至多支持200张谱面</small></label>}
    <button className="button primary full" disabled={!!generating || (kind === 'Best N' && !valid)} onClick={() => generateImage(kind, Number(n))}><ImageIcon size={19} />{generating === kind ? '正在生成…' : '生成 ' + label + ' 成绩图'}</button>
    {generating && <Loading text={'正在后台生成 ' + (timeRewind ? 'B19' : generating) + ' 成绩图…'} />}
    {url ? <><button className="image-preview-button" onClick={() => setViewer(true)}><img alt={label + ' 成绩图'} src={url} /></button><div className="button-row"><button className="button outline" onClick={() => download()}><Download size={18} />保存图片</button><button className="button outline" onClick={() => download(true)}><Share2 size={18} />分享</button></div></> : <Empty title="暂无成绩图" detail="生成后可保存、分享和放大查看" />}
    {viewer && blob && <ImageViewer blob={blob} title={label + ' 成绩图'} onClose={() => setViewer(false)} />}
  </section>;
}
export function Leaderboard({ go }: { go: (page: string) => void }) {
  const { notify, snapshot } = usePSQ();
  const [items, setItems] = useState<any[]>([]), [me, setMe] = useState<any>(null), [total, setTotal] = useState(0), [loading, setLoading] = useState(false), [limit, setLimit] = useState(100), [showTop, setShowTop] = useState(false), [targetRank, setTargetRank] = useState<number | null>(null);
  const seq = useRef(0), sentinel = useRef<HTMLDivElement>(null), list = useRef<HTMLDivElement>(null);
  const load = async () => {
    const id = ++seq.current; setLoading(true);
    try {
      const fetchTop = async () => {
        let entries: any[] = [], cursor = '', count = 0;
        while (entries.length < 1500) {
          const page = await api('leaderboard/rks/top?lite=true&limit=' + Math.min(1000, 1500 - entries.length) + (cursor ? '&cursor=' + encodeURIComponent(cursor) : '&offset=' + entries.length));
          entries.push(...(page.items || [])); count = page.total; if (!page.items?.length || entries.length >= count) break;
          cursor = page.nextCursor || '';
        }
        return { entries: entries.slice(0, 1500), count };
      };
      const [mine, top] = await Promise.all([api('leaderboard/rks/me', { taptapVersion: 'cn' }), fetchTop()]);
      if (id === seq.current) { setMe(mine); setItems(top.entries); setTotal(Math.max(mine.total || 0, top.count || 0, top.entries.length)); }
    } catch (e) { if (id === seq.current) notify((e as Error).message); } finally { if (id === seq.current) setLoading(false); }
  };
  useEffect(() => { load(); return () => { seq.current++; }; }, []);
  useEffect(() => {
    const scroll = () => setShowTop(window.scrollY > 120);
    scroll(); window.addEventListener('scroll', scroll, { passive: true }); return () => window.removeEventListener('scroll', scroll);
  }, []);
  useEffect(() => {
    if (!sentinel.current || limit >= items.length) return;
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) setLimit(value => value + 100); }, { rootMargin: '500px' });
    observer.observe(sentinel.current); return () => observer.disconnect();
  }, [items.length, limit]);
  useEffect(() => {
    if (targetRank == null) return;
    list.current?.querySelector('[data-rank="' + targetRank + '"]')?.scrollIntoView({ behavior: reducedMotion() ? 'instant' : 'smooth', block: 'center' }); setTargetRank(null);
  }, [targetRank, limit]);
  const ownIndex = items.findIndex(entry => entry.rank === me?.rank);
  const jumpToOwn = () => { if (ownIndex >= 0) { setLimit(value => Math.max(value, ownIndex + 60)); setTargetRank(me.rank); } };
  const percentile = me?.percentile ?? (me?.rank > 0 && total ? (total - me.rank) / total * 100 : 0);
  return <section className="page leaderboard-page"><PageHeader title="排行榜" subtitle={me ? '共 ' + total.toLocaleString('zh-CN') + ' 位公开玩家' : '公开玩家 RKS 排名'} refresh={load} loading={loading} />
    {loading && !me ? <Loading /> : !me ? <Empty title="暂未读取排行榜" detail="刷新后会提交当前存档 RKS，并读取公开 Ranklist。" action={load} label="读取排行榜" /> : <>
      <button className="current-player-rank" onClick={jumpToOwn} aria-label={ownIndex >= 0 ? '跳转到我的排名' : undefined} type="button"><Avatar avatar={snapshot?.avatar} name={snapshot?.nickname || '当前玩家'} /><div className="current-player-copy"><h2>{snapshot?.nickname || '当前玩家'}</h2>{!!snapshot?.challengeModeRank && <strong>课题模式 {challengeLabel(snapshot.challengeModeRank)}</strong>}<p>{me.rank > 1500 ? '当前排名不在前 1,500 名' : me.rank > 0 ? '超过 ' + fmt(percentile, 2) + '% 的公开玩家' : '刷新存档后生成排名'}</p></div><div className="current-player-score"><strong>{me.rank > 0 ? '#' + me.rank : '--'}</strong><span>{fmt(me.score)} RKS</span></div></button>
      <h2 className="leaderboard-label accent">排名</h2><div className="rank-list" role="list" ref={list}>{items.slice(0, limit).map(entry => {
        const name = entry.nickname?.trim() || entry.alias?.trim() || 'Phigros Player';
        return <div className="rank-row" role="listitem" aria-label={'#' + entry.rank + ' ' + name + ' · ' + fmt(entry.score) + ' RKS'} key={entry.rank} data-rank={entry.rank}><strong className={'rank-number' + (entry.rank <= 3 ? ' rank-top' : '')}>#{entry.rank}</strong><Avatar avatar={entry.avatar} name={name} /><div className="rank-name"><h3>{name}</h3>{!!entry.challengeModeRank && <span className="accent">课题 {challengeLabel(entry.challengeModeRank)}</span>}</div><div className="rank-score"><strong className="accent">{fmt(entry.score)}</strong><small className="muted">RKS</small></div></div>;
      })}</div>{!items.length && <p className="leaderboard-empty muted">还没有可公开显示的排行榜记录。玩家刷新存档后会自动加入。</p>}{limit < items.length && <div ref={sentinel} className="list-sentinel" />}
    </>}
    {showTop && <button className="leaderboard-to-top" aria-label="回到排行榜顶部" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}><ChevronUp size={24} /></button>}
  </section>;
}
