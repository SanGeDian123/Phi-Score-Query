import { useCallback, useEffect, useState } from 'react';
import access from '../access.config.json';
import { areNewWebLoginsEnabled, isWebsiteOpen, type AccessPolicy } from '../server/access-policy.mjs';

export function useLaunchState(synchronize = true) {
  const [clock, setClock] = useState({ policy: access as AccessPolicy, offset: 0, loggedIn: null as boolean | null, resolved: false, unavailable: false });
  const [now, setNow] = useState(Date.now);
  const syncStatus = useCallback((status: any) => {
    setClock(previous => ({
      policy: typeof status.newWebLogins === 'boolean' ? {
        ...previous.policy, newWebLogins: status.newWebLogins,
        openAfterCountdown: status.openAfterCountdown === true,
        countdownTarget: typeof status.countdownTarget === 'string' ? status.countdownTarget : previous.policy.countdownTarget,
      } : previous.policy,
      offset: Number.isFinite(status.serverTime) ? status.serverTime - Date.now() : previous.offset,
      loggedIn: typeof status.loggedIn === 'boolean' ? status.loggedIn : previous.loggedIn,
      resolved: true, unavailable: false,
    }));
    setNow(Date.now());
  }, []);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const check = () => {
      clearTimeout(timer);
      const current = Date.now() + clock.offset;
      setNow(Date.now());
      const remaining = Date.parse(clock.policy.countdownTarget) - current;
      if (!document.hidden && clock.policy.openAfterCountdown && remaining > 0 && Number.isFinite(remaining)) {
        timer = setTimeout(check, Math.min(remaining, 2147483647));
      }
    };
    check(); document.addEventListener('visibilitychange', check);
    return () => { clearTimeout(timer); document.removeEventListener('visibilitychange', check); };
  }, [clock.policy, clock.offset]);
  useEffect(() => {
    if (!synchronize) return;
    let active = true;
    const sync = async () => {
      if (document.hidden) return;
      try {
        const reply = await fetch('/api/auth/status', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(8000) });
        if (!reply.ok) throw new Error('Unavailable');
        const status = await reply.json();
        if (active) syncStatus(status);
      } catch { if (active) setClock(previous => ({ ...previous, resolved: true, unavailable: true })); }
    };
    sync(); document.addEventListener('visibilitychange', sync); window.addEventListener('online', sync);
    return () => { active = false; document.removeEventListener('visibilitychange', sync); window.removeEventListener('online', sync); };
  }, [synchronize, syncStatus]);
  return {
    websiteOpen: (!synchronize || clock.resolved) && isWebsiteOpen(clock.policy, now + clock.offset),
    loginEnabled: (!synchronize || clock.resolved) && areNewWebLoginsEnabled(clock.policy, now + clock.offset),
    targetTime: Date.parse(clock.policy.countdownTarget), clockOffset: clock.offset,
    loggedIn: clock.loggedIn, resolved: clock.resolved, unavailable: clock.unavailable, syncStatus,
  };
}
