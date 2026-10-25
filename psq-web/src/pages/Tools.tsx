import { useEffect, useMemo, useRef, useState } from 'react';
import { Calculator, CalendarCheck, ChevronRight, Gamepad2, Image, MessageSquare, Search, Trophy, Trash2, Upload, WandSparkles } from 'lucide-react';
import { api, imageRequest } from '../api';
import { Art, Confirm, Empty, ImageViewer, Loading, Modal, NumberField, PageHeader, Tabs, useBlobUrl } from '../components';
import { difficulties, fmt, playScore, projectRks, scoreText, solveRks, type Song } from '../core';
import { customComposite, customRows, customScores, customValues, emptyChart, importRanking, type CustomChart, type CustomRow } from '../custom';
import { usePSQ } from '../state';
import { localRead, localWrite } from '../storage';
import { PRODUCT_VERSION } from '../pwa';
import { useContentMotion } from '../motion';

export function More({ go }: { go: (page: string) => void }) {
  const { checkin, notificationCount } = usePSQ();
  const [unavailable, setUnavailable] = useState('');
  const item = (title: string, detail: string, Icon: any, page?: string, badge?: number) => <button className={'feature-row ' + (!page ? 'unavailable' : '')} key={title} onClick={() => page ? go(page) : setUnavailable(title)}><span className="feature-icon"><Icon size={22} /></span><span><strong>{title}</strong><small>{detail}</small></span>{!!badge && <b className="badge">{badge}</b>}<ChevronRight size={18} /></button>;
  return <section className="page"><PageHeader title="更多" />
    <button className="checkin-entry card" onClick={() => go('checkin')}><span className="feature-icon"><CalendarCheck size={24} /></span><span><h2>每日签到</h2><p className="muted small">{checkin?.today ? '今日已签到 · 累计 ' + checkin.totalDays + ' 天' : checkin ? '今日还未签到，领取 15–50 Coin' : '每日可领取 15–50 Coin'}</p></span><ChevronRight /></button>
    <section className="stack"><h2 className="section-label">成绩工具</h2><div className="card feature-list">{item('RKS 计算器', '推算 · 提升估算 · 分数 / ACC', Calculator, 'calculator')}{item('谱面评级达成率', '查看评级分布与个人百分位', Trophy, 'achievement')}{item('自定义 BP30', '编辑谱面 · 生成成绩图', Image, 'custom')}</div></section>
    <section className="stack"><h2 className="section-label">交流</h2><div className="card feature-list">{item('求建议 / 给建议', '分享成绩，交流推分思路', MessageSquare, 'suggestions', notificationCount)}</div></section>
    <section className="stack"><h2 className="section-label">小游戏</h2><div className="card feature-list"><button className="feature-row" onClick={() => go('guess')}><span className="feature-icon guess-icon"><img src="/images/rks-guess.png" alt="" /></span><span><strong>RKS 猜猜乐</strong><small>单人 · 公开匹配</small></span><ChevronRight size={18} /></button>{item('猜曲绘', '暂未开放', Gamepad2)}{item('开字母', '暂未开放', Gamepad2)}</div></section>
    {unavailable && <Modal title={unavailable} onClose={() => setUnavailable('')}>{close => <><p className="muted">暂未开放</p><div className="modal-actions"><button className="button primary" onClick={close}>我知道了</button></div></>}</Modal>}
  </section>;
}

