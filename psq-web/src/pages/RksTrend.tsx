import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { api } from '../api';
import { localWrite } from '../storage';
import { Empty, PageHeader, Tabs } from '../components';
import { fmt } from '../core';
import { coversHistory, historyPoints, historyRanges, loadHistory, type HistoryPage } from '../release-features';
import './rks-trend.css';

const change = (value: number) => Math.abs(value) < .00005 ? '±0.0000' : (value > 0 ? '+' : '') + fmt(value);
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
export function RksTrend({ go }: { go: (page: string) => void }) {
  const [days, setDays] = useState(30), [revision, setRevision] = useState(0), [history, setHistory] = useState<HistoryPage | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState(''), [count, setCount] = useState(0), [chosen, setChosen] = useState(-1);
  useEffect(() => { localWrite('rks-trend-guide-shown', true); }, []);
  const cache = useRef<HistoryPage | null>(null), windowEnd = useRef(Date.now()), dragging = useRef(false);
  const gradient = useId().replace(/:/g, '');
  useEffect(() => {
    const controller = new AbortController(); let partial = cache.current;
    setLoading(true); setError(''); setCount(partial?.items.length || 0);
    loadHistory(days, windowEnd.current, cursor => api<HistoryPage>('rks/history', { auth: {}, limit: 200, ...(cursor ? { cursor } : {}) }, 'POST', controller.signal), partial,
      page => { if (!controller.signal.aborted) { partial = page; setCount(page.items.length); } })
      .then(result => { if (!controller.signal.aborted) { cache.current = result; setHistory(result); } })
      .catch((reason: Error) => { if (!controller.signal.aborted) { cache.current = partial; setHistory(partial); setError(reason.message); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [days, revision]);
  const points = useMemo(() => historyPoints(history?.items || [], days, windowEnd.current), [history, days]);
  const index = chosen >= 0 && chosen < points.length ? chosen : points.length - 1, selected = points[index];
  const geometry = useMemo(() => {
    if (!points.length) return null;
    const low = points.reduce((n, p) => Math.min(n, p.rks), Infinity), high = points.reduce((n, p) => Math.max(n, p.rks), -Infinity), pad = Math.max(.002, (high - low) * .22), min = Math.max(0, low - pad), max = high + pad;
    const start = Date.parse(points[0].createdAt), duration = Date.parse(points.at(-1)!.createdAt) - start;
    const positions = points.map((p, i) => ({ x: 56 + 524 * (points.length === 1 ? .5 : duration ? (Date.parse(p.createdAt) - start) / duration : i / (points.length - 1)), y: 220 - (p.rks - min) / (max - min) * 200 }));
    return { min, max, positions, path: positions.map((p, i) => `${i ? 'L' : 'M'}${p.x},${p.y}`).join(' ') };
  }, [points]);
  const refresh = () => { cache.current = null; windowEnd.current = Date.now(); setRevision(v => v + 1); };
  const choose = (x: number, left: number, width: number) => {
    if (!geometry) return; const position = (x - left) / width * 600;
    let nearest = 0; for (let i = 1; i < geometry.positions.length; i++) if (Math.abs(geometry.positions[i].x - position) < Math.abs(geometry.positions[nearest].x - position)) nearest = i;
    setChosen(nearest);
  };
  return <section className="page readable rks-trend-page">
    <PageHeader title="RKS趋势图" onBack={() => go('home')} refresh={refresh} loading={loading} />
    {history && !!history.items.length && <div className="trend-summary"><p className="muted small">当前 RKS</p><strong className="rks-large accent">{fmt(history.currentRks)}</strong><div className="trend-stats"><div><span>历史最高</span><strong>{fmt(history.peakRks)}</strong></div><div><span>区间变化</span><strong className={points.length > 1 && points.at(-1)!.rks < points[0].rks ? 'trend-negative' : 'accent'}>{points.length > 1 ? change(points.at(-1)!.rks - points[0].rks) : '—'}</strong></div><div><span>区间记录</span><strong>{points.length}</strong></div></div></div>}
    <Tabs values={historyRanges.map(r => r.label)} value={historyRanges.find(r => r.days === days)!.label} onChange={label => { setChosen(-1); setDays(historyRanges.find(r => r.label === label)!.days); }} />
    {loading && <div className="trend-progress" role="status"><span />正在加载区间记录 · 已读取 {count} 条</div>}
    {error && <div className="card"><p className="error-message">{history && !coversHistory(history, days, windowEnd.current) ? '区间记录未加载完整，已保留读取的数据' : '暂时无法更新趋势'}</p><p className="muted small">{error}</p><button className="text-button" onClick={refresh}>重试</button></div>}
    {geometry && selected ? <>
      <section className="card trend-chart-card"><h2>变化轨迹</h2>
        <svg className="trend-chart" viewBox="0 0 600 260" role="img" aria-label={'RKS趋势图，' + points.length + '条记录'} onPointerDown={e => { dragging.current = true; e.currentTarget.setPointerCapture(e.pointerId); const r = e.currentTarget.getBoundingClientRect(); choose(e.clientX, r.left, r.width); }} onPointerMove={e => { if (dragging.current) { const r = e.currentTarget.getBoundingClientRect(); choose(e.clientX, r.left, r.width); } }} onPointerUp={() => { dragging.current = false; }} onPointerCancel={() => { dragging.current = false; }}>
          <defs><linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="currentColor" stopOpacity=".18" /><stop offset="1" stopColor="currentColor" stopOpacity=".01" /></linearGradient></defs>
          {[0, 1, 2, 3].map(i => <g key={i}><line x1="56" x2="580" y1={20 + i * 200 / 3} y2={20 + i * 200 / 3} className="trend-grid" /><text x="0" y={25 + i * 200 / 3}>{fmt(geometry.max - (geometry.max - geometry.min) * i / 3)}</text></g>)}
          <path d={geometry.path + ` L${geometry.positions.at(-1)!.x},220 L${geometry.positions[0].x},220 Z`} fill={`url(#${gradient})`} />
          <path d={geometry.path} fill="none" stroke="currentColor" strokeWidth="2.4" vectorEffect="non-scaling-stroke" />
          <line x1={geometry.positions[index].x} x2={geometry.positions[index].x} y1="20" y2="220" stroke="currentColor" opacity=".22" />
          <circle cx={geometry.positions[index].x} cy={geometry.positions[index].y} r="9" fill="currentColor" opacity=".15" /><circle cx={geometry.positions[index].x} cy={geometry.positions[index].y} r="4.5" fill="currentColor" />
          <text x="56" y="247">{new Date(points[0].createdAt).toLocaleDateString('zh-CN')}</text><text x="580" y="247" textAnchor="end">{new Date(points.at(-1)!.createdAt).toLocaleDateString('zh-CN')}</text>
        </svg>
        <div className="trend-selected"><p className="muted small">{date(selected.createdAt)}</p><div><strong>{fmt(selected.rks)}</strong><span className={selected.rksJump < -.00005 ? 'trend-negative' : 'accent'}>{change(selected.rksJump)}</span></div><footer><small className="muted">较上次记录</small><button className="icon-button" aria-label="上一条记录" disabled={index <= 0} onClick={() => setChosen(index - 1)}><ChevronLeft /></button><button className="icon-button" aria-label="下一条记录" disabled={index >= points.length - 1} onClick={() => setChosen(index + 1)}><ChevronRight /></button></footer></div>
      </section><div className="section-heading"><h2>变化明细</h2><span className="muted small">{points.length} 条记录</span></div><div className="trend-details">{[...points].reverse().map((p, i) => <button key={p.createdAt + ':' + i} className={'card trend-record' + (points.length - 1 - i === index ? ' selected' : '')} onClick={() => setChosen(points.length - 1 - i)}><span><strong>{fmt(p.rks)}</strong><small className="muted">{date(p.createdAt)}</small></span><span className={p.rksJump < -.00005 ? 'trend-negative' : 'accent'}>{change(p.rksJump)}</span></button>)}</div>
    </> : !loading && !error && <Empty title="这个区间还没有记录" detail="同步成绩后，RKS 记录将在这里呈现。" />}
    <p className="muted tiny">时间为成绩同步记录时间；未同步期间的实际达成时间无法追溯。</p>
  </section>;
}
