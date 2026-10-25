import { useEffect, useRef, useState } from 'react';
import { CalendarCheck, ChevronLeft, ChevronRight, Download, Share2, Trophy } from 'lucide-react';
import { api } from '../api';
import { Empty, ImageViewer, Loading, PageHeader, saveBlob, useBlobUrl } from '../components';
import { beijingDate } from '../core';
import { luckText, renderCheckin } from '../renderers';
import { usePSQ } from '../state';
import { readCache, writeCache } from '../storage';
import { useContentMotion } from '../motion';

export function Checkin({ go }: { go: (page: string) => void }) {
  const { account, checkin, setCheckin, notify } = usePSQ();
  const [month, setMonth] = useState(checkin?.month || beijingDate().slice(0, 7)), [busy, setBusy] = useState(false), [error, setError] = useState(''), [image, setImage] = useState<Blob | null>(null), [generating, setGenerating] = useState(false), [artError, setArtError] = useState(false), [viewer, setViewer] = useState(false), [showRanks, setShowRanks] = useState(false), [ranks, setRanks] = useState<any[]>([]), [ranksBusy, setRanksBusy] = useState(false), [retry, setRetry] = useState(0);
  const seq = useRef(0), signOnMonthChange = useRef(false), url = useBlobUrl(image), today = checkin?.today;
  const contentMotion = useContentMotion<HTMLElement>(month, '.calendar-grid');
  const load = async (sign = false, selected = month) => {
    const id = ++seq.current; setBusy(true); setError('');
    try {
      const data = await api('checkin?month=' + selected, sign ? {} : undefined);
      if (id === seq.current) { setCheckin(data); await writeCache('checkin:' + account, data); if (sign) notify('签到成功 · Coin + ' + data.today.coin); }
    } catch (e) { if (id === seq.current) setError((e as Error).message); } finally { if (id === seq.current) setBusy(false); }
  };
  useEffect(() => { const sign = signOnMonthChange.current; signOnMonthChange.current = false; load(sign); }, [month]);
  useEffect(() => { const timer = setInterval(() => { if (document.visibilityState === 'visible' && navigator.onLine) load(); }, 60000); return () => clearInterval(timer); }, [month]);
  useEffect(() => {
    let stopped = false; setImage(null); setArtError(false);
    if (!today) return;
    (async () => {
      setGenerating(true);
      try {
        const key = 'checkin-card:' + account, cached = retry === 0 ? await readCache<any>(key) : null;
        const result = cached?.date === today.date ? cached : await renderCheckin(today);
        if (!stopped) { setImage(result.blob); setArtError(!!result.artworkMissing); }
        if (!result.artworkMissing) await writeCache(key, { ...result, date: today.date });
      } catch (e) { if (!stopped) notify((e as Error).message); } finally { if (!stopped) setGenerating(false); }
    })();
    return () => { stopped = true; };
  }, [today?.date, retry, account]);
  const shift = (n: number) => { const [y, m] = month.split('-').map(Number), date = new Date(Date.UTC(y, m - 1 + n, 1)); setMonth(date.toISOString().slice(0, 7)); };
  const [year, monthNumber] = month.split('-').map(Number), offset = (new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay() + 6) % 7, days = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  const signed = new Set(checkin?.month === month ? checkin.dates : []);
  const showLeaderboard = async () => {
    setShowRanks(!showRanks); if (showRanks) return; setRanksBusy(true);
    try { const data = await api('checkin/leaderboard'); setRanks(data.items || []); } catch (e) { notify((e as Error).message); } finally { setRanksBusy(false); }
  };
  const save = async (share = false) => { if (image) try { await saveBlob(image, 'Phi-Checkin-' + today.date + '.png', share); } catch (e) { if ((e as Error).name !== 'AbortError') notify('保存失败，请重试'); } };
  const sign = () => {
    const current = (checkin?.serverDate || beijingDate()).slice(0, 7);
    if (month === current) load(true, current);
    else { signOnMonthChange.current = true; setMonth(current); }
  };
  return <section ref={contentMotion} className="page readable"><PageHeader title="每日签到" onBack={() => go('more')} refresh={() => load()} loading={busy} /><div className="card stack checkin-summary"><h2>{today ? '今日已签到' : '每天，留下一点幸运'}</h2><div className="checkin-totals"><div><strong>{checkin?.totalCoin?.toLocaleString('zh-CN') ?? '—'}</strong><span className="muted small">Coin</span></div><div><strong>{checkin?.totalDays ?? '—'} <small>天</small></strong><span className="muted small">累计签到</span></div></div><p className="muted small">每日获得 15–50 Coin · 北京时间 00:00 重置</p>{today && <><strong>Coin + {today.coin} · 今日幸运值 {today.luck}</strong><p className={today.luck >= 85 ? 'luck-high' : today.luck < 50 ? 'muted' : ''}>{luckText(today.luck)}</p></>}<button className="button primary full" disabled={busy || !!today} onClick={sign}><CalendarCheck size={19} />{busy ? '正在加载…' : today ? '已领取今日奖励' : '立即签到'}</button>{error && <p className="error-message" role="alert">{error}</p>}</div>
    {today && <section className="stack"><h2>今日签到卡</h2>{generating && <Loading text="正在生成签到卡…" />}{url && <><button className="image-preview-button landscape" onClick={() => setViewer(true)}><img src={url} alt="每日签到图片，点击放大" /></button><div className="button-row equal"><button className="button outline" onClick={() => save()}><Download size={18} />保存图片</button><button className="button outline" onClick={() => save(true)}><Share2 size={18} />分享</button></div></>}{artError && <p className="muted small">曲绘暂时不可用，可重试补全</p>}{(artError || (!image && !generating)) && <button className="text-button" onClick={() => setRetry(v => v + 1)}>重新生成图片</button>}</section>}
    <section className="card calendar"><div className="section-heading"><button className="icon-button" aria-label="上月" disabled={busy || month <= '2020-01'} onClick={() => shift(-1)}><ChevronLeft /></button><h2>{year} 年 {monthNumber} 月</h2><button className="icon-button" aria-label="下月" disabled={busy || month >= (checkin?.serverDate || beijingDate()).slice(0, 7)} onClick={() => shift(1)}><ChevronRight /></button></div><div className="calendar-grid">{['一', '二', '三', '四', '五', '六', '日'].map(d => <span className="calendar-weekday muted" key={d}>{d}</span>)}{Array.from({ length: Math.ceil((offset + days) / 7) * 7 }, (_, i) => { const n = i - offset + 1, date = month + '-' + String(n).padStart(2, '0'), valid = n > 0 && n <= days, checked = signed.has(date); return <div className={'calendar-day ' + (checked ? 'checked ' : '') + (date === checkin?.serverDate ? 'today' : '')} key={i}>{valid && <><strong>{n}</strong>{checked && <small>已签</small>}</>}</div>; })}</div><p className="muted small">本月已签到 {signed.size} 天</p></section>
    <button className="button outline" onClick={showLeaderboard}><Trophy size={18} />{showRanks ? '收起累计签到排行榜' : '累计签到排行榜'}</button>{showRanks && <section className="stack"><p className="muted small">累计天数前 100 名 · 同天数按达成日期排序</p>{ranksBusy ? <Loading /> : ranks.length ? <div className="rank-list">{ranks.map(r => <div className="rank-row" key={r.rank}><strong className="rank-number">{r.rank}</strong><span className="rank-name">{r.nickname}</span><strong>{r.totalDays} 天</strong></div>)}</div> : <Empty title="暂无排行数据" />}</section>}{viewer && image && <ImageViewer blob={image} title="每日签到" onClose={() => setViewer(false)} />}</section>;
}
