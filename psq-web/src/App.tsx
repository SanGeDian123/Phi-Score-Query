import { startTransition, useCallback, useEffect, useRef, useState } from 'react';
import { ChartNoAxesCombined, ChevronRight, Grid2X2, Home, Image, LoaderCircle, Menu, MoreHorizontal, Music2, Settings as SettingsIcon, Trophy, WifiOff, X } from 'lucide-react';
import CountdownPage from './CountdownPage';
import Login from './Login';
import { Avatar, Modal } from './components';
import { PSQProvider, usePSQ } from './state';
import { RksTrend } from './pages/RksTrend';
import { Overview, Scores, SongSearch, SongDetail, ConstantTable, Images, Leaderboard } from './pages/Scores';
import { Achievement, CalculatorPage, CustomRanking, More } from './pages/Tools';
import { Checkin } from './pages/Checkin';
import { Guess } from './pages/Guess';
import { Feedback, Suggestions } from './pages/Community';
import { AnnouncementBody, InstallDialog, Moderation, Settings } from './pages/Settings';
import { PRODUCT_VERSION, usePWA } from './pwa';
import { localRead, localWrite } from './storage';
import { useWebMCP } from './webmcp';
import { useIOSInstallGuide } from './IOSInstallGuide';
import './styles.css';
import './app-ui.css';
import './app-motion.css';
const nav = [
  { page: 'home', label: '概览', icon: Home }, { page: 'scores', label: 'B30', icon: ChartNoAxesCombined }, { page: 'song', label: '单曲', icon: Music2 }, { page: 'constants', label: '定数表', icon: Grid2X2 },
  { page: 'leaderboard', label: '排行榜', icon: Trophy }, { page: 'image', label: '图片', icon: Image }, { page: 'more', label: '更多', icon: MoreHorizontal }, { page: 'settings', label: '设置', icon: SettingsIcon },
];

