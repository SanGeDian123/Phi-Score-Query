import React, { lazy, Suspense, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { isAppEntry, isWebsitePreview } from './site';
import access from '../access.config.json';
import CountdownPage from './CountdownPage';
import { localRead, readCache } from './storage';
import { useLaunchState } from './launch';
import { StartupBoundary, StartupReady } from './Startup';

const App = lazy(() => import('./App'));
const LandingPage = lazy(() => import('./LandingPage'));
const standalone = matchMedia('(display-mode: standalone)').matches || !!(navigator as Navigator & { standalone?: boolean }).standalone;
function WebsiteContent({ launch }: { launch: ReturnType<typeof useLaunchState> }) {
  return launch.websiteOpen ? <LandingPage /> : <CountdownPage targetTime={launch.targetTime} clockOffset={launch.clockOffset} />;
}
function WebsiteEntry() {
  const launch = useLaunchState();
  return <WebsiteContent launch={launch} />;
}
function PreviewEntry() {
  const launch = useLaunchState();
  useEffect(() => {
    if (launch.resolved && !launch.websiteOpen && !launch.loggedIn) location.replace('/');
  }, [launch.resolved, launch.websiteOpen, launch.loggedIn]);
  return launch.websiteOpen || launch.loggedIn ? <LandingPage /> : <CountdownPage targetTime={launch.targetTime} clockOffset={launch.clockOffset} />;
}
function LegacyEntry() {
  const launch = useLaunchState();
  useEffect(() => {
    if (!launch.resolved) return;
    if (launch.loggedIn || launch.loginEnabled) { location.replace('/app/' + location.hash); return; }
    if (!launch.unavailable) return;
    let active = true;
    (async () => {
      const previous = localRead<string | null>('last-account', null);
      if (previous && await readCache('snapshot:' + previous) && active) location.replace('/app/' + location.hash);
    })();
    return () => { active = false; };
  }, [launch.resolved, launch.loggedIn, launch.loginEnabled, launch.unavailable]);
  return <WebsiteContent launch={launch} />;
}
const Entry = /^\/app(?:\/|$)/.test(location.pathname) ? App : isWebsitePreview(location.pathname, access.websitePreviewPath) ? PreviewEntry : location.pathname === '/' && isAppEntry('/', location.hash, standalone) ? LegacyEntry : WebsiteEntry;
createRoot(document.getElementById('root')!).render(<React.StrictMode><StartupBoundary><Suspense fallback={null}><StartupReady><Entry /></StartupReady></Suspense></StartupBoundary></React.StrictMode>);