export function CalculatorPage({ go }: { go: (page: string) => void }) {
  const { snapshot, notify, account } = usePSQ();
  const key = 'calculator:' + account;
  const [draft, setDraft] = useState<Record<string, string>>(() => localRead(key, { tab: '三值推算' }));
  const [result, setResult] = useState<any>(null), [error, setError] = useState('');
  const contentMotion = useContentMotion<HTMLElement>(draft.tab || '三值推算', 'form');
  const set = (name: string, value: string) => { setDraft(d => ({ ...d, [name]: value })); setError(''); setResult(null); };
  useEffect(() => localWrite(key, draft), [draft, key]);
  const field = (name: string, label: string, integer = false, placeholder?: string) => <NumberField label={label} value={draft[name] || ''} onChange={v => set(name, v)} integer={integer} placeholder={placeholder} min={0} />;
  const calculate = () => {
    setError('');
    try {
      if (draft.tab === '三值推算') {
        const optional = (name: string) => draft[name]?.trim() ? Number(draft[name]) : null;
        setResult(solveRks(optional('constant'), optional('accuracy'), optional('rks')));
      } else if (draft.tab === '提升估算') {
        if (!snapshot) throw new Error('暂无真实 B30，请先刷新成绩');
        if (!['growthConstant', 'current', 'target'].every(k => draft[k]?.trim())) throw new Error('请填写谱面定数、当前成绩和目标 ACC');
        const estimate = projectRks(snapshot, Number(draft.growthConstant), Number(draft.current), Number(draft.target), draft.metric === 'rks');
        const currentAccount = draft.accountRks?.trim() ? Number(draft.accountRks) : snapshot.totalRks;
        if (!Number.isFinite(currentAccount) || currentAccount < 0) throw new Error('请输入有效的账号 RKS');
        setResult({ ...estimate, currentAccount, projected: currentAccount + estimate.increase });
      } else {
        if (!['notes', 'perfect', 'good', 'bad', 'miss', 'combo'].every(k => draft[k]?.trim())) throw new Error('请填写物量和全部判定数量');
        setResult(playScore(Number(draft.notes), Number(draft.perfect), Number(draft.good), Number(draft.bad), Number(draft.miss), Number(draft.combo)));
      }
    } catch (e) { setResult(null); setError((e as Error).message); }
  };
  return <section ref={contentMotion} className="page readable"><PageHeader title="RKS 计算器" onBack={() => go('more')} /><Tabs values={['三值推算', '提升估算', '分数 / ACC']} value={draft.tab || '三值推算'} onChange={v => set('tab', v)} />
    <form className="card stack" onSubmit={e => { e.preventDefault(); calculate(); }}>
      {(draft.tab || '三值推算') === '三值推算' ? <><p className="muted small">填写任意两项，推算剩余一项。</p>{field('constant', '谱面定数')}{field('accuracy', 'ACC（%）')}{field('rks', '单曲 RKS')}</> : draft.tab === '提升估算' ? <>{field('growthConstant', '谱面定数')}<Tabs values={['当前 ACC', '当前 RKS']} value={draft.metric === 'rks' ? '当前 RKS' : '当前 ACC'} onChange={v => set('metric', v === '当前 RKS' ? 'rks' : 'acc')} />{field('current', draft.metric === 'rks' ? '当前单曲 RKS' : '当前 ACC（%）')}{field('target', '目标 ACC（%）')}{field('accountRks', '当前账号 RKS', false, fmt(snapshot?.totalRks))}<p className="muted tiny">使用当前存档的 Best 27 与 P3 估算。</p></> : <><div className="fields-grid">{field('notes', '谱面物量', true)}{field('perfect', 'Perfect', true)}{field('good', 'Good', true)}{field('bad', 'Bad', true)}{field('miss', 'Miss', true)}{field('combo', 'Max Combo', true)}</div></>}
      {error && <p className="error-message" role="alert">{error}</p>}<button className="button primary" type="submit"><Calculator size={18} />计算</button>
    </form>
    {result && <section className="card result-card"><h2>计算结果</h2>{draft.tab === '提升估算' ? <><div className="result-large accent">{fmt(result.projected)}</div><p className="muted small">预计账号 RKS · +{fmt(result.increase)}</p><div className="metadata-card"><p><span>目标单曲 RKS</span><strong>{fmt(result.target)}</strong></p><p><span>Best 27 贡献提升</span><strong>+{fmt(result.b27Increase)}</strong></p><p><span>P3 贡献提升</span><strong>+{fmt(result.ap3Increase)}</strong></p><p><span>Best 27 门槛</span><strong>{fmt(result.floor)}</strong></p></div></> : draft.tab === '分数 / ACC' ? <><div className="result-large accent">{result.score.toLocaleString('en-US')}</div><p>ACC {fmt(result.accuracy)}%</p></> : <div className="metadata-card"><p><span>谱面定数</span><strong>{fmt(result.constant)}</strong></p><p><span>ACC</span><strong>{fmt(result.accuracy)}%</strong></p><p><span>单曲 RKS</span><strong className="accent">{fmt(result.rks)}</strong></p></div>}</section>}
  </section>;
}

