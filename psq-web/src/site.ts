const legacyAppPages = new Set(['home', 'scores', 'song', 'constants', 'leaderboard', 'image', 'more', 'settings', 'calculator', 'custom', 'achievement', 'checkin', 'guess', 'suggestions', 'feedback', 'moderation']);

export function isAppEntry(pathname: string, hash: string, standalone: boolean) {
  return standalone || /^\/app(?:\/|$)/.test(pathname) || (pathname === '/' && legacyAppPages.has(hash.replace(/^#/, '').split(/[/?]/)[0]));
}

export function isWebsitePreview(pathname: string, previewPath = '/preview/') {
  return pathname === previewPath || pathname + '/' === previewPath;
}

export interface AndroidRelease {
  versionName: string;
  apkUrl: string;
  publishedAt: string;
  sizeBytes: number | null;
  changelog: string[];
}

export function androidRelease(value: unknown): AndroidRelease | null {
  if (!value || typeof value !== 'object') return null;
  const data = value as Record<string, unknown>;
  if (typeof data.versionName !== 'string' || !data.versionName.trim() || typeof data.apkUrl !== 'string') return null;
  try {
    const url = new URL(data.apkUrl);
    if (url.protocol !== 'https:' || url.username || url.password || !url.pathname.toLowerCase().endsWith('.apk')) return null;
    return {
      versionName: data.versionName,
      apkUrl: url.href,
      publishedAt: typeof data.publishedAt === 'string' ? data.publishedAt : '',
      sizeBytes: typeof data.sizeBytes === 'number' && Number.isFinite(data.sizeBytes) && data.sizeBytes > 0 ? data.sizeBytes : null,
      changelog: Array.isArray(data.changelog) ? data.changelog.filter((line): line is string => typeof line === 'string') : [],
    };
  } catch { return null; }
}

export interface SiteAnnouncement { id: string; title: string; body: string; publishedAt: string }
export function siteAnnouncements(value: unknown): SiteAnnouncement[] {
  if (!value || typeof value !== 'object' || !Array.isArray((value as { items?: unknown }).items)) return [];
  return (value as { items: unknown[] }).items.filter((item): item is SiteAnnouncement => {
    if (!item || typeof item !== 'object') return false;
    const data = item as Record<string, unknown>;
    return ['id', 'title', 'body', 'publishedAt'].every(key => typeof data[key] === 'string');
  }).sort((a, b) => b.publishedAt.localeCompare(a.publishedAt));
}
