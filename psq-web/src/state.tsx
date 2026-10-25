import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, imageRequest, request } from './api';
import { makeSnapshot, type Snapshot, type Song } from './core';
import { clearAccountCache, localRead, localWrite, readCache, writeCache } from './storage';
import { PRODUCT_VERSION } from './pwa';
import { useLaunchState } from './launch';
import { rewindSnapshot, rewindSongs } from './release-features';
export interface Preferences { dark: boolean; autoRefresh: boolean; autoUpdate: boolean; imageStyle: string; songStyle: string; notifications: boolean; swipeNavigation: boolean; navigationHandle: boolean }
const defaults: Preferences = { dark: false, autoRefresh: true, autoUpdate: true, imageStyle: 'classic', songStyle: 'default', notifications: true, swipeNavigation: false, navigationHandle: true };
const Context = createContext<any>(null);
export const usePSQ = () => useContext(Context);
export function PSQProvider({ children }: { children: ReactNode }) {
  const { loginEnabled, syncStatus } = useLaunchState(false);
  const [account, setAccount] = useState<string | null>(null);
  const [checking, setChecking] = useState(true);
  const [songs, setSongs] = useState<Song[]>([]);
  const [catalogVersion, setCatalogVersion] = useState('');
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [offline, setOffline] = useState(!navigator.onLine);
  const [loading, setLoading] = useState(false);
  const [preferences, setPreferences] = useState<Preferences>(() => ({ ...defaults, ...localRead('preferences', {}) }));
  const [images, setImages] = useState<Record<string, Blob>>({});
  const [timeRewind, setRewind] = useState(false), [rewindImages, setRewindImages] = useState<Record<string, Blob>>({});
  const rewindRef = useRef(false), imageEpoch = useRef(0);
  const setTimeRewind = (enabled: boolean) => { imageEpoch.current++; rewindRef.current = enabled; setRewind(enabled); setGenerating(null); };
  const visibleSnapshot = useMemo(() => timeRewind ? rewindSnapshot(snapshot) : snapshot, [snapshot, timeRewind]);
  const visibleSongs = useMemo(() => timeRewind ? rewindSongs(songs) : songs, [songs, timeRewind]);
  const [generating, setGenerating] = useState<string | null>(null);
  const [toast, setToast] = useState('');
  const [checkin, setCheckin] = useState<any>(null);
  const [announcements, setAnnouncements] = useState<any[]>([]);
  const [moderation, setModeration] = useState<any>(null);
  const [notificationCount, setNotificationCount] = useState(0);
  const [feedbackCount, setFeedbackCount] = useState(0);
  const accountRef = useRef(account), songsRef = useRef(songs), snapshotRef = useRef(snapshot), catalogRef = useRef(catalogVersion);
  const refreshLock = useRef(false), catalogLock = useRef(false), metadata = useRef<Record<string, any>>({});
  accountRef.current = account; songsRef.current = songs; snapshotRef.current = snapshot; catalogRef.current = catalogVersion;
  const notify = useCallback((message: string) => setToast(message), []);
  useEffect(() => { if (toast) { const t = setTimeout(() => setToast(''), 4500); return () => clearTimeout(t); } }, [toast]);
  const setPreference = useCallback((key: keyof Preferences, value: any) => setPreferences(p => { const next = { ...p, [key]: value }; localWrite('preferences', next); return next; }), []);
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = preferences.dark ? 'dark' : 'light';
    document.documentElement.style.colorScheme = preferences.dark ? 'dark' : 'only light';
    document.querySelector('meta[name="color-scheme"]')?.setAttribute('content', preferences.dark ? 'dark' : 'light');
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', preferences.dark ? '#0b0c0f' : '#f3f4f6');
  }, [preferences.dark]);
  const refreshScores = useCallback(async (silent = false) => {
    if (!accountRef.current || refreshLock.current) return;
    refreshLock.current = true; setLoading(true);
    try {
      const owner = accountRef.current;
      const raw = await api('save?calculate_rks=true', { taptapVersion: 'cn' });
      if (!raw.save || !raw.rks) throw new Error('服务器成绩响应无效');
      if (!owner || owner !== accountRef.current) return;
      const next = makeSnapshot(raw, songsRef.current, snapshotRef.current);
      await writeCache('snapshot:' + owner, next);
      if (owner !== accountRef.current) return;
      snapshotRef.current = next; setSnapshot(next); setOffline(false);
      if (!silent) notify('成绩已同步');
    } catch (e) {
      if ((e as any).status === 403) await request('/api/moderation').then(setModeration).catch(() => {});
      setOffline(true); if (!silent) notify((e as Error).message);
    } finally { refreshLock.current = false; setLoading(false); }
  }, [notify]);
  const refreshCatalog = useCallback(async () => {
    if (catalogLock.current) return;
    catalogLock.current = true;
    try {
      const headers: Record<string, string> = {};
      if (/^[a-f0-9]{64}$/i.test(catalogRef.current)) headers['If-None-Match'] = '"' + catalogRef.current + '"';
      const response = await fetch('/api/catalog', { headers, cache: 'no-store' });
      if (response.status === 304) return;
      if (!response.ok) throw new Error('曲库同步失败');
      const data = await response.json();
      if (!data.items?.length || !data.version || new Set(data.items.map((s: Song) => s.id)).size !== data.items.length) throw new Error('曲库响应无效');
      const changed = !!catalogRef.current && catalogRef.current !== data.version;
      await writeCache('catalog', data);
      const resolved = data.items.map((s: Song) => ({ ...metadata.current[s.id], ...s }));
      songsRef.current = resolved; catalogRef.current = data.version;
      setSongs(resolved); setCatalogVersion(data.version);
      if (changed && accountRef.current) refreshScores(true);
    } catch {} finally { catalogLock.current = false; }
  }, [refreshScores]);
  const authenticated = useCallback(async (status?: any) => {
    const state = status || await request('/api/auth/status');
    syncStatus(state);
    if (!state.loggedIn) { imageEpoch.current++; rewindRef.current = false; setRewind(false); setRewindImages({}); setImages({}); setGenerating(null); accountRef.current = null; snapshotRef.current = null; setAccount(null); setSnapshot(null); return; }
    imageEpoch.current++; rewindRef.current = false; setRewind(false); setRewindImages({}); setGenerating(null);
    accountRef.current = state.accountKey; setAccount(state.accountKey); localWrite('last-account', state.accountKey);
    const saved = await readCache<Snapshot>('snapshot:' + state.accountKey);
    snapshotRef.current = saved; setSnapshot(saved); setImages({});
    for (const kind of ['B30', 'P30']) { const image = await readCache<Blob>('image-' + kind + ':' + state.accountKey); if (image) setImages(current => ({ ...current, [kind]: image })); }
    if (songsRef.current.length && (preferences.autoRefresh || !saved)) await refreshScores(true);
    request('/api/moderation').then(setModeration).catch(() => {});
    const oldCheckin = await readCache<any>('checkin:' + state.accountKey); if (oldCheckin) setCheckin(oldCheckin);
    api('checkin').then(setCheckin).catch(() => {});
  }, [preferences.autoRefresh, refreshScores, syncStatus]);
  useEffect(() => {
    let stopped = false;
    (async () => {
      metadata.current = await fetch('/data/song-metadata.json').then(r => r.json()).catch(() => ({}));
      const cached = await readCache<any>('catalog') || await fetch('/data/catalog.json').then(r => r.json()).catch(() => null);
      if (cached?.items?.length) { const resolved = cached.items.map((s: Song) => ({ ...metadata.current[s.id], ...s })); songsRef.current = resolved; catalogRef.current = cached.version || ''; if (!stopped) { setSongs(resolved); setCatalogVersion(cached.version || ''); } }
      await refreshCatalog();
      try { if (!stopped) await authenticated(); }
      catch {
        const previous = localRead<string | null>('last-account', null);
        if (previous) { const saved = await readCache<Snapshot>('snapshot:' + previous); if (saved && !stopped) { accountRef.current = previous; setAccount(previous); setSnapshot(saved); setOffline(true); } }
      } finally { if (!stopped) setChecking(false); }
    })();
    return () => { stopped = true; };
  }, []);
  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible' && navigator.onLine) refreshCatalog(); };
    const timer = setInterval(refresh, 15000); document.addEventListener('visibilitychange', refresh);
    const online = () => { setOffline(false); refresh(); if (accountRef.current) refreshScores(true); };
    const off = () => setOffline(true);
    window.addEventListener('online', online); window.addEventListener('offline', off);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', refresh); window.removeEventListener('online', online); window.removeEventListener('offline', off); };
  }, [refreshCatalog, refreshScores]);
  useEffect(() => {
    if (!account) return;
    let stopped = false;
    const refresh = async () => {
      if (document.visibilityState !== 'visible') return;
      const [ann, mod, suggestions, feedback] = await Promise.allSettled([
        request('/api/announcements'), request('/api/moderation'),
        preferences.notifications ? api('suggestions/notifications?after=' + encodeURIComponent(localRead('suggestions-read:' + account, new Date(0).toISOString()))) : Promise.resolve(null),
        api('feedback/notifications'),
      ]);
      if (stopped) return;
      if (ann.status === 'fulfilled') setAnnouncements((ann.value.items || []).filter((a: any) => a.id && a.title && a.body && a.body.length <= 8000));
      if (mod.status === 'fulfilled') setModeration(mod.value);
      if (suggestions.status === 'fulfilled') setNotificationCount(suggestions.value?.items?.length || 0);
      if (feedback.status === 'fulfilled') setFeedbackCount(feedback.value?.length || 0);
    };
    refresh(); const timer = setInterval(refresh, 45000);
    return () => { stopped = true; clearInterval(timer); };
  }, [account, preferences.notifications]);
  useEffect(() => {
    const expired = () => { notify('登录已失效，请重新登录'); imageEpoch.current++; rewindRef.current = false; setRewind(false); setRewindImages({}); setGenerating(null); snapshotRef.current = null; accountRef.current = null; setAccount(null); setSnapshot(null); setImages({}); };
    const restricted = () => { if (accountRef.current) request('/api/moderation').then(setModeration).catch(() => {}); };
    window.addEventListener('psq-auth-expired', expired); window.addEventListener('psq-restricted', restricted);
    return () => { window.removeEventListener('psq-auth-expired', expired); window.removeEventListener('psq-restricted', restricted); };
  }, [notify]);
  const generateImage = async (kind: string, n = 30) => {
    if (generating) return;
    if (rewindRef.current && kind !== 'B30') return;
    if (kind === 'Best N' && (!Number.isInteger(n) || n < 1 || n > 200)) { notify('谱面数量应为 1–200'); return; }
    const rewind = rewindRef.current, epoch = imageEpoch.current;
    setGenerating(kind);
    try {
      const style = preferences.imageStyle === 'classic' ? '' : '&template=' + preferences.imageStyle;
      const path = 'image/' + (kind === 'Best N' ? 'bestn' : kind === 'P30' ? 'p30' : 'bn') + '?format=png&width=1440' + style + (rewind ? '&time_rewind=true' : '');
      const blob = await imageRequest(path, { ...(kind === 'Best N' ? { n } : kind === 'B30' ? { n: rewind ? 19 : preferences.imageStyle === 'phi-plugin' ? 33 : 27 } : {}), theme: preferences.dark ? 'black' : 'white', embedImages: false, appVersion: PRODUCT_VERSION });
      if (account !== accountRef.current || epoch !== imageEpoch.current) return;
      if (rewind) setRewindImages(current => ({ ...current, [kind]: blob }));
      else { await writeCache('image-' + kind + ':' + account, blob); if (epoch !== imageEpoch.current || account !== accountRef.current) return; setImages(current => ({ ...current, [kind]: blob })); }
      notify((rewind ? 'B19' : kind) + ' 成绩图已生成');
    } catch (e) { if (epoch === imageEpoch.current) notify((e as Error).message); } finally { if (epoch === imageEpoch.current) setGenerating(null); }
  };
  const logout = async () => {
    try { await request('/api/auth/logout', {}); } catch { notify('暂时无法退出，请联网后重试'); return; }
    setTimeRewind(false); setRewindImages({}); snapshotRef.current = null;
    const previous = account; accountRef.current = null; setAccount(null); setSnapshot(null); setImages({}); setModeration(null); setCheckin(null);
    localWrite('last-account', null); if (previous) await clearAccountCache(previous);
    if (previous) for (const name of ['custom:', 'calculator:', 'feedback-draft:', 'guess-game:', 'suggestions-read:']) { try { localStorage.removeItem('psq:' + name + previous); } catch {} }
  };
  return <Context.Provider value={{ account, checking, loginEnabled, authenticated, songs: visibleSongs, catalogVersion, snapshot: visibleSnapshot, offline, loading, refreshScores, preferences, setPreference, images: timeRewind ? rewindImages : images, setImages, generating, generateImage, toast, notify, checkin, setCheckin, announcements, setAnnouncements, moderation, setModeration, notificationCount, setNotificationCount, feedbackCount, setFeedbackCount, logout, timeRewind, setTimeRewind }}>{children}</Context.Provider>;
}