export function ChartPicker({ onPick, onClose, used = new Set<string>() }: { onPick: (song: Song, difficulty: string) => void; onClose: () => void; used?: Set<string> }) {
  const { songs } = usePSQ();
  const [query, setQuery] = useState(''), [remote, setRemote] = useState<string[]>([]), [searching, setSearching] = useState(false);
  const seq = useRef(0);
  useEffect(() => {
    const id = ++seq.current; setRemote([]); setSearching(false);
    if (!query.trim()) return;
    setSearching(true);
    const timer = setTimeout(async () => {
      try { const data = await api('songs/search?q=' + encodeURIComponent(query.trim())); if (id === seq.current) setRemote((data.items || []).map((song: Song) => song.id)); }
      catch {} finally { if (id === seq.current) setSearching(false); }
    }, 220);
    return () => { clearTimeout(timer); seq.current++; };
  }, [query]);
  const filtered = songs.filter((song: Song) => !query.trim() || remote.includes(song.id) || [song.id, song.name, song.composer].some(v => v?.toLowerCase().includes(query.trim().toLowerCase())))
    .flatMap((song: Song) => difficulties.flatMap(difficulty => { const constant = song.chartConstants[difficulty.toLowerCase() as keyof Song['chartConstants']]; return constant == null || used.has(song.id + ':' + difficulty) ? [] : [{ song, difficulty, constant }]; }))
    .sort((a: AchievementChart, b: AchievementChart) => b.constant - a.constant || a.song.name.localeCompare(b.song.name)).slice(0, 120);
  return <Modal title="选择谱面" onClose={onClose} wide className="custom-chart-picker"><div className="search-field"><input autoFocus placeholder="搜索曲目" aria-label="选择谱面搜索" value={query} onChange={e => setQuery(e.target.value)} /></div><div className="custom-picker-list">{filtered.map((entry: AchievementChart) => <button className="custom-picker-row" key={entry.song.id + ':' + entry.difficulty} onClick={() => onPick(entry.song, entry.difficulty)}><span>{entry.song.name}</span><span><strong className="accent">{entry.difficulty}</strong><small className="muted">{fmt(entry.constant, 1)}</small></span></button>)}{!filtered.length && !searching && <Empty title="没有可选的谱面" />}</div>{searching && <p className="muted tiny">搜索中…</p>}</Modal>;
}

