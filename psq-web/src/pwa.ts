import { useEffect, useRef, useState } from 'react';
import release from '../release.config.json';
export const PRODUCT_VERSION = release.productVersion;
export const WEB_BUILD = __PSQ_BUILD_ID__;
declare const __PSQ_BUILD_ID__: string;
export function usePWA(autoUpdate: boolean) {
  const [registration, setRegistration] = useState<ServiceWorkerRegistration | null>(null), [updateReady, setUpdateReady] = useState(false), [installPrompt, setInstallPrompt] = useState<any>(null), [installed, setInstalled] = useState(matchMedia('(display-mode: standalone)').matches || !!(navigator as any).standalone), [checking, setChecking] = useState(false);
  const applying = useRef(false);
  useEffect(() => {
    if (!('serviceWorker' in navigator) || import.meta.env.DEV) return;
    let stopped = false;
    navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).then(reg => {
      if (stopped) return; setRegistration(reg); if (reg.waiting) setUpdateReady(true);
      reg.addEventListener('updatefound', () => { const worker = reg.installing; worker?.addEventListener('statechange', () => { if (worker.state === 'installed' && navigator.serviceWorker.controller) setUpdateReady(true); }); });
    }).catch(() => {});
    let hadController = !!navigator.serviceWorker.controller;
    const changed = () => { if (!hadController) { hadController = true; return; } if (applying.current) location.reload(); else setUpdateReady(true); };
    navigator.serviceWorker.addEventListener('controllerchange', changed);
    return () => { stopped = true; navigator.serviceWorker.removeEventListener('controllerchange', changed); };
  }, []);
  useEffect(() => {
    const prompt = (e: Event) => { e.preventDefault(); setInstallPrompt(e); }, complete = () => { setInstalled(true); setInstallPrompt(null); };
    window.addEventListener('beforeinstallprompt', prompt); window.addEventListener('appinstalled', complete);
    return () => { window.removeEventListener('beforeinstallprompt', prompt); window.removeEventListener('appinstalled', complete); };
  }, []);
  const checkUpdate = async () => {
    setChecking(true); try { if (registration) { await registration.update(); if (registration.waiting) setUpdateReady(true); } const info = await fetch('/api/version', { cache: 'no-store' }).then(r => { if (!r.ok) throw new Error('更新检查失败'); return r.json(); }); return info; }
    finally { setChecking(false); }
  };
  useEffect(() => { if (!autoUpdate || !registration) return; const check = () => { if (navigator.onLine && document.visibilityState === 'visible') registration.update().catch(() => {}); }; const timer = setInterval(check, 300000); document.addEventListener('visibilitychange', check); return () => { clearInterval(timer); document.removeEventListener('visibilitychange', check); }; }, [autoUpdate, registration]);
  const applyUpdate = () => { if (registration?.waiting) { applying.current = true; registration.waiting.postMessage({ type: 'ACTIVATE' }); } else location.reload(); };
  const install = async () => { if (!installPrompt) return false; await installPrompt.prompt(); const result = await installPrompt.userChoice; setInstallPrompt(null); return result.outcome === 'accepted'; };
  return { updateReady, applyUpdate, checkUpdate, checking, installed, installAvailable: !!installPrompt, install, development: import.meta.env.DEV };
}
