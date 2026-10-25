import { useEffect, useRef, type CSSProperties } from 'react';
import { ArrowUpRight, Check, CheckCheck, ChartNoAxesCombined, Music2, Sparkles, Trophy } from 'lucide-react';

export function CountUp({ value, digits = 4 }: { value: number; digits?: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const element = ref.current!;
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const compact = matchMedia('(max-width: 800px), (pointer: coarse)');
    let frame = 0, start: number | undefined, lastPaint = -Infinity;
    const final = value.toFixed(digits);
    const finish = () => { cancelAnimationFrame(frame); element.textContent = final; };
    const observer = new IntersectionObserver(entries => {
      if (!entries[0].isIntersecting) return;
      observer.disconnect();
      if (reduced.matches) { finish(); return; }
      const duration = compact.matches ? 850 : 1450;
      const step = (time: number) => {
        start ??= time;
        const progress = Math.min(1, (time - start) / duration);
        if (progress === 1 || time - lastPaint >= 1000 / (compact.matches ? 24 : 60)) {
          element.textContent = (value * (1 - (1 - progress) ** 4)).toFixed(digits);
          lastPaint = time;
        }
        if (progress < 1 && !reduced.matches) frame = requestAnimationFrame(step);
        else finish();
      };
      frame = requestAnimationFrame(step);
    }, { threshold: .3 });
    observer.observe(element);
    reduced.addEventListener('change', finish);
    return () => { observer.disconnect(); cancelAnimationFrame(frame); reduced.removeEventListener('change', finish); };
  }, [value, digits]);
  return <><span ref={ref} aria-hidden="true">{value.toFixed(digits)}</span><span className="site-sr-only">{value.toFixed(digits)}</span></>;
}

export function HeroAtmosphere() {
  return <div className="site-hero-atmosphere" aria-hidden="true"><div className="site-hero-wordmark">PSQ</div><div className="site-ambient-cloud cloud-one" /><div className="site-ambient-cloud cloud-two" /><div className="site-judgement-grid" /><div className="site-orbit orbit-one" /><div className="site-orbit orbit-two" />{Array.from({ length: 7 }, (_, index) => <i className="site-rhythm-note" key={index} style={{ '--note-index': index } as CSSProperties} />)}<div className="site-judgement-line"><i /></div></div>;
}

export function FeatureVisual({ kind }: { kind: string }) {
  if (kind === 'scores') return <div className="site-feature-visual visual-scores" aria-hidden="true"><div className="visual-stat"><span>RANKING SCORE</span><strong>16.0000<ArrowUpRight size={20} /></strong><div className="visual-bars">{[25, 38, 33, 49, 59, 54, 70, 79, 74, 95].map((height, index) => <i style={{ '--bar-height': height + '%', '--bar-index': index } as CSSProperties} key={index} />)}</div><div className="visual-stat-foot"><span>B30</span><span>P30</span><span>Best N</span></div></div><span className="visual-floating-icon"><ChartNoAxesCombined size={24} /></span></div>;
  if (kind === 'constants') return <div className="site-feature-visual visual-constants" aria-hidden="true"><div className="visual-levels">{[18, 17, 16, 15, 14, 13].map((level, index) => <span className={index === 0 ? 'selected' : ''} key={level}>{level}</span>)}</div><div className="visual-song-tile"><span className="visual-song-symbol"><Music2 size={25} /></span><div><strong>寻找下一次突破</strong><span>EZ · HD · IN · AT</span></div><ArrowUpRight size={16} /></div><div className="visual-constant-orbit"><strong>18</strong><span>定数表</span></div></div>;
  if (kind === 'images') return <div className="site-feature-visual visual-images" aria-hidden="true"><div className="visual-report report-back"><span>PHI SCORE QUERY</span><strong>P30</strong><div className="visual-report-lines" /></div><div className="visual-report report-front"><span>PHI SCORE QUERY</span><strong>B30</strong><div className="visual-report-art"><img src="/api/assets?path=%2F_ill%2FillLow%2FStasis.Maozon.png" alt="" loading="lazy" decoding="async" /></div><div className="visual-report-lines" /></div><span className="visual-save-stamp"><Check size={15} />属于你的高光</span></div>;
  return <div className="site-feature-visual visual-community" aria-hidden="true"><div className="visual-checkin"><span><CheckCheck size={19} />每日签到</span><strong>Coin <i>+25</i></strong><div>{[1, 2, 3, 4, 5, 6, 7].map((day, index) => <span className={index < 5 ? 'done' : ''} key={day}>{index < 5 ? <Check size={10} /> : day}</span>)}</div></div><div className="visual-guess"><span><Sparkles size={16} />RKS 猜猜乐</span><strong>?.????</strong><span className="visual-players"><i>Φ</i><b>VS</b><i><Trophy size={14} /></i></span></div></div>;
}
