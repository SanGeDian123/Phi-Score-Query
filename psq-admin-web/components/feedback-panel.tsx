'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';

type Feedback = { id: string; title: string; body: string; status: string; reply: string; createdAt: string; updatedAt: string; revision: number; imageCount: number };
const labels: Record<string, string> = { pending: '待处理', processing: '处理中', resolved: '已处理' };

function FeedbackImage({ token, id, position }: { token: string; id: string; position: number }) {
  const [url, setUrl] = useState('');
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let objectUrl = ''; let disposed = false;
    const controller = new AbortController();
    setFailed(false); setUrl('');
    fetch(`/api/feedback/${id}/images/${position}`, { headers: { 'X-Admin-Token': token }, signal: controller.signal })
      .then(async response => { if (!response.ok) throw new Error('图片加载失败'); return response.blob(); })
      .then(blob => { if (!disposed) { objectUrl = URL.createObjectURL(blob); setUrl(objectUrl); } })
      .catch(() => { if (!disposed) setFailed(true); });
    return () => { disposed = true; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [token, id, position, attempt]);
  if (failed) return <Button variant="outline" onClick={() => setAttempt(attempt + 1)}>重新加载图片 {position + 1}</Button>;
  if (!url) return <p className="text-sm text-muted-foreground">正在加载图片…</p>;
  return <a href={url} target="_blank" rel="noreferrer" aria-label={`查看附件 ${position + 1} 原图`}><img src={url} alt={`反馈附件 ${position + 1}`} className="max-h-72 w-full rounded-xl border object-contain" /></a>;
}

export function FeedbackPanel({ token, connected }: { token: string; connected: boolean }) {
  const [items, setItems] = useState<Feedback[]>([]);
  const [offset, setOffset] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [selected, setSelected] = useState<Feedback | null>(null);
  const [status, setStatus] = useState('pending');
  const [reply, setReply] = useState('');
  const load = useCallback(async (signal?: AbortSignal) => {
    if (!connected) return;
    setBusy(true); setMessage('');
    try {
      const response = await fetch(`/api/feedback?offset=${offset}`, { headers: { 'X-Admin-Token': token }, signal });
      const data = await response.json() as Feedback[] | { error?: string };
      if (!response.ok || !Array.isArray(data)) throw new Error(!Array.isArray(data) ? data.error || '加载反馈失败' : '加载反馈失败');
      if (!signal?.aborted) setItems(data);
    } catch (error) { if (!signal?.aborted) setMessage(error instanceof Error ? error.message : '加载失败'); }
    finally { if (!signal?.aborted) setBusy(false); }
  }, [token, connected, offset]);
  useEffect(() => {
    setItems([]); setSelected(null);
    const controller = new AbortController();
    if (connected) void load(controller.signal);
    return () => controller.abort();
  }, [load, connected]);
  async function save() {
    if (!selected) return;
    setBusy(true); setMessage('');
    try {
      const response = await fetch(`/api/feedback/${selected.id}`, { method: 'POST', headers: { 'X-Admin-Token': token, 'Content-Type': 'application/json' }, body: JSON.stringify({ status, reply, revision: selected.revision }) });
      const data = await response.json() as { error?: string };
      if (!response.ok) throw new Error(data.error || '保存失败');
      setSelected(null); await load(); setMessage('处理结果已保存，用户可在 APP 查看更新。');
    } catch (error) { setMessage(error instanceof Error ? error.message : '保存失败，请重试'); }
    finally { setBusy(false); }
  }
  return <Card id="feedback" className="mt-6 min-w-0 scroll-mt-6">
    <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 px-4 sm:px-6"><CardTitle>用户反馈</CardTitle><Button variant="outline" disabled={!connected || busy} onClick={() => void load()}>刷新</Button></CardHeader>
    <CardContent className="space-y-4 px-4 sm:px-6">
      {message && <p role="status" className="break-words text-sm">{message}</p>}
      {!connected ? <p className="text-muted-foreground">连接管理台后查看反馈</p> : busy && !items.length ? <p>正在加载…</p> : !items.length ? <p className="text-muted-foreground">暂无反馈</p> :
        <div className="grid gap-3 md:grid-cols-2">{items.map(item => <button key={item.id} disabled={busy} onClick={() => { setSelected(item); setStatus(item.status); setReply(item.reply); setMessage(''); }} className="min-w-0 rounded-2xl border p-4 text-left transition-colors hover:bg-primary/5 focus-visible:outline-2 focus-visible:outline-primary">
          <div className="flex flex-wrap items-center justify-between gap-2"><span className="break-words font-semibold">{item.title}</span><span className="text-sm text-primary">{labels[item.status] || item.status}</span></div>
          <p className="mt-2 line-clamp-2 break-words text-sm text-muted-foreground">{item.body}</p>
          <p className="mt-3 text-sm text-muted-foreground">{new Date(item.createdAt).toLocaleString('zh-CN')} · {item.imageCount} 张图片</p>
        </button>)}</div>}
      <div className="flex flex-wrap items-center justify-between gap-3"><Button variant="outline" disabled={busy || offset === 0} onClick={() => setOffset(Math.max(0, offset - 30))}>上一页</Button><span className="text-sm">第 {offset / 30 + 1} 页</span><Button variant="outline" disabled={busy || items.length < 30} onClick={() => setOffset(offset + 30)}>下一页</Button></div>
    </CardContent>
    <Dialog open={!!selected} onOpenChange={open => { if (!open && !busy) setSelected(null); }}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader><DialogTitle className="break-words">{selected?.title}</DialogTitle><DialogDescription>查看反馈并回复提交者</DialogDescription></DialogHeader>
        {selected && <div className="space-y-4">
          <p className="whitespace-pre-wrap break-words text-base">{selected.body}</p>
          <div className="grid gap-3 sm:grid-cols-2">{Array.from({ length: selected.imageCount }, (_, position) => <FeedbackImage key={`${selected.id}-${position}`} token={token} id={selected.id} position={position} />)}</div>
          <label className="block space-y-2 text-sm"><span>处理状态</span><select value={status} disabled={busy} onChange={event => setStatus(event.target.value)} className="block h-11 w-full rounded-xl border bg-background px-3">{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label className="block space-y-2 text-sm"><span>管理员回复</span><Textarea value={reply} disabled={busy} onChange={event => setReply(event.target.value)} maxLength={4000} rows={6} placeholder="请说明处理结果；设为已处理时必填" /></label>
          {message && <p role="alert" className="text-sm text-destructive">{message}</p>}
          <Button className="w-full" disabled={busy || (status === 'resolved' && !reply.trim()) || (status === selected.status && reply === selected.reply)} onClick={() => void save()}>{busy ? '正在保存…' : '保存并通知用户'}</Button>
        </div>}
      </DialogContent>
    </Dialog>
  </Card>;
}
