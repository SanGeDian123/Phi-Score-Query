import { useEffect, useRef, useState } from 'react';
import { Bell, ChevronRight, ImagePlus, MessageSquare, Plus, RefreshCw, Send, Shuffle, Trash2, Upload, X } from 'lucide-react';
import { api, mediaUrl } from '../api';
import { Avatar, Confirm, Empty, ImageViewer, Loading, Modal, PageHeader, Switch, Tabs, useBlobUrl } from '../components';
import { challengeLabel, fmt } from '../core';
import { usePSQ } from '../state';
import { localRead, localWrite } from '../storage';
import { useContentMotion } from '../motion';

export const chineseDate = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false });
export function fileDataUrl(file: File): Promise<string> { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result).split(',')[1]); reader.onerror = () => reject(new Error('图片读取失败')); reader.readAsDataURL(file); }); }
function Attachment({ file, onRemove }: { file: File; onRemove: () => void }) {
  const url = useBlobUrl(file); return <div className="attachment"><img src={url} alt={file.name} /><button className="icon-button" aria-label={'移除 ' + file.name} onClick={onRemove} type="button"><X size={15} /></button></div>;
}
export function Attachments({ files, setFiles, max = 3, maxMB = 1 }: { files: File[]; setFiles: (files: File[]) => void; max?: number; maxMB?: number }) {
  const { notify } = usePSQ();
  const select = (input: HTMLInputElement) => {
    const selected = Array.from(input.files || []); input.value = '';
    if (selected.some(f => !['image/png', 'image/jpeg', 'image/webp'].includes(f.type) || f.size > maxMB * 1024 * 1024)) { notify('支持 JPG、PNG、WebP，每张不超过 ' + maxMB + ' MB'); return; }
    setFiles([...files, ...selected].slice(0, max));
  };
  return <div className="stack"><div className="section-heading"><strong className="small">图片</strong><span className="muted tiny">{files.length}/{max}</span></div><div className="attachments">{files.map((f, i) => <Attachment key={f.name + i} file={f} onRemove={() => setFiles(files.filter((_, j) => j !== i))} />)}{files.length < max && <label className="upload-button"><ImagePlus size={22} /><span>添加图片</span><input type="file" accept="image/png,image/jpeg,image/webp" multiple={max > 1} onChange={e => select(e.currentTarget)} /></label>}</div><p className="muted tiny">支持 JPG、PNG、WebP，每张不超过 {maxMB} MB。</p></div>;
}
function Author({ author }: { author: any }) {
  return <div className="post-author"><Avatar avatar={author.avatar} name={author.nickname} className="small-avatar" /><div><strong>{author.nickname}</strong><p>{!!author.challengeModeRank && <span className="accent">课题 {challengeLabel(author.challengeModeRank)}</span>}<span className="muted">{fmt(author.rks)} RKS</span></p></div></div>;
}
function SuggestionPostImage({ url, alt, onView }: { url: string; alt: string; onView: () => void }) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  return <button type="button" className="post-image suggestion-main-image" disabled={status === 'loading'} aria-busy={status === 'loading'} aria-label={status === 'error' ? '重新加载成绩图' : '查看成绩图'} onClick={() => {
    if (status === 'error') { setStatus('loading'); setAttempt(value => value + 1); } else onView();
  }}>
    <img key={attempt} src={url} alt={alt} decoding="async" style={status === 'ready' ? undefined : { display: 'none' }} onLoad={() => setStatus('ready')} onError={() => setStatus('error')} />
    {status === 'loading' && <Loading text="正在加载成绩图…" />}
    {status === 'error' && <span className="suggestion-image-error">成绩图加载失败，点击重试</span>}
  </button>;
}
export function Suggestions({ go, postId }: { go: (page: string) => void; postId?: string }) {
  const { account, notify, images, preferences, setPreference, notificationCount, setNotificationCount, moderation } = usePSQ();
  const [tab, setTab] = useState(postId ? '给建议' : '求建议'), [post, setPost] = useState<any>(null), [ownPosts, setOwnPosts] = useState<any[]>([]), [commented, setCommented] = useState<any[]>([]), [loading, setLoading] = useState(false), [busy, setBusy] = useState(false), [description, setDescription] = useState('求建议！'), [askLabel, setAskLabel] = useState(''), [text, setText] = useState(''), [commentLabel, setCommentLabel] = useState(''), [viewer, setViewer] = useState(''), [deletion, setDeletion] = useState<{ path: string; post: boolean } | null>(null), [error, setError] = useState(''), [guide, setGuide] = useState(() => !localRead('suggestion-swipe-guide-seen', false));
  const seq = useRef(0), currentPost = useRef<any>(null), swipeStart = useRef<{ x: number; y: number } | null>(null); currentPost.current = post;
  const pageMotion = useContentMotion<HTMLElement>(tab, '.suggestion-ask,.suggestion-give');
  const postMotion = useContentMotion<HTMLElement>(post?.id || '');
  const readKey = 'suggestions-read:' + account;
  const imageLabels = ['B30', 'P30'].filter(label => images[label]);
  const askImage = images[askLabel], commentImage = images[commentLabel], askUrl = useBlobUrl(askImage), commentUrl = useBlobUrl(commentImage);
  useEffect(() => { if (!images[askLabel]) setAskLabel(imageLabels[0] || ''); if (!images[commentLabel]) setCommentLabel(''); }, [images]);
  const load = async (id?: string) => {
    const revision = ++seq.current; setLoading(true); setError('');
    try {
      if (id || tab === '给建议') {
        const current = currentPost.current?.id;
        const data = await api(id ? 'suggestions/posts/' + id : 'suggestions/random' + (current ? '?exclude_ids=' + encodeURIComponent(current) : ''));
        if (revision === seq.current) { setPost(data); setText(''); setCommentLabel(''); }
      } else {
        const [mine, replies] = await Promise.all([api('suggestions/mine'), api('suggestions/commented')]);
        if (revision === seq.current) { setOwnPosts(Array.isArray(mine) ? mine : mine.items || []); setCommented(Array.isArray(replies) ? replies : replies.items || []); }
      }
    } catch (e) { if (revision === seq.current && (e as any).status !== 404) setError((e as Error).message); }
    finally { if (revision === seq.current) setLoading(false); }
  };
  useEffect(() => { if (postId) { setTab('给建议'); load(postId); } else load(); return () => { seq.current++; }; }, [tab, postId]);
  const markRead = async () => {
    try { const data = await api('suggestions/notifications?after=' + encodeURIComponent(localRead(readKey, new Date(0).toISOString()))); localWrite(readKey, data.checkedAt || new Date().toISOString()); setNotificationCount(0); await load(); }
    catch (e) { notify((e as Error).message); }
  };
  const send = async (comment = false) => {
    if (busy) return; setBusy(true); setError('');
    try {
      const data = new FormData();
      if (comment) {
        data.append('text', text.trim()); if (commentImage) data.append('image', new File([commentImage], 'PSQ-' + commentLabel + '.png', { type: 'image/png' }));
        await api('suggestions/posts/' + post.id + '/comments', data); setText(''); setCommentLabel(''); await load(post.id); notify('建议已发送');
      } else {
        if (!askImage) throw new Error('请先在成绩图页面生成 B30 / P30');
        data.append('description', description.trim()); data.append('image', new File([askImage], 'PSQ-' + askLabel + '.png', { type: 'image/png' }));
        const created = await api('suggestions/posts', data); setDescription('求建议！'); setPost(created); setTab('给建议'); go('suggestions/' + created.id); notify('求助已发布');
      }
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  };
  const remove = async () => {
    if (!deletion) return;
    try { await api(deletion.path, undefined, 'DELETE'); notify('已删除'); if (deletion.post) { setPost(null); if (postId) go('suggestions'); await load(); } else await load(post.id); }
    catch (e) { notify((e as Error).message); }
  };
  const selectTab = (value: string) => { setTab(value); if (postId) go('suggestions'); };
  const refreshRandom = () => { if (!loading) load(); };
  return <section ref={pageMotion} className="page readable suggestions-page"><PageHeader title="求建议 / 给建议" onBack={() => go('more')} />
    <div className="suggestion-tabs">{['求建议', '给建议'].map(value => <button className={'chip' + (tab === value ? ' selected' : '')} key={value} aria-pressed={tab === value} onClick={() => selectTab(value)}>{value}</button>)}</div>
    {error && <p className="error-message" role="alert">{error}</p>}
    {tab === '求建议' ? <div className="suggestion-ask">
      {imageLabels.length > 0 && <div className="suggestion-image-choices">{imageLabels.map(label => <button className={'chip' + (askLabel === label ? ' selected' : '')} key={label} onClick={() => setAskLabel(label)}>{label}</button>)}</div>}
      <div className="suggestion-image-picker">{askUrl ? <button className="post-image" onClick={() => setViewer(askUrl)}><img src={askUrl} alt="待发送成绩图" /></button> : <div className="suggestion-no-image"><ImagePlus size={42} /><strong>请先在成绩图页面生成 B30 / P30</strong></div>}</div>
      <div className="suggestion-notification"><Switch label="评论通知" detail="有新建议时提醒我" checked={preferences.notifications} onChange={value => setPreference('notifications', value)} /></div>
      <form className="suggestion-post-form" onSubmit={e => { e.preventDefault(); send(); }}><label className="field"><span>文字描述</span><textarea value={description} onChange={e => setDescription(e.target.value)} maxLength={120} rows={2} placeholder="求建议！" /></label><button className="button primary full" disabled={busy || !askImage || moderation?.publicHidden}><Upload size={20} />{busy ? '正在发送…' : '发送成绩图'}</button></form>
      {notificationCount > 0 && <button className="notice-banner" onClick={markRead}><Bell size={18} />{notificationCount} 条求助有新回复<ChevronRight size={17} /></button>}
      <div className="section-heading"><h2>我的求建议帖子</h2><button className="icon-button accent" aria-label="刷新我的帖子" disabled={loading} onClick={() => load()}><RefreshCw size={20} className={loading ? 'spin' : ''} /></button></div>
      {loading && <Loading />}{ownPosts.map(item => <div className="suggestion-own-post" key={item.id}><button onClick={() => go('suggestions/' + item.id)}><strong>{item.description}</strong><p className="muted">{chineseDate(item.createdAt)} · {item.comments?.length ?? item.commentCount ?? 0} 条评论</p></button>{item.canDelete && <button className="icon-button" aria-label="删除帖子" onClick={() => setDeletion({ path: 'suggestions/posts/' + item.id, post: true })}><Trash2 size={20} /></button>}</div>)}
      {!loading && !ownPosts.length && <p className="muted small">暂无求建议帖子</p>}
      {commented.length > 0 && <><h2>我的建议评论</h2>{commented.map(item => <button className="suggestion-commented-post" key={item.id} onClick={() => go('suggestions/' + item.id)}><strong>{item.description}</strong><p className="muted">{item.comments?.filter((comment: any) => comment.canDelete).length ?? item.commentCount ?? 0} 条我的评论 · 点击管理</p></button>)}</>}
    </div> : <div className="suggestion-give" onPointerDown={e => { if (!(e.target as HTMLElement).closest('button,input,textarea')) swipeStart.current = { x: e.clientX, y: e.clientY }; }} onPointerCancel={() => { swipeStart.current = null; }} onPointerUp={e => {
      const start = swipeStart.current; swipeStart.current = null;
      if (start && start.x - e.clientX >= 64 && start.x - e.clientX > Math.abs(start.y - e.clientY) * 1.2) { e.stopPropagation(); if (postId) go('suggestions'); refreshRandom(); }
    }}>
      {guide && <div className="suggestion-swipe-guide"><strong>滑动刷新成绩图</strong><p>向左滑动：随机刷新一张新的成绩图</p><button className="text-button" onClick={() => { setGuide(false); localWrite('suggestion-swipe-guide-seen', true); }}>知道了</button></div>}
      <div className="section-heading"><span /><button className="text-button" onClick={() => { if (postId) go('suggestions'); refreshRandom(); }} disabled={loading}><Shuffle size={17} />换一张成绩图</button></div>
      {loading && !post ? <Loading /> : !post ? <Empty title="暂无求助" action={refreshRandom} label="抽一张成绩图" /> : <>
        <article ref={postMotion} className="suggestion-post"><div className="section-heading"><Author author={post.author} />{post.canDelete && <button className="icon-button" aria-label="删除帖子" onClick={() => setDeletion({ path: 'suggestions/posts/' + post.id, post: true })}><Trash2 size={20} /></button>}</div>{loading && <Loading />}<SuggestionPostImage key={post.id + ':' + post.imageUrl} url={mediaUrl(post.imageUrl)} alt={post.author.nickname + ' 的成绩图'} onView={() => setViewer(mediaUrl(post.imageUrl))} /><div><p className="pre-wrap suggestion-description">{post.description}</p><p className="muted suggestion-time">{chineseDate(post.createdAt)}</p></div></article>
        {!!post.comments?.length && <div className="suggestion-comments">{post.comments.map((comment: any) => <article className="card suggestion-comment" key={comment.id}><div className="section-heading"><Author author={comment.author} />{comment.canDelete && <button className="icon-button" aria-label="删除评论" onClick={() => setDeletion({ path: 'suggestions/comments/' + comment.id, post: false })}><Trash2 size={20} /></button>}</div>{comment.text && <p className="pre-wrap">{comment.text}</p>}{comment.imageUrl && <button className="post-image comment-image" onClick={() => setViewer(mediaUrl(comment.imageUrl))}><img src={mediaUrl(comment.imageUrl)} alt="评论成绩图" loading="lazy" /></button>}<p className="muted suggestion-time">{chineseDate(comment.createdAt)}</p></article>)}</div>}
        <form className="card suggestion-composer" onSubmit={e => { e.preventDefault(); send(true); }}><textarea aria-label="写下建议" value={text} onChange={e => setText(e.target.value)} maxLength={240} rows={2} placeholder="写下建议" />{commentUrl && <button type="button" className="post-image" onClick={() => setViewer(commentUrl)}><img src={commentUrl} alt="待发布成绩图" /></button>}<div className="suggestion-compose-actions">{imageLabels.length ? imageLabels.map(label => <button type="button" key={label} className={'chip' + (commentLabel === label ? ' selected' : '')} onClick={() => setCommentLabel(current => current === label ? '' : label)}>{label}</button>) : <button type="button" className="button outline" disabled><ImagePlus size={18} />暂无成绩图</button>}<button className="button primary" aria-label="发布建议" disabled={busy || (!text.trim() && !commentImage) || moderation?.publicHidden}><Send size={20} /></button></div></form>
      </>}
    </div>}
    {viewer && <ImageViewer url={viewer} title="成绩与建议" onClose={() => setViewer('')} />}{deletion && <Confirm title={deletion.post ? '删除求建议帖子？' : '删除这条评论？'} body={deletion.post ? '帖子及其全部评论将不再展示。' : '删除后无法恢复。'} danger onClose={() => setDeletion(null)} onConfirm={remove} />}
  </section>;
}

export function Feedback({ go, create = false }: { go: (page: string) => void; create?: boolean }) {
  const { notify, account, setFeedbackCount } = usePSQ();
  const key = 'feedback-draft:' + account;
  const [draft, setDraft] = useState(() => localRead(key, { title: '', body: '', id: crypto.randomUUID() })), [files, setFiles] = useState<File[]>([]), [list, setList] = useState<any[]>([]), [detail, setDetail] = useState<any>(null), [loading, setLoading] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(''), [more, setMore] = useState(false), [viewer, setViewer] = useState('');
  useEffect(() => localWrite(key, draft), [draft, key]);
  const load = async (append = false) => {
    setLoading(true); setError('');
    try { const data = await api('feedback/mine?offset=' + (append ? list.length : 0)); setList(current => append ? [...current, ...data] : data); setMore(data.length >= 20); }
    catch (e) { setError((e as Error).message); } finally { setLoading(false); }
  };
  useEffect(() => { if (!create) load(); }, [create]);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true); setError('');
    try { await api('feedback', { id: draft.id, title: draft.title.trim(), body: draft.body.trim(), images: await Promise.all(files.map(fileDataUrl)) }); setDraft({ title: '', body: '', id: crypto.randomUUID() }); setFiles([]); notify('反馈已提交'); go('feedback'); }
    catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  };
  const open = async (item: any) => {
    setDetail(item); if (item.revision <= item.readRevision) return;
    try { await api('feedback/' + item.id + '/read', { revision: item.revision }); setList(current => current.map(f => f.id === item.id ? { ...f, readRevision: item.revision } : f)); const items = await api('feedback/notifications'); setFeedbackCount(items.length); } catch {}
  };
  const status = (value: string) => value === 'processing' ? '处理中' : value === 'resolved' ? '已处理' : '待处理';
  return <section className="page readable"><PageHeader title={create ? '提交反馈' : '我的反馈'} onBack={() => go('settings')} refresh={!create ? () => load() : undefined} loading={loading}>{!create && <button className="icon-button" aria-label="提交反馈" onClick={() => go('feedback/new')}><Plus /></button>}</PageHeader>{error && <p className="error-message" role="alert">{error}</p>}
    {create ? <form className="card stack" onSubmit={submit}><label className="field"><span>标题</span><input value={draft.title} maxLength={80} placeholder="概括问题或建议" onChange={e => setDraft(d => ({ ...d, title: e.target.value }))} required /></label><label className="field"><span>内容</span><textarea value={draft.body} maxLength={4000} rows={7} placeholder="描述具体情况、操作步骤与期望结果" onChange={e => setDraft(d => ({ ...d, body: e.target.value }))} required /></label><Attachments files={files} setFiles={setFiles} /><button className="button primary" disabled={busy || !draft.title.trim() || !draft.body.trim()}><Send size={18} />{busy ? '正在提交…' : '提交反馈'}</button></form> : <>{loading && !list.length ? <Loading /> : <div className="stack">{list.map(f => <button className="card feedback-summary" key={f.id} onClick={() => open(f)}><div className="section-heading"><h3>{f.title}</h3><span className={'status-chip status-' + f.status}>{status(f.status)}</span></div><p className="muted small line-clamp">{f.body}</p><div className="section-heading"><span className="muted tiny">{chineseDate(f.updatedAt)}</span>{f.revision > f.readRevision && <span className="badge">新进展</span>}</div></button>)}</div>}{!loading && !list.length && <Empty title="暂无反馈" action={() => go('feedback/new')} label="提交反馈" />}{more && <button className="text-button" disabled={loading} onClick={() => load(true)}>加载更多</button>}</>}
    {detail && <Modal title={detail.title} wide onClose={() => setDetail(null)}><div className="stack"><span className={'status-chip status-' + detail.status}>{status(detail.status)}</span><p className="pre-wrap">{detail.body}</p>{detail.imageCount > 0 && <div className="feedback-images">{Array.from({ length: detail.imageCount }, (_, i) => { const path = '/api/backend/feedback/' + detail.id + '/images/' + i; return <button className="post-image" key={i} onClick={() => setViewer(path)}><img src={path} alt={'反馈图片 ' + (i + 1)} /></button>; })}</div>}{detail.reply && <div className="reply-card"><h3>管理员回复</h3><p className="pre-wrap">{detail.reply}</p></div>}<p className="muted tiny">提交时间 {chineseDate(detail.createdAt)}<br />更新时间 {chineseDate(detail.updatedAt)}</p></div></Modal>}{viewer && <ImageViewer url={viewer} title="反馈图片" onClose={() => setViewer('')} />}</section>;
}