type AchievementChart = { song: Song; difficulty: string; constant: number };
function AchievementChartCard({ entry, onPick }: { entry: AchievementChart; onPick?: () => void }) {
  const notes = entry.song.charts?.[entry.difficulty]?.notes ?? entry.song.charts?.[entry.difficulty.toLowerCase()]?.notes;
  const content = <><Art songId={entry.song.id} /><div className="achievement-chart-copy"><h3>{entry.song.name}</h3><p className="muted">{entry.song.composer}</p><strong className="accent">{entry.difficulty}　{fmt(entry.constant, 1)}{notes != null ? '  ·  ' + notes + ' Notes' : ''}</strong></div></>;
  return onPick ? <button className="achievement-chart" onClick={onPick} type="button">{content}</button> : <div className="achievement-chart">{content}</div>;
}
export function Achievement({ go }: { go: (page: string) => void }) {
  const { songs } = usePSQ();
  const [selected, setSelected] = useState<AchievementChart | null>(null), [query, setQuery] = useState(''), [remote, setRemote] = useState<string[]>([]), [limit, setLimit] = useState(60), [searching, setSearching] = useState(false), [data, setData] = useState<any>(null), [loading, setLoading] = useState(false), [error, setError] = useState('');
  const seq = useRef(0), searchSeq = useRef(0), sentinel = useRef<HTMLDivElement>(null);
  const charts = useMemo(() => {
    const needle = query.trim().toLowerCase(), matches = new Set(remote);
    return songs.filter((song: Song) => !needle || matches.has(song.id) || [song.name, song.composer, song.id].some(v => v.toLowerCase().includes(needle)))
      .flatMap((song: Song) => difficulties.flatMap(difficulty => {
        const constant = song.chartConstants[difficulty.toLowerCase() as keyof Song['chartConstants']];
        return constant == null ? [] : [{ song, difficulty, constant }];
      })).sort((a: AchievementChart, b: AchievementChart) => b.constant - a.constant || a.song.name.localeCompare(b.song.name));
  }, [songs, query, remote]);
  useEffect(() => {
    const id = ++searchSeq.current; setRemote([]); setSearching(false);
    if (!query.trim()) return;
    setSearching(true);
    const timer = setTimeout(async () => {
      try { const result = await api('songs/search?q=' + encodeURIComponent(query.trim())); if (id === searchSeq.current) setRemote((result.items || []).map((song: Song) => song.id)); }
      catch {} finally { if (id === searchSeq.current) setSearching(false); }
    }, 450);
    return () => { clearTimeout(timer); searchSeq.current++; };
  }, [query]);
  useEffect(() => {
    if (selected || !sentinel.current || limit >= charts.length) return;
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) setLimit(value => value + 60); }, { rootMargin: '400px' });
    observer.observe(sentinel.current); return () => observer.disconnect();
  }, [selected, limit, charts.length]);
  useEffect(() => () => { seq.current++; }, []);
  const load = async () => {
    if (!selected) return; const id = ++seq.current; setLoading(true); setError('');
    try { const result = await api('songs/achievement-rates?song_id=' + encodeURIComponent(selected.song.id) + '&difficulty=' + selected.difficulty); if (id === seq.current) setData(result); }
    catch (e) { if (id === seq.current) setError((e as Error).message); } finally { if (id === seq.current) setLoading(false); }
  };
  const returnToCharts = () => { seq.current++; setSelected(null); setData(null); setLoading(false); setError(''); };
  const pick = (entry: AchievementChart) => { seq.current++; setSelected(entry); setData(null); setLoading(false); setError(''); window.scrollTo({ top: 0 }); };
  const groups = new Map<number, AchievementChart[]>();
  for (const chart of charts.slice(0, limit)) { const group = groups.get(chart.constant) || []; group.push(chart); groups.set(chart.constant, group); }
  const fraction = (value: number) => Math.max(0, Math.min(1, value || 0));
  return <section className="page readable achievement-page"><PageHeader title="谱面评级达成率" onBack={selected ? returnToCharts : () => go('more')} />
    {!selected ? <>
      <label className="field achievement-search"><span>搜索曲目</span><input value={query} placeholder="可输入曲名、别名、曲师或曲目 ID" onChange={e => { setQuery(e.target.value); setLimit(60); }} /></label>
      <div className="achievement-catalog">{Array.from(groups, ([constant, entries]) => <section className="achievement-group" key={constant}><h2>{fmt(constant, 1)}</h2>{entries.map(entry => <AchievementChartCard key={entry.song.id + ':' + entry.difficulty} entry={entry} onPick={() => pick(entry)} />)}</section>)}</div>
      {searching && <p className="muted tiny">搜索中…</p>}{!charts.length && !searching && <p className="muted">没有匹配谱面</p>}{limit < charts.length && <div ref={sentinel} className="list-sentinel" />}
    </> : <>
      <AchievementChartCard entry={selected} /><div className="button-row achievement-actions"><button className="button outline" onClick={returnToCharts}>{query.trim() ? '返回搜索结果' : '返回定数表'}</button><button className="button primary" disabled={loading} onClick={load}>{data ? '刷新数据' : '查看达成率'}</button></div>
      {loading && <Loading />}{error && <p className="error-message" role="alert">{error}</p>}
      {data && !loading && <div className="achievement-detail stack">
        {data.mine && <section className="card my-achievement"><h2>我的成绩</h2><div className="my-achievement-score"><strong>{scoreText(data.mine.score)}</strong><img src={'/grades/grade_' + data.mine.grade.toLowerCase() + '.png'} alt={data.mine.grade} /></div><p className="muted">超过全部存档</p><strong className="achievement-percent">{fmt(fraction(data.mine.exceededRate) * 100, 1)}%</strong><strong>当前位于　前 {fmt(fraction(data.mine.topRate) * 100, 1)}%</strong><p className="muted">高于 {scoreText(data.mine.exceededCount ?? Math.round(data.total * fraction(data.mine.exceededRate)))} 份存档（共 {scoreText(data.total)} 份）</p><progress className="progress" max={1} value={fraction(data.mine.exceededRate)} /><div className="section-heading muted"><span>较低</span><span>较高</span></div></section>}
        <h2 className="achievement-sample">统计样本 {data.total} 份</h2><section className="card achievement-distribution"><h2>评级分布</h2>{['AP', 'FC', 'V', 'S', 'A', 'B', 'C', 'F'].map(grade => { const rate = data.rates?.find((item: any) => item.grade === grade); return rate && <div className="achievement-distribution-row" key={grade}><div className="section-heading"><strong className="accent">{grade}</strong><span>{fmt(rate.rate * 100, 2)}% · {rate.count} 份</span></div><progress className="progress" max={1} value={fraction(rate.rate)} aria-label={grade + ' 达成率'} /></div>; })}</section>
        {data.total === 0 && <p className="muted small">暂无该谱面的统计样本，玩家刷新存档后会逐步补充。</p>}
      </div>}
    </>}
  </section>;
}

