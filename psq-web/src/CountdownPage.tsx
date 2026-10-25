import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import access from '../access.config.json';
import { countdownParts } from './countdown';
import { usePWA } from './pwa';
import './countdown.css';

const target = Date.parse(access.countdownTarget);
const units = ['DAYS', 'HOURS', 'MINS', 'SECS'];

function CountdownDigit({ digit }: { digit: string }) {
  const previous = useRef(digit), [leaving, setLeaving] = useState<string | null>(null);
  useLayoutEffect(() => {
    if (previous.current === digit) return;
    setLeaving(previous.current); previous.current = digit;
    const timer = setTimeout(() => setLeaving(null), 520);
    return () => clearTimeout(timer);
  }, [digit]);
  return <span className="countdown-digit">
    {leaving === null ? <span className="countdown-digit-static">{digit}</span> : <span className="countdown-digit-track" key={digit}>
      <span>{leaving}</span><span>{digit}</span>
    </span>}
  </span>;
}

function CountdownValue({ value }: { value: number }) {
  const digits = String(value).padStart(2, '0').split('');
  return <div className="countdown-number" aria-hidden="true">
    {digits.map((digit, index) => <CountdownDigit key={digits.length - index} digit={digit} />)}
  </div>;
}

export default function CountdownPage({ targetTime = target, clockOffset = 0 }: { targetTime?: number; clockOffset?: number } = {}) {
  const [values, setValues] = useState(() => countdownParts(targetTime, Date.now() + clockOffset)), [visible, setVisible] = useState(!document.hidden);
  const pwa = usePWA(true);
  useEffect(() => { document.title = 'Phi Score Query'; document.documentElement.removeAttribute('data-theme'); document.documentElement.style.colorScheme = 'light'; }, []);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const tick = () => { setValues(countdownParts(targetTime, Date.now() + clockOffset)); if (!document.hidden) timer = setTimeout(tick, 1000 - (Date.now() + clockOffset) % 1000); };
    const changed = () => { clearTimeout(timer); setVisible(!document.hidden); tick(); };
    tick(); document.addEventListener('visibilitychange', changed);
    return () => { clearTimeout(timer); document.removeEventListener('visibilitychange', changed); };
  }, [targetTime, clockOffset]);
  useEffect(() => { if (pwa.updateReady) pwa.applyUpdate(); }, [pwa.updateReady]);
  return <main className="psq-countdown" data-visible={visible}>
    <div className="countdown-atmosphere" aria-hidden="true"><div className="countdown-aurora" /><div className="countdown-bloom" /><div className="countdown-horizon" /></div>
    <div className="countdown-panel" role="timer" aria-live="off" aria-label={values.map((value, index) => value + units[index]).join(' ')}>
      {values.map((value, index) => <div className="countdown-unit" key={units[index]}><CountdownValue value={value} /><span className="countdown-label">{units[index]}</span></div>)}
    </div>
  </main>;
}
