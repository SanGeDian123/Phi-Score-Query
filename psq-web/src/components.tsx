import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { ArrowLeft, Download, LoaderCircle, RefreshCw, Share2, UserRound, X, Image as ImageIcon } from 'lucide-react';
import { artwork, avatarUrl } from './api';
import { fmt, grade, scoreText, type Score } from './core';
import { usePSQ } from './state';
import { motionEase, reducedMotion, useAnimatedClose } from './motion';
export function PageHeader({ title, subtitle, onBack, refresh, loading, children }: { title: string; subtitle?: ReactNode; onBack?: () => void; refresh?: () => void; loading?: boolean; children?: ReactNode }) {
  return <header className={'page-header' + (onBack ? ' subpage-header' : '')}><div className="header-title">{onBack && <button className="icon-button" aria-label="返回" onClick={onBack}><ArrowLeft /></button>}<div><h1>{title}</h1>{subtitle && <div className="muted small">{subtitle}</div>}</div></div><div className="header-actions">{children}{refresh && <button className="icon-button" aria-label="刷新" onClick={refresh} disabled={loading}><RefreshCw className={loading ? 'spin' : ''} size={21} /></button>}</div></header>;
}
export function Modal({ title, children, onClose, wide = false, className = '', dismissDisabled = false }: { title: string; children: ReactNode | ((close: () => void) => ReactNode); onClose: () => void; wide?: boolean; className?: string; dismissDisabled?: boolean }) {
  const ref = useRef<HTMLElement>(null), backdrop = useRef<HTMLDivElement>(null);
  const close = useAnimatedClose(backdrop, onClose, '.modal', dismissDisabled);
  useEffect(() => {
    const focus = document.activeElement as HTMLElement;
    const previous = document.body.style.overflow; document.body.style.overflow = 'hidden';
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close();
      if (event.key === 'Tab') {
        const elements = ref.current?.querySelectorAll<HTMLElement>('button:not(:disabled),input,select,textarea,a[href],[tabindex="0"]');
        if (!elements?.length) return;
        const first = elements[0], last = elements[elements.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', key); ref.current?.querySelector<HTMLElement>('input,button')?.focus();
    return () => { document.body.style.overflow = previous; document.removeEventListener('keydown', key); focus?.focus(); };
  }, [close]);
  return <div ref={backdrop} className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) close(); }}><section ref={ref} className={'modal' + (wide ? ' modal-wide' : '') + ' ' + className} role="dialog" aria-modal="true" aria-label={title}><div className="modal-heading"><h2>{title}</h2><button className="icon-button" aria-label="关闭" onClick={close}><X /></button></div>{typeof children === 'function' ? children(close) : children}</section></div>;
}
export function Confirm({ title, body, onConfirm, onClose, danger = false }: { title: string; body: string; onConfirm: () => void; onClose: () => void; danger?: boolean }) {
  return <Modal title={title} onClose={onClose}>{close => <><p className="muted">{body}</p><div className="modal-actions"><button className="text-button" onClick={close}>取消</button><button className={'button ' + (danger ? 'danger' : 'primary')} onClick={() => { close(); onConfirm(); }}>确认</button></div></>}</Modal>;
}
export function Collapse({ open, children }: { open: boolean; children: ReactNode }) {
  return <div className="collapse" data-open={open} aria-hidden={!open} inert={!open}><div>{children}</div></div>;
}
export function Progress({ value, max, label, className = '' }: { value: number; max: number; label: string; className?: string }) {
  const fraction = Math.max(0, Math.min(1, value / Math.max(1, max)));
  return <div className={'progress ' + className} role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={max} aria-valuenow={value}><span className="progress-fill" style={{ transform: 'scaleX(' + fraction + ')' }} /></div>;
}
export function Empty({ title = '暂无数据', detail, action, label = '刷新' }: { title?: string; detail?: string; action?: () => void; label?: string }) {
  return <div className="empty"><ImageIcon size={32} strokeWidth={1.3} /><h2>{title}</h2>{detail && <p className="muted small">{detail}</p>}{action && <button className="text-button" onClick={action}>{label}</button>}</div>;
}
export function Loading({ text = '正在加载…' }: { text?: string }) { return <div className="loading"><LoaderCircle className="spin" size={23} /><span>{text}</span></div>; }
export function Art({ songId, full = false, className = '', onClick }: { songId: string; full?: boolean; className?: string; onClick?: () => void }) {
  const { catalogVersion } = usePSQ();
  const [failed, setFailed] = useState(false), [fallback, setFallback] = useState(false);
  const src = artwork(songId, full || fallback) + '&revision=' + encodeURIComponent(catalogVersion);
  useEffect(() => { setFailed(false); setFallback(false); }, [songId, full, catalogVersion]);
  return failed ? <div className={'art-placeholder ' + className} onClick={onClick}><ImageIcon size={23} /></div> : <img className={'art ' + className} alt="" src={src} loading="lazy" decoding="async" onError={() => { if (!full && !fallback) setFallback(true); else setFailed(true); }} onClick={onClick} />;
}
export function Avatar({ avatar, name = 'Phigros Player', className = '' }: { avatar?: unknown; name?: string; className?: string }) {
  const src = avatarUrl(avatar), [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  return <div className={'avatar ' + className} role={!src || failed ? 'img' : undefined} aria-label={!src || failed ? name + ' 的头像' : undefined}><UserRound size={21} aria-hidden="true" />{src && !failed && <img alt={name + ' 的头像'} src={src} loading="lazy" decoding="async" onError={() => setFailed(true)} />}</div>;
}
export function ScoreRow({ score, onClick, compact = false }: { score: Score; onClick?: () => void; compact?: boolean }) {
  return <button className={'score-row ' + (compact ? 'compact ' : '')} onClick={onClick} type="button">
    <span className="score-position">{score.position || ''}</span>
    <div className="score-info"><h3>{score.songName}</h3><p className="score-meta muted">{score.difficulty} {fmt(score.chartConstant, 1)} · {scoreText(score.score)}</p><p className="score-numbers">ACC {fmt(score.accuracy, 4)}%{['AP', 'FC'].includes(grade(score.score, score.isFullCombo)) ? ' · ' + grade(score.score, score.isFullCombo) : ''}</p>{score.pushAcc != null && <div className={'push-acc difficulty-text ' + score.difficulty}>推分 ACC {fmt(score.pushAcc, 4)}%</div>}</div>
    <div className="score-right"><strong>{fmt(score.rks)}</strong><span className="muted tiny">RKS</span></div>
  </button>;
}
export function Tabs({ values, value, onChange }: { values: string[]; value: string; onChange: (value: string) => void }) {
  const ref = useRef<HTMLDivElement>(null), indicator = useRef<HTMLSpanElement>(null);
  const choices = values.join('\u0000');
  useLayoutEffect(() => {
    const root = ref.current, marker = indicator.current;
    if (!root || !marker) return;
    let frame = 0;
    const measure = () => {
      const selected = root.querySelector<HTMLElement>('[aria-selected="true"]');
      if (!selected) return;
      marker.style.width = selected.offsetWidth + 'px'; marker.style.height = selected.offsetHeight + 'px';
      marker.style.transform = 'translate3d(' + selected.offsetLeft + 'px,' + selected.offsetTop + 'px,0)';
    };
    measure();
    const observer = new ResizeObserver(() => { cancelAnimationFrame(frame); frame = requestAnimationFrame(measure); });
    observer.observe(root);
    return () => { observer.disconnect(); cancelAnimationFrame(frame); };
  }, [value, choices]);
  return <div ref={ref} className="tabs" role="tablist"><span ref={indicator} className="tabs-indicator" aria-hidden="true" />{values.map(v => <button key={v} role="tab" aria-selected={value === v} className={value === v ? 'selected' : ''} onClick={() => onChange(v)}>{v}</button>)}</div>;
}
export function DragScroll({ label, children }: { label: string; children: ReactNode }) {
  const drag = useRef<{ x: number; scroll: number; moved: boolean } | null>(null), ignoreClick = useRef(false);
  return <div className="chip-scroll drag-scroll" aria-label={label} onPointerDown={event => {
    event.stopPropagation(); ignoreClick.current = false;
    if (event.pointerType === 'mouse' && event.button === 0) drag.current = { x: event.clientX, scroll: event.currentTarget.scrollLeft, moved: false };
  }} onPointerMove={event => {
    if (!drag.current) return;
    const delta = event.clientX - drag.current.x;
    if (Math.abs(delta) > 5) { drag.current.moved = true; event.currentTarget.setPointerCapture(event.pointerId); }
    if (drag.current.moved) { event.currentTarget.scrollLeft = drag.current.scroll - delta; event.preventDefault(); }
  }} onPointerUp={event => {
    if (drag.current?.moved) ignoreClick.current = true;
    drag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  }} onPointerCancel={() => { drag.current = null; }} onClickCapture={event => {
    if (ignoreClick.current) { ignoreClick.current = false; event.preventDefault(); event.stopPropagation(); }
  }}>{children}</div>;
}
export function NumberField({ label, value, onChange, placeholder, min, max, integer }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string; min?: number; max?: number; integer?: boolean }) {
  return <label className="field"><span>{label}</span><input inputMode={integer ? 'numeric' : 'decimal'} type="number" step={integer ? 1 : 'any'} min={min} max={max} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} /></label>;
}
export function Switch({ label, detail, checked, onChange }: { label: string; detail?: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label className="switch-row"><span><strong>{label}</strong>{detail && <small>{detail}</small>}</span><input type="checkbox" role="switch" aria-label={label} checked={checked} onChange={e => onChange(e.target.checked)} /><span className="switch-track" aria-hidden="true" /></label>;
}
export function useBlobUrl(blob?: Blob | null) {
  const [current, setCurrent] = useState<{ blob: Blob; url: string } | null>(null);
  useEffect(() => { if (!blob) { setCurrent(null); return; } const url = URL.createObjectURL(blob); setCurrent({ blob, url }); return () => URL.revokeObjectURL(url); }, [blob]);
  return current?.blob === blob ? current?.url || '' : '';
}
export async function saveBlob(blob: Blob, name: string, share = false) {
  const file = new File([blob], name, { type: blob.type || 'image/png' });
  if (share && navigator.canShare?.({ files: [file] })) return navigator.share({ files: [file] });
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 30000);
}
export function ImageViewer({ blob, url, title = '成绩图', onClose }: { blob?: Blob; url?: string; title?: string; onClose: () => void }) {
  const local = useBlobUrl(blob); const { notify } = usePSQ();
  const source = local || url, [ready, setReady] = useState(''), [failed, setFailed] = useState('');
  const root = useRef<HTMLDivElement>(null), image = useRef<HTMLImageElement>(null), frame = useRef(0);
  const view = useRef({ scale: 1, x: 0, y: 0 }), smooth = useRef(false);
  const close = useAnimatedClose(root, onClose);
  const pointers = useRef(new Map<number, { x: number; y: number }>()), last = useRef<any>(null);
  const paint = (animated = false) => {
    smooth.current = animated;
    if (frame.current) return;
    frame.current = requestAnimationFrame(() => {
      frame.current = 0;
      if (!image.current) return;
      const current = view.current;
      image.current.style.transition = smooth.current && !reducedMotion() ? 'transform 180ms ' + motionEase + ', opacity 200ms ease-out' : 'opacity 200ms ease-out';
      image.current.style.transform = 'translate3d(' + current.x + 'px,' + current.y + 'px,0) scale(' + current.scale + ')';
    });
  };
  const zoom = (scale: number, animated = false) => { view.current.scale = Math.min(5, Math.max(1, scale)); if (view.current.scale === 1) { view.current.x = 0; view.current.y = 0; } paint(animated); };
  const update = () => {
    const points = [...pointers.current.values()];
    if (points.length >= 2) {
      const distance = Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y);
      if (last.current?.distance) zoom(view.current.scale * distance / last.current.distance);
      last.current = { distance };
    } else if (points.length === 1) {
      if (last.current?.point && view.current.scale > 1) { view.current.x += points[0].x - last.current.point.x; view.current.y += points[0].y - last.current.point.y; paint(); }
      last.current = { point: points[0] };
    }
  };
  const release = (id: number) => { pointers.current.delete(id); last.current = null; };
  useEffect(() => { const previous = document.body.style.overflow; document.body.style.overflow = 'hidden'; const escape = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); }; document.addEventListener('keydown', escape); return () => { cancelAnimationFrame(frame.current); document.body.style.overflow = previous; document.removeEventListener('keydown', escape); }; }, [close]);
  const download = async (share = false) => { try { const b = blob || (url ? await fetch(url).then(r => { if (!r.ok) throw new Error('图片不可用'); return r.blob(); }) : undefined); if (!b) throw new Error('图片不可用'); await saveBlob(b, 'Phi-Score-Query.png', share); } catch (e) { if ((e as Error).name !== 'AbortError') notify('保存失败，请重试'); } };
  return <div ref={root} className="image-viewer" role="dialog" aria-modal="true" aria-label={title}>
    <div className="viewer-toolbar"><button className="icon-button" aria-label="关闭图片" onClick={close}><X /></button><span>{title}</span><div><button className="icon-button" aria-label="保存图片" onClick={() => download()}><Download /></button><button className="icon-button" aria-label="分享图片" onClick={() => download(true)}><Share2 /></button></div></div>
    <div className="viewer-stage" onDoubleClick={() => { view.current.x = 0; view.current.y = 0; zoom(view.current.scale === 1 ? 2 : 1, true); }} onWheel={e => zoom(view.current.scale * (e.deltaY > 0 ? .9 : 1.1), true)} onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId); pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY }); last.current = null; update(); }} onPointerMove={e => { if (pointers.current.has(e.pointerId)) { pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY }); update(); } }} onPointerUp={e => release(e.pointerId)} onPointerCancel={e => release(e.pointerId)} onLostPointerCapture={e => release(e.pointerId)}>
      {ready !== source && <div className="viewer-image-status">{failed && failed === source ? <p>图片加载失败</p> : <Loading text="正在加载图片…" />}</div>}
      <img ref={image} key={source} alt={title} src={source} decoding="async" draggable={false} onLoad={() => { setReady(source || ''); paint(); }} onError={() => setFailed(source || '')} style={{ opacity: ready === source ? 1 : 0 }} />
    </div>
  </div>;
}