export function CustomRanking({ go }: { go: (page: string) => void }) {
  const { songs, snapshot, preferences, notify, account } = usePSQ();
  const key = 'custom:' + account;
  const [draft, setDraft] = useState<any>(() => localRead(key, { ranking: 'b30', mode: 'manual', b30: Array.from({ length: 36 }, emptyChart), p30: Array.from({ length: 36 }, emptyChart), b30Values: Array(36).fill(''), p30Values: Array(36).fill('') }));
  const [frozen, setFrozen] = useState<CustomRow[] | null>(null), [picker, setPicker] = useState<number | null>(null), [clear, setClear] = useState(false), [image, setImage] = useState<Blob | null>(null), [viewer, setViewer] = useState(false), [busy, setBusy] = useState(false);
  const ranking: string = draft.ranking, charts: CustomChart[] = draft[ranking], manual = draft.mode === 'manual', entries: string[] = draft[ranking + 'Values'];
  const values = manual ? entries.map(v => v.trim() && Number.isFinite(Number(v)) && Number(v) >= 0 ? Number(v) : null) : customValues(charts, songs, ranking);
  const keys = charts.map(c => c.songId ? c.songId + ':' + c.difficulty : null), rows = frozen || customRows(values, manual ? undefined : keys);
  const filled = rows.filter(r => manual ? values[r.sourceIndex] !== null : !!keys[r.sourceIndex]).length;
  const composite = customComposite(values, rows), url = useBlobUrl(image);
  let validation = ''; try { if (!manual) customScores(charts, rows, songs, ranking); } catch (e) { validation = (e as Error).message; }
  useEffect(() => localWrite(key, draft), [draft, key]);
  const patch = (value: any) => { setDraft((d: any) => ({ ...d, ...value })); setImage(null); };
  const changeChart = (i: number, value: Partial<CustomChart>) => patch({ [ranking]: charts.map((c, index) => index === i ? { ...c, ...value } : c) });
  const generate = async () => {
    setBusy(true); try { const scores = customScores(charts, customRows(values, keys), songs, ranking); setImage(await imageRequest('image/bn/user?format=png&width=1440&template=phi-plugin', { custom: true, ranking, scores, theme: preferences.dark ? 'black' : 'white', appVersion: PRODUCT_VERSION })); notify('自定义成绩图已生成'); }
    catch (e) { notify((e as Error).message); } finally { setBusy(false); }
  };
  const importOwn = () => {
    try { const imported = importRanking(snapshot?.records || [], ranking); const importedValues = customValues(imported, songs, ranking).map(v => v == null ? '' : String(v)); patch({ [ranking]: imported, [ranking + 'Values']: importedValues, mode: 'catalog' }); setFrozen(null); notify('已导入我的 ' + ranking.toUpperCase()); }
    catch (e) { notify((e as Error).message); }
  };
  const used = new Set<string>(charts.flatMap((c, i) => i !== picker && (picker! >= 3 || i < 3) && c.songId ? [c.songId + ':' + c.difficulty] : []));
  return <section className="page"><PageHeader title="自定义 BP30" onBack={() => go('more')} /><div className="custom-summary"><Tabs values={['B30', 'P30']} value={ranking.toUpperCase()} onChange={v => { patch({ ranking: v.toLowerCase() }); setFrozen(null); }} /><div><span className="muted tiny">RKS</span><strong className="accent">{fmt(composite)}</strong></div></div><Tabs values={['输入 RKS', '选择谱面']} value={manual ? '输入 RKS' : '选择谱面'} onChange={v => { patch({ mode: v === '输入 RKS' ? 'manual' : 'catalog' }); setFrozen(null); }} /><div className="section-heading"><span className="muted small">{filled} / 36</span><div className="button-row"><button className="text-button" onClick={importOwn} disabled={!snapshot || busy}><Upload size={16} />导入我的 {ranking.toUpperCase()}</button><button className="icon-button" aria-label="清空全部" onClick={() => setClear(true)}><Trash2 size={18} /></button></div></div><progress className="progress" max={36} value={filled} />
    <div className={'custom-slots ' + (manual ? 'manual' : '')} onFocusCapture={() => { if (!frozen) setFrozen(rows); }} onBlurCapture={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setFrozen(null); }}>
      {rows.map((r, i) => { const c = charts[r.sourceIndex], song: Song | undefined = songs.find((s: Song) => s.id === c.songId), isPerfect = ranking === 'p30' || i < 3 || r.mirrored, slotLabel = i < 3 ? 'P' + (i + 1) : ranking === 'p30' ? 'P' + (i + 1) : 'B' + (i - 2); return manual ? <div className="manual-slot" key={(r.mirrored ? 'copy' : 'input') + r.sourceIndex}><strong className="accent">{slotLabel}</strong>{r.mirrored ? <div className="linked-value"><strong>{fmt(values[r.sourceIndex])}</strong><span className="muted tiny">P{rows.slice(0, 3).findIndex(top => top.sourceIndex === r.sourceIndex) + 1}</span></div> : <input aria-label={slotLabel + ' RKS'} type="number" inputMode="decimal" min={0} step="any" placeholder="0.0000" value={entries[r.sourceIndex]} onChange={e => patch({ [ranking + 'Values']: entries.map((v, j) => j === r.sourceIndex ? e.target.value : v) })} />}</div> : <div className="card custom-slot" key={(r.mirrored ? 'copy' : 'input') + r.sourceIndex}><div className="section-heading"><strong className="accent">{slotLabel}</strong><span className="muted small">{r.mirrored ? 'P' + (rows.slice(0, 3).findIndex(top => top.sourceIndex === r.sourceIndex) + 1) + ' · 自动同步' : fmt(values[r.sourceIndex])}</span>{!r.mirrored && <button className="icon-button" aria-label={'清空 ' + slotLabel} onClick={() => changeChart(r.sourceIndex, emptyChart())}><Trash2 size={15} /></button>}</div><button className="slot-picker" disabled={!!r.mirrored} onClick={() => setPicker(r.sourceIndex)}><span><strong>{song?.name || '选择谱面'}</strong>{song && <small className={'difficulty ' + c.difficulty}>{c.difficulty} {fmt(song.chartConstants[c.difficulty.toLowerCase() as keyof Song['chartConstants']], 1)}</small>}</span><ChevronRight size={18} /></button>{song && <div className="fields-grid">{isPerfect ? <><div className="fixed-field"><span>分数</span><strong>1,000,000</strong></div><div className="fixed-field"><span>ACC</span><strong>100.0000%</strong></div></> : <><NumberField label="分数（选填）" value={c.score} integer min={0} max={1000000} onChange={v => changeChart(r.sourceIndex, { score: v })} /><NumberField label="ACC（%）" value={c.accuracy} min={0} max={100} onChange={v => changeChart(r.sourceIndex, { accuracy: v })} /></>}</div>}</div>; })}
    </div>{!manual && <><button className="button primary full" onClick={generate} disabled={!!validation || busy}><WandSparkles size={19} />{busy ? '正在生成…' : '生成自定义 ' + ranking.toUpperCase() + ' 成绩图'}</button><p className="muted tiny">Phi-Plugin 样式{validation ? ' · ' + validation : ''}</p></>}{url && <button className="image-preview-button" onClick={() => setViewer(true)}><img src={url} alt="自定义成绩图" /></button>}{viewer && image && <ImageViewer blob={image} title="自定义成绩图" onClose={() => setViewer(false)} />}{picker !== null && <ChartPicker used={used} onClose={() => setPicker(null)} onPick={(song, difficulty) => { changeChart(picker, { songId: song.id, difficulty, score: ranking === 'p30' || picker < 3 ? '1000000' : '', accuracy: ranking === 'p30' || picker < 3 ? '100' : '' }); setPicker(null); setFrozen(null); }} />}{clear && <Confirm title="清空全部？" body={'清空当前自定义 ' + ranking.toUpperCase() + ' 的全部数据。'} danger onClose={() => setClear(false)} onConfirm={() => { patch({ [ranking]: Array.from({ length: 36 }, emptyChart), [ranking + 'Values']: Array(36).fill('') }); setFrozen(null); }} />}</section>;
}
