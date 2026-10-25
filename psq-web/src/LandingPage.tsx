import { PRODUCT_VERSION } from './pwa';
import { useEffect, useId, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { ArrowRight, ArrowUp, ArrowUpRight, ChartNoAxesCombined, Check, ChevronDown, Code2 as Github, Download, ExternalLink, Grid2X2, Image, Menu, Monitor, Music2, RefreshCw, Smartphone, Sparkles, Tablet, Trophy, X } from 'lucide-react';
import { usePWA } from './pwa';
import { androidRelease, siteAnnouncements, type AndroidRelease, type SiteAnnouncement } from './site';
import { useSiteMotion } from './site-motion';
import { CountUp, FeatureVisual, HeroAtmosphere } from './SiteVisuals';
import { IOSInstallSteps } from './IOSInstallGuide';
import { isIOSDevice } from './ios-install';
import './landing.css';
import './landing-experience.css';

const repository = 'https://github.com/SanGeDian123/Phi-Score-Query';
const appUrl = '/app/';
const previewSongs = [
  { name: "Rrhar'il", id: 'Rrharil.TeamGrimoire', level: 'AT', constant: '17.6' },
  { name: 'Stasis', id: 'Stasis.Maozon', level: 'AT', constant: '16.7' },
  { name: 'Chronostasis', id: 'Chronostasis.黒皇帝', level: 'IN', constant: '16.0' },
];
const features = [
  { kind: 'scores', icon: ChartNoAxesCombined, title: '成绩，一览无余', detail: 'B30、P30 与 Best N，配合 RKS 趋势图查看每一次变化。', tag: 'B30 / P30 / BEST N' },
  { kind: 'constants', icon: Grid2X2, title: '下一首，心中有数', detail: '搜索单曲、查阅定数，或用时间回溯查看旧定数体系下的 RKS 与 Best 19。', tag: '曲目查询 · 定数表' },
  { kind: 'images', icon: Image, title: '分享你的高光', detail: '经典、简约、Phi-Plugin 三种样式，Best N 图片支持至多 200 张谱面。', tag: '成绩图片 · BEST N · 自定义 BP30' },
  { kind: 'community', icon: Sparkles, title: '还有更多小乐趣', detail: '每日签到、RKS 猜猜乐、求建议与给建议，和同好一起玩。', tag: '签到 · 小游戏 · 互动' },
];

type DialogState = { type: 'install' } | { type: 'privacy' } | { type: 'release' } | { type: 'announcements' } | { type: 'announcement'; item: SiteAnnouncement } | null;
function SiteDialog({ title, children, close }: { title: string; children: ReactNode; close: () => void }) {
  const ref = useRef<HTMLDialogElement>(null), titleId = useId();
  const [leaving, setLeaving] = useState(false), closing = useRef(false), closeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const requestClose = () => { if (closing.current) return; closing.current = true; if (matchMedia('(prefers-reduced-motion: reduce)').matches) { close(); return; } setLeaving(true); closeTimer.current = setTimeout(close, 180); };
  useEffect(() => {
    const dialog = ref.current!, previous = document.body.style.overflow;
    dialog.showModal(); document.body.style.overflow = 'hidden';
    return () => { clearTimeout(closeTimer.current); dialog.close(); document.body.style.overflow = previous; };
  }, []);
  useEffect(() => { ref.current?.querySelector<HTMLButtonElement>('button')?.focus(); }, [title]);
  return <dialog ref={ref} className={'site-dialog' + (leaving ? ' is-leaving' : '')} aria-labelledby={titleId} onCancel={event => { event.preventDefault(); requestClose(); }} onClick={event => { if (event.target === event.currentTarget) requestClose(); }}><section className="site-dialog-content"><header><h2 id={titleId}>{title}</h2><button className="site-icon-button" aria-label="关闭弹窗" onClick={requestClose}><X size={22} /></button></header>{children}</section></dialog>;
}

function ProductPreview() {
  const [mode, setMode] = useState('B30');
  return <div className="site-preview-stage" aria-label="PSQ 成绩界面示意，使用示例成绩">
    <div className="site-preview-halo" aria-hidden="true" />
    <div className="site-float-tag site-float-top"><span className="site-float-icon"><Trophy size={19} /></span><div><strong>你的高光时刻</strong><span>值得好好收藏</span></div></div>
    <div className="site-preview-window">
      <div className="site-preview-toolbar"><span className="site-window-dots" aria-hidden="true"><i /><i /><i /></span><span>Phi Score Query</span><span className="site-demo-label">界面示意</span></div>
      <div className="site-preview-body">
        <div className="site-preview-player"><div className="site-preview-avatar">Φ</div><div><strong>Phigros Player</strong><span>课题模式 <b>金 13</b></span></div></div>
        <div className="site-preview-rks"><span>RANKING SCORE</span><strong><CountUp value={16} /></strong><svg viewBox="0 0 180 60" aria-hidden="true"><path className="site-rks-trend" pathLength="1" d="M2 56L20 50L39 53L57 37L76 42L93 28L113 30L135 16L155 21L177 4" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" /><circle cx="177" cy="4" r="4" fill="currentColor" /></svg></div>
        <div className="site-preview-tabs" role="group" aria-label="预览成绩列表"><button aria-pressed={mode === 'B30'} onClick={() => setMode('B30')}>B30</button><button aria-pressed={mode === 'P30'} onClick={() => setMode('P30')}>P30</button><span>示例成绩</span></div>
        <div className="site-preview-songs" key={mode}>{previewSongs.map((song, index) => <div className="site-preview-song" key={song.id} style={{ '--song-index': index } as CSSProperties}><span className="site-preview-rank">{index + 1}</span><div className={'site-preview-art art-' + index}><img src={'/api/assets?path=' + encodeURIComponent('/_ill/illLow/' + song.id + '.png')} alt="" width="60" height="44" decoding="async" onError={event => { event.currentTarget.style.visibility = 'hidden'; }} /></div><div className="site-preview-song-title"><strong>{song.name}</strong><span><b className={'site-difficulty ' + song.level}>{song.level} {song.constant}</b> RKS {song.constant}000</span></div><div className="site-preview-score"><strong>1000000</strong><span>100.00% · AP</span></div></div>)}</div>
        <div className="site-preview-nav" aria-hidden="true"><span><ChartNoAxesCombined size={17} />概览</span><span><Music2 size={17} />单曲</span><span><Grid2X2 size={17} />定数表</span><span><Sparkles size={17} />更多</span></div>
      </div>
    </div>
    <div className="site-float-tag site-float-bottom"><span className="site-float-icon mint"><Check size={19} /></span><div><strong>随时随地，轻松查询</strong><span>Android · iPhone · iPad · 电脑</span></div></div>
  </div>;
}

const dateLabel = (value: string) => value.slice(0, 10).replaceAll('-', '.');
export default function LandingPage() {
  const rootRef = useRef<HTMLDivElement>(null);
  useSiteMotion(rootRef);
  const [menu, setMenu] = useState(false), [dialog, setDialog] = useState<DialogState>(null), [release, setRelease] = useState<AndroidRelease | null>(null), [announcements, setAnnouncements] = useState<SiteAnnouncement[]>([]), [loading, setLoading] = useState(true), [newsFailed, setNewsFailed] = useState(false), [reload, setReload] = useState(0);
  const pwa = usePWA(true);
  useEffect(() => { document.title = 'Phi Score Query'; document.documentElement.removeAttribute('data-theme'); }, []);
  useEffect(() => {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 15000);
    let active = true; setLoading(true); setNewsFailed(false);
    const read = async (url: string) => { const response = await fetch(url, { signal: controller.signal, cache: 'no-store', credentials: 'omit' }); if (!response.ok) throw new Error(String(response.status)); return response.json(); };
    Promise.allSettled([read('/api/app-update'), read('/api/announcements')]).then(([update, news]) => {
      if (!active) return;
      setRelease(update.status === 'fulfilled' ? androidRelease(update.value) : null);
      setAnnouncements(news.status === 'fulfilled' ? siteAnnouncements(news.value) : []);
      setNewsFailed(news.status === 'rejected'); setLoading(false); clearTimeout(timer);
    });
    return () => { active = false; clearTimeout(timer); controller.abort(); };
  }, [reload]);
  useEffect(() => {
    if (!menu) return;
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenu(false); };
    document.addEventListener('keydown', escape); return () => document.removeEventListener('keydown', escape);
  }, [menu]);
  const close = () => setDialog(null);
  const downloadLink = release?.apkUrl || repository + '/releases';
  const downloadLabel = release ? '下载 Android 版' : loading ? '查看 Android 版本' : '查看 Android 发布页';
  const newsCards = announcements.slice(0, 3);
  return <div className="psq-site" ref={rootRef}>
    <div className="site-reading-progress" aria-hidden="true" />
    <a className="site-skip" href="#main">跳转到正文</a>
    <header className="site-header"><div className="site-container site-header-inner"><a className="site-brand" href="/" aria-label="PSQ 官网首页"><img src="/icons/icon-192.png" alt="" width="40" height="40" /><span><strong>PSQ</strong><small>Phi Score Query</small></span></a><button className="site-icon-button site-menu-toggle" aria-label={menu ? '收起导航' : '展开导航'} aria-expanded={menu} aria-controls="site-navigation" onClick={() => setMenu(!menu)}>{menu ? <X /> : <Menu />}</button><nav id="site-navigation" className={'site-navigation' + (menu ? ' is-open' : '')} aria-label="官网导航"><a href="#download" data-section-link="download" onClick={() => setMenu(false)}>下载</a><a href="#features" data-section-link="features" onClick={() => setMenu(false)}>功能</a><a href="#news" data-section-link="news" onClick={() => setMenu(false)}>公告</a><a href="#faq" data-section-link="faq" onClick={() => setMenu(false)}>常见问题</a><a className="site-github" href={repository} target="_blank" rel="noopener noreferrer" aria-label="在 GitHub 查看项目源码"><Github size={18} /></a><a className="site-button site-button-small site-button-blue" href={appUrl} data-magnetic>进入网页版<ArrowUpRight size={15} /></a></nav></div></header>
    {pwa.updateReady && <div className="site-update" role="status">官网与网页版有新版本可用<button onClick={pwa.applyUpdate}>立即更新<RefreshCw size={14} /></button></div>}
    <main id="main">
      <section className="site-hero site-container"><HeroAtmosphere /><div className="site-hero-copy"><h1><span>每一次进步，</span><br /><em>都清晰可见。</em></h1><p className="site-hero-description">查成绩、找定数、分享高光。<br />用 PSQ，轻松查看你的 Phigros 成绩。</p><div className="site-hero-actions"><a className="site-button site-button-blue" href={appUrl} data-magnetic>打开网页版<ArrowUpRight size={19} /></a><a className="site-button site-button-white" href="#download" data-magnetic><Download size={18} />下载 Android 版</a></div><div className="site-platforms"><span><Smartphone size={15} />Android / iOS</span><i /><span><Monitor size={15} />电脑</span><i /><span><Tablet size={15} />平板</span></div></div><ProductPreview /><a className="site-scroll-cue" href="#features" aria-label="探索 PSQ 功能"><span>向下探索</span><ChevronDown size={17} /></a><span className="site-hero-edition" aria-hidden="true">PHI SCORE QUERY / FOR PLAYERS</span></section>
      
      <section className="site-section site-container" id="download"><div className="site-section-heading" data-reveal><div><span className="site-section-kicker">选择你的打开方式</span><h2>开始使用PSQ</h2></div></div><div className="site-download-grid"><article className="site-download-card site-android" data-reveal data-spotlight><div className="site-download-card-top"><span className="site-download-icon"><Smartphone size={29} strokeWidth={1.5} /></span><span className="site-pill">原生应用</span></div><h3>Android</h3><p>完整的 PSQ 体验。</p><div className="site-download-bottom"><div className="site-release-info" aria-live="polite">{release ? <><strong>{release.versionName}</strong><span>{release.sizeBytes ? (release.sizeBytes / 1024 / 1024).toFixed(1) + ' MB · ' : ''}Android 8.0 及以上</span></> : <><strong>{loading ? '正在获取发布版本…' : '前往 GitHub 查看公开版本'}</strong><span>Android 8.0 及以上</span></>}</div><div className="site-download-actions"><a className="site-button site-button-blue" href={downloadLink} target="_blank" rel="noopener noreferrer"><Download size={18} />{downloadLabel}<ArrowUpRight size={16} /></a>{release && <button className="site-inline-link" onClick={() => setDialog({ type: 'release' })}>查看更新内容<ArrowRight size={14} /></button>}</div></div></article><article className="site-download-card site-web" data-reveal data-spotlight style={{ '--reveal-delay': '90ms' } as CSSProperties}><div className="site-download-card-top"><span className="site-download-icon"><Monitor size={29} strokeWidth={1.5} /></span><span className="site-pill">网页版 · PWA</span></div><h3>iPhone、iPad 与电脑</h3><p>打开浏览器即可使用。添加到主屏幕，<br className="site-desktop-break" />让查成绩像打开应用一样方便。</p><div className="site-download-bottom"><div className="site-release-info"><strong>{PRODUCT_VERSION} · 网页版</strong><span>iOS / iPadOS · Windows · macOS</span></div><div className="site-download-actions"><a className="site-button site-button-dark" href={appUrl}>打开 PSQ 网页版<ArrowUpRight size={18} /></a><button className="site-inline-link" onClick={() => setDialog({ type: 'install' })}>如何添加到主屏幕<ArrowRight size={14} /></button></div></div></article></div></section>
      <section className="site-section site-container" id="features"><div className="site-section-heading" data-reveal><div><h2>成绩之外，<em>还有更多。</em></h2></div></div><div className="site-feature-grid">{features.map((feature, index) => <article className={'site-feature-card feature-' + feature.kind} key={feature.title} data-reveal data-spotlight style={{ '--reveal-delay': (index % 2) * 90 + 'ms' } as CSSProperties}><div className="site-feature-copy"><span className="site-feature-icon"><feature.icon size={24} strokeWidth={1.6} /></span><h3>{feature.title}</h3><p>{feature.detail}</p><span className="site-feature-tag">{feature.tag}</span></div><FeatureVisual kind={feature.kind} /><span className="site-feature-sample">界面示意</span></article>)}</div></section>
      <section className="site-section site-container" id="news"><div className="site-section-heading" data-reveal><div><h2>PSQ 最新动态。</h2></div>{announcements.length > 3 && <button className="site-inline-link" onClick={() => setDialog({ type: 'announcements' })}>全部公告<ArrowRight size={16} /></button>}</div><div className="site-news-grid" aria-live="polite">{loading ? <div className="site-news-empty"><RefreshCw size={20} />正在加载公告…</div> : newsCards.length ? newsCards.map((item, index) => <button className="site-news-card" data-reveal style={{ '--reveal-delay': index * 65 + 'ms' } as CSSProperties} onClick={() => setDialog({ type: 'announcement', item })} key={item.id}><div className="site-news-meta"><time dateTime={item.publishedAt.slice(0, 10)}><b>{item.publishedAt.slice(8, 10)}</b><span>{dateLabel(item.publishedAt).slice(0, 7)}</span></time>{index === 0 && <span>最新</span>}</div><h3>{item.title}</h3><p>{item.body}</p><span className="site-news-read">阅读公告<ArrowUpRight size={16} /></span></button>) : <div className="site-news-empty"><p>{newsFailed ? '暂时无法加载公告，请稍后重试。' : '暂无公告。'}</p>{newsFailed && <button className="site-inline-link" onClick={() => setReload(value => value + 1)}><RefreshCw size={15} />重新加载</button>}</div>}</div></section>
      <section className="site-section site-container site-faq-section" id="faq"><div className="site-faq-heading" data-reveal><span className="site-section-kicker">你可能想知道</span><h2>从这里，<br />开始了解 PSQ。</h2><a className="site-inline-link" href={repository} target="_blank" rel="noopener noreferrer">了解开源项目<ExternalLink size={15} /></a></div><div className="site-faq-list" data-reveal><details><summary>PSQ 是什么？<ChevronDown size={19} /></summary><p>Phi Score Query（PSQ）是一个由玩家开发的 Phigros 成绩查询工具，提供成绩列表、定数查询、成绩图生成、RKS 计算与互动功能。本项目为非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。</p></details><details><summary>iPhone 上怎么使用？<ChevronDown size={19} /></summary><p>使用 Safari 打开 PSQ 网页版，通过 TapTap 扫码或 SessionToken 登录。你也可以从 Safari 的分享菜单选择“添加到主屏幕”，以后直接从桌面打开。</p><button className="site-inline-link" onClick={() => setDialog({ type: 'install' })}>查看详细步骤<ArrowRight size={14} /></button></details><details><summary>网页版和 Android 版有什么区别？<ChevronDown size={19} /></summary><p>两端共用 PSQ 后端，均支持成绩查询、成绩图、定数表、排行榜、签到和 RKS 猜猜乐。谱面播放、分段练习与谱面资源管理目前由 Android 版提供。</p></details><details><summary>登录时需要什么？<ChevronDown size={19} /></summary><p>支持大陆版 TapTap 扫码或输入 SessionToken。首次使用时，在登录页面选择合适的方式即可；网页与 Android 客户端分别保存各自的登录状态。</p></details><details><summary>离线时还能查看成绩吗？<ChevronDown size={19} /></summary><p>网页版首次联网使用并缓存后，可以离线查看已缓存的成绩。登录、获取最新成绩、签到、图片生成与互动功能需要网络连接。</p></details></div></section>
      <section className="site-container site-final-section"><div className="site-final-card" data-reveal><div><span className="site-section-kicker">你的下一次进步，就从这里开始</span><h2>立刻开始使用PSQ</h2></div><a className="site-button site-button-blue" href={appUrl}>立即体验<ArrowUpRight size={19} /></a></div></section>
    </main>
    <footer className="site-footer site-container"><div className="site-footer-top" data-reveal><a className="site-brand" href="/"><img src="/icons/icon-192.png" alt="" width="36" height="36" /><span><strong>PSQ</strong><small>Phi Score Query</small></span></a><div className="site-footer-links"><a href={repository} target="_blank" rel="noopener noreferrer">GitHub<ArrowUpRight size={13} /></a><button onClick={() => setDialog({ type: 'privacy' })}>隐私说明</button><a href="/licenses.txt" target="_blank" rel="noopener noreferrer">开源许可<ArrowUpRight size={13} /></a><a href={appUrl + '#feedback'}>反馈建议<ArrowUpRight size={13} /></a></div></div><div className="site-footer-bottom"><p>非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。<br />Phigros、TapTap 及相关游戏素材归各自权利人所有。</p></div></footer>
    <button className="site-back-top" aria-label="返回顶部" aria-hidden="true" tabIndex={-1} onClick={() => window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })}><ArrowUp size={18} /></button>
    {dialog && <SiteDialog close={close} title={dialog.type === 'install' ? '添加到主屏幕' : dialog.type === 'privacy' ? '隐私说明' : dialog.type === 'release' ? (release?.versionName || 'Android') + ' 更新内容' : dialog.type === 'announcements' ? '全部公告' : dialog.item.title}>
      {dialog.type === 'install' && <><IOSInstallSteps />{!isIOSDevice() && <div className="site-install-other"><strong>Android 与电脑</strong><p>使用浏览器菜单中的“安装应用”或“添加到主屏幕”；菜单名称可能因浏览器而有所不同。</p></div>}<a className="site-button site-button-blue" href={appUrl} onClick={close}>打开网页版<ArrowUpRight size={18} /></a><button className="site-inline-link" onClick={close}>我知道了</button></>}
      {dialog.type === 'privacy' && <div className="site-dialog-prose"><p>PSQ 在你主动登录后处理登录凭据、Phigros 存档、成绩、昵称、头像标识和课题模式信息，用于查询与展示你的成绩。</p><p>网页版通过同源服务连接 PSQ 后端；登录会话保存在加密的 HttpOnly Cookie 中，成绩、图片缓存及编辑草稿按账户保存在当前浏览器中。退出登录会清理该账户的本地缓存。</p><p>登录、存档查询、应用更新与源码访问会连接 TapTap、相关游戏服务、项目 API 及 GitHub。这些服务适用各自的隐私政策。</p><p>你可以在设置中清理缓存或退出登录，也可以通过浏览器设置删除站点数据。请妥善保管 SessionToken，不要将其提交到公开反馈或 Issue 中。</p><a className="site-inline-link" href={repository + '/blob/main/PRIVACY.md'} target="_blank" rel="noopener noreferrer">查看项目隐私说明<ExternalLink size={15} /></a></div>}
      {dialog.type === 'release' && <div className="site-dialog-prose">{release?.publishedAt && <p className="site-dialog-date">发布于 {dateLabel(release.publishedAt)}</p>}{release?.changelog.length ? <ul>{release.changelog.map((line, index) => <li key={index}>{line}</li>)}</ul> : <p>此版本暂无更新说明。</p>}<a className="site-button site-button-blue" href={downloadLink} target="_blank" rel="noopener noreferrer"><Download size={17} />下载 Android 版</a></div>}
      {dialog.type === 'announcements' && <div className="site-all-news">{announcements.map(item => <button key={item.id} onClick={() => setDialog({ type: 'announcement', item })}><span><small>{dateLabel(item.publishedAt)}</small><strong>{item.title}</strong></span><ArrowUpRight size={18} /></button>)}</div>}
      {dialog.type === 'announcement' && <article className="site-dialog-prose"><p className="site-dialog-date">{dateLabel(dialog.item.publishedAt)}</p><p className="site-announcement-body">{dialog.item.body}</p><button className="site-inline-link" onClick={() => setDialog({ type: 'announcements' })}>返回全部公告<ArrowRight size={14} /></button></article>}
    </SiteDialog>}
  </div>;
}