function currentRoute() { return location.hash.slice(1) || 'home'; }
function decodeRoute(value: string) { try { return decodeURIComponent(value); } catch { return value; } }
function Shell() {
  useEffect(() => { document.title = 'Phi Score Query'; const manifest = document.createElement('link'); manifest.rel = 'manifest'; manifest.href = '/manifest.webmanifest'; document.head.append(manifest); return () => manifest.remove(); }, []);
  const { account, checking, loginEnabled, preferences, snapshot, offline, toast, announcements, moderation, notificationCount, feedbackCount, timeRewind } = usePSQ();
  useEffect(() => { if (!checking && !account && !loginEnabled) location.replace('/'); }, [checking, account, loginEnabled]);
  const [route, setRoute] = useState(currentRoute), [drawer, setDrawer] = useState(false), [install, setInstall] = useState(false), [announcement, setAnnouncement] = useState<any>(null), [update, setUpdate] = useState(false);
  const pwa = usePWA(preferences.autoUpdate), mainRef = useRef<HTMLElement>(null), pointer = useRef<any>(null);
  const iosGuide = useIOSInstallGuide(pwa.installed);
  const go = useCallback((page: string) => { location.hash = page; startTransition(() => setRoute(page)); setDrawer(false); window.scrollTo({ top: 0, behavior: 'instant' }); }, []);
  useEffect(() => { const changed = () => { startTransition(() => setRoute(currentRoute())); setDrawer(false); window.scrollTo({ top: 0, behavior: 'instant' }); }; window.addEventListener('hashchange', changed); return () => window.removeEventListener('hashchange', changed); }, []);
  useEffect(() => { if (!drawer) return; const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setDrawer(false); }; document.addEventListener('keydown', close); return () => document.removeEventListener('keydown', close); }, [drawer]);
  useWebMCP(go, account ? true : false);
  useEffect(() => { if (!iosGuide.open && account && announcements[0] && announcements[0].id !== localRead('announcement-seen', '')) setAnnouncement(announcements[0]); }, [account, announcements[0]?.id, iosGuide.open]);
  const closeAnnouncement = () => { localWrite('announcement-seen', announcement.id); setAnnouncement(null); };
  const page = route.split('/')[0].split('?')[0], id = route.includes('/') ? decodeRoute(route.slice(route.indexOf('/') + 1)) : '';
  const visibleNav = nav.filter(item => !timeRewind || ['home','scores','image','constants','settings'].includes(item.page)).map(item => item.page === 'scores' && timeRewind ? { ...item, label: 'B19' } : item);
  useEffect(() => { if (timeRewind && !['home','scores','image','constants','settings'].includes(page)) go('home'); }, [timeRewind, page, go]);
  const active = page === 'rks-trend' ? 'home' : ['calculator', 'custom', 'achievement', 'checkin', 'guess', 'suggestions'].includes(page) ? 'more' : ['feedback', 'moderation'].includes(page) ? 'settings' : page;
  const content = page === 'rks-trend' && !timeRewind ? <RksTrend go={go} /> : page === 'home' ? <Overview go={go} /> : page === 'scores' ? <Scores go={go} /> : page === 'song' ? id ? <SongDetail id={id} go={go} /> : <SongSearch go={go} /> : page === 'constants' ? <ConstantTable go={go} /> : page === 'leaderboard' ? <Leaderboard go={go} /> : page === 'image' ? <Images /> : page === 'more' ? <More go={go} /> : page === 'calculator' ? <CalculatorPage go={go} /> : page === 'custom' ? <CustomRanking go={go} /> : page === 'achievement' ? <Achievement go={go} /> : page === 'checkin' ? <Checkin go={go} /> : page === 'guess' ? <Guess go={go} /> : page === 'suggestions' ? <Suggestions go={go} postId={id || undefined} /> : page === 'feedback' ? <Feedback go={go} create={id === 'new'} /> : page === 'moderation' ? <Moderation go={go} /> : <Settings go={go} pwa={pwa} />;
  const primary = timeRewind ? ['home', 'scores', 'image', 'constants', 'settings'] : ['home', 'scores', 'song', 'more'];
  const swipeStart = (e: React.PointerEvent) => { if (!preferences.swipeNavigation || e.pointerType === 'mouse' || (e.target as Element).closest('input,textarea,button,select,.chip-scroll,.tabs,.image-viewer')) return; pointer.current = { x: e.clientX, y: e.clientY, at: Date.now() }; };
  const swipeEnd = (e: React.PointerEvent) => { const from = pointer.current; pointer.current = null; if (!from || Date.now() - from.at > 650 || Math.abs(e.clientY - from.y) > 45 || Math.abs(e.clientX - from.x) < 110) return; const index = primary.indexOf(page), next = index + (e.clientX < from.x ? 1 : -1); if (index >= 0 && next >= 0 && next < primary.length) go(primary[next]); };
  const navButton = (item: typeof nav[number], bottom = false) => <button key={item.page} className={'nav-item ' + (active === item.page ? 'active' : '')} aria-current={active === item.page ? 'page' : undefined} onClick={() => go(item.page)}><span className="nav-icon"><item.icon size={bottom ? 22 : 20} />{((item.page === 'more' && notificationCount > 0) || (item.page === 'settings' && feedbackCount > 0)) && <i className="unread-dot" />}</span><span>{item.label}</span></button>;
  return <>
    {checking ? <div className="launch"><div className="phi-mark">Φ</div><LoaderCircle className="spin" aria-label="正在打开" /></div> : !account ? loginEnabled ? <Login install={() => setInstall(true)} /> : <CountdownPage /> : moderation?.accountSuspended ? <Moderation go={go} /> : <div className="app-shell">
      <aside className={'sidebar ' + (drawer ? 'open' : '')} aria-hidden={!drawer} inert={!drawer}><div className="sidebar-brand"><div className="phi-mark">Φ</div><div><h2>Phi Score Query</h2><p>{PRODUCT_VERSION}</p></div><button className="icon-button drawer-close" aria-label="关闭菜单" onClick={() => setDrawer(false)}><X /></button></div><nav aria-label="主导航">{visibleNav.map(item => navButton(item))}</nav><div className="sidebar-account"><Avatar avatar={snapshot?.avatar} name={snapshot?.nickname} className="small-avatar" /><div><strong>{snapshot?.nickname || 'Phigros Player'}</strong><small>{offline ? '离线缓存' : 'TapTap · 大陆版'}</small></div></div></aside>
      <button className={'drawer-backdrop' + (drawer ? ' open' : '')} aria-label="关闭菜单背景" aria-hidden={!drawer} inert={!drawer} tabIndex={drawer ? 0 : -1} onClick={() => setDrawer(false)} />
      <main className="main-content" ref={mainRef} onPointerDown={swipeStart} onPointerUp={swipeEnd} onPointerCancel={() => { pointer.current = null; }}>
        {offline && <div className="connection-banner"><WifiOff size={15} />离线查看 · 联网后恢复同步</div>}{moderation?.publicHidden && <button className="restriction-banner" onClick={() => go('moderation')}>公开展示已限制 · 查看原因与申诉<ChevronRight size={16} /></button>}{pwa.updateReady && <div className="update-banner"><span>新版本已准备好</span><button className="text-button" onClick={() => setUpdate(true)}>更新</button></div>}
        <div className="route-content" key={page === 'song' && id ? page + id : page}>{content}</div>
      </main>
      <nav className="bottom-nav" aria-label="快捷导航">{visibleNav.filter(item => primary.includes(item.page)).map(item => navButton(item, true))}<button className={'nav-item ' + (!primary.includes(active) ? 'active' : '')} aria-label="菜单" aria-expanded={drawer} onClick={() => setDrawer(true)}><span className="nav-icon"><Menu size={20} />{(notificationCount > 0 || feedbackCount > 0) && <i className="unread-dot" />}</span><span>菜单</span></button></nav>{preferences.navigationHandle && <button className="navigation-handle" aria-label="展开全部导航" onClick={() => setDrawer(true)}><ChevronRight size={14} /></button>}
    </div>}
    {toast && <div className="toast" role="status">{toast}</div>}{(install || (account && iosGuide.open && !announcement && !update)) && <InstallDialog pwa={pwa} onClose={() => { setInstall(false); iosGuide.dismiss(); }} />}{announcement && account && <Modal title={announcement.title} wide onClose={closeAnnouncement}><AnnouncementBody item={announcement} /><div className="modal-actions"><button className="button primary" onClick={closeAnnouncement}>我知道了</button></div></Modal>}{update && <Modal title="更新 PSQ" onClose={() => setUpdate(false)}><p className="muted">新版本已准备好，更新后将重新打开页面。</p><div className="modal-actions"><button className="text-button" onClick={() => setUpdate(false)}>稍后</button><button className="button primary" onClick={pwa.applyUpdate}>立即更新</button></div></Modal>}
  </>;
}
export default function App() { return <PSQProvider><Shell /></PSQProvider>; }
