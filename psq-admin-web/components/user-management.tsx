'use client';

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  Ban,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  ImagePlus,
  LoaderCircle,
  RefreshCw,
  Search,
  Shield,
  ShieldAlert,
  ShieldCheck,
  UnlockKeyhole,
  UserRound,
  UsersRound,
} from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { challengeModeLabel } from '@/lib/challenge-mode';

type Restriction = { active: boolean; reason: string | null; expiresAt: string | null };
type User = {
  userHash: string;
  alias: string | null;
  nickname: string | null;
  avatar: string | null;
  rks: number;
  challengeModeRank: number | null;
  leaderboardRank: number | null;
  publicHidden: Restriction;
  accountSuspended: Restriction;
};
type Appeal = {
  id: string;
  userHash: string;
  alias: string | null;
  nickname: string | null;
  avatar: string | null;
  rks: number;
  challengeModeRank: number | null;
  restrictionType: string;
  body: string;
  status: string;
  adminReply: string;
  createdAt: string;
  updatedAt: string;
  imageCount: number;
};
type RestrictionKind = 'public_hidden' | 'account_suspended';
type RestrictionFilter = 'all' | RestrictionKind;
type RestrictedUsersResponse = {
  items: User[];
  total: number;
  page: number;
  pageSize: number;
  counts: { all: number; publicHidden: number; accountSuspended: number };
};

const durations = [
  { label: '1 小时', minutes: 60 },
  { label: '24 小时', minutes: 1440 },
  { label: '7 天', minutes: 10080 },
  { label: '30 天', minutes: 43200 },
  { label: '长期', minutes: null },
] as const;

async function readJson<T>(response: Response): Promise<T> {
  const payload = await response.json() as T & { error?: string };
  if (!response.ok) throw new Error(payload.error || `请求失败（${response.status}）`);
  return payload;
}

function dateLabel(value: string | null): string {
  if (!value) return '长期';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : `${date.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })}（UTC+8）`;
}

function displayName(user: Pick<User, 'nickname' | 'alias'>): string {
  return user.nickname?.trim() || user.alias?.trim() || '未设置昵称';
}

function avatarUrl(name: string | null): string | null {
  if (!name) return null;
  return `/api/user-management/avatar?name=${encodeURIComponent(name)}`;
}

function UserAvatar({ name, avatar }: { name: string; avatar: string | null }) {
  const [failed, setFailed] = useState(false);
  const src = useMemo(() => avatarUrl(avatar), [avatar]);
  useEffect(() => setFailed(false), [src]);
  return (
    <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-2xl border border-primary/10 bg-primary/8 text-primary">
      {src && !failed ? <img alt={`${name}的头像`} className="size-full object-cover" onError={() => setFailed(true)} src={src} /> : <UserRound className="size-5" />}
    </div>
  );
}

function AppealImages({ appeal, token, onView }: { appeal: Appeal; token: string; onView: (src: string) => void }) {
  const [images, setImages] = useState<string[]>([]);
  useEffect(() => {
    let live = true;
    const urls: string[] = [];
    void Promise.all(Array.from({ length: appeal.imageCount }, async (_, position) => {
      const response = await fetch(`/api/user-management/appeals/${appeal.id}/images/${position}`, { headers: { 'X-Admin-Token': token } });
      if (!response.ok) return null;
      return URL.createObjectURL(await response.blob());
    })).then((results) => {
      for (const result of results) if (result) urls.push(result);
      if (live) setImages(urls);
      else urls.forEach(URL.revokeObjectURL);
    }).catch(() => undefined);
    return () => {
      live = false;
      urls.forEach(URL.revokeObjectURL);
    };
  }, [appeal.id, appeal.imageCount, token]);
  if (!appeal.imageCount) return null;
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {images.map((src, index) => <button aria-label={`放大申诉材料 ${index + 1}`} key={src} className="block overflow-hidden rounded-xl border" onClick={() => onView(src)} type="button"><img alt={`申诉材料 ${index + 1}`} className="size-20 object-cover" src={src} /></button>)}
      {!images.length && <span className="flex items-center gap-1 text-xs text-muted-foreground"><ImagePlus className="size-3.5" />{appeal.imageCount} 张附件加载中</span>}
    </div>
  );
}

export function UserManagementPanel({ token, connected }: { token: string; connected: boolean }) {
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState<User[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [restrictedUsers, setRestrictedUsers] = useState<User[]>([]);
  const [restrictionFilter, setRestrictionFilter] = useState<RestrictionFilter>('all');
  const [restrictedQueryInput, setRestrictedQueryInput] = useState('');
  const [restrictedQuery, setRestrictedQuery] = useState('');
  const [restrictedPage, setRestrictedPage] = useState(1);
  const [restrictedPageSize, setRestrictedPageSize] = useState(20);
  const [restrictedTotal, setRestrictedTotal] = useState(0);
  const [restrictedCounts, setRestrictedCounts] = useState<RestrictedUsersResponse['counts']>({ all: 0, publicHidden: 0, accountSuspended: 0 });
  const [restrictedLoading, setRestrictedLoading] = useState(false);
  const [restrictedLoaded, setRestrictedLoaded] = useState(false);
  const [restrictedError, setRestrictedError] = useState('');
  const restrictedRequest = useRef(0);
  const [appeals, setAppeals] = useState<Appeal[]>([]);
  const [appealFilter, setAppealFilter] = useState('pending');
  const [appealsLoading, setAppealsLoading] = useState(false);
  const [appealError, setAppealError] = useState('');
  const [appealReply, setAppealReply] = useState<Record<string, string>>({});
  const [busyAppeal, setBusyAppeal] = useState<string | null>(null);
  const [pendingUser, setPendingUser] = useState<{ user: User; kind: RestrictionKind } | null>(null);
  const [restrictionReason, setRestrictionReason] = useState('');
  const [duration, setDuration] = useState<number | null>(null);
  const [savingRestriction, setSavingRestriction] = useState(false);
  const [restrictionError, setRestrictionError] = useState('');
  const [viewer, setViewer] = useState<string | null>(null);
  const [busyUser, setBusyUser] = useState<string | null>(null);
  const [userActionError, setUserActionError] = useState<{ userHash: string; message: string } | null>(null);

  const searchUsers = useCallback(async (event?: FormEvent) => {
    event?.preventDefault();
    if (!connected || !query.trim() || searching) return;
    setSearching(true);
    setSearchError('');
    try {
      const response = await fetch(`/api/user-management/search?query=${encodeURIComponent(query.trim())}`, { headers: { 'X-Admin-Token': token }, cache: 'no-store' });
      const result = await readJson<{ items: User[] }>(response);
      setUsers(result.items);
      if (!result.items.length) setSearchError('没有找到匹配的用户');
    } catch (error) {
      setSearchError(error instanceof Error ? error.message : '搜索失败');
    } finally {
      setSearching(false);
    }
  }, [connected, query, searching, token]);

  const loadRestrictedUsers = useCallback(async () => {
    const requestId = ++restrictedRequest.current;
    if (!connected) return;
    setRestrictedLoading(true);
    setRestrictedError('');
    try {
      const params = new URLSearchParams({ restrictionType: restrictionFilter, query: restrictedQuery, page: String(restrictedPage) });
      const response = await fetch(`/api/user-management/restricted?${params}`, { headers: { 'X-Admin-Token': token }, cache: 'no-store' });
      const result = await readJson<RestrictedUsersResponse>(response);
      if (requestId !== restrictedRequest.current) return;
      setRestrictedUsers(result.items);
      setRestrictedTotal(result.total);
      setRestrictedCounts(result.counts);
      setRestrictedPageSize(result.pageSize);
      setRestrictedPage(result.page);
      setRestrictedLoaded(true);
    } catch (error) {
      if (requestId === restrictedRequest.current) setRestrictedError(error instanceof Error ? error.message : '受限用户列表加载失败');
    } finally {
      if (requestId === restrictedRequest.current) setRestrictedLoading(false);
    }
  }, [connected, restrictedPage, restrictedQuery, restrictionFilter, token]);

  const loadAppeals = useCallback(async () => {
    if (!connected) return;
    setAppealsLoading(true);
    setAppealError('');
    try {
      const response = await fetch(`/api/user-management/appeals?status=${appealFilter}`, { headers: { 'X-Admin-Token': token }, cache: 'no-store' });
      setAppeals(await readJson<Appeal[]>(response));
    } catch (error) {
      setAppealError(error instanceof Error ? error.message : '申诉列表加载失败');
    } finally {
      setAppealsLoading(false);
    }
  }, [appealFilter, connected, token]);

  useEffect(() => {
    setUsers([]);
    setAppeals([]);
    setPendingUser(null);
    setViewer(null);
    setRestrictedUsers([]);
    setRestrictedTotal(0);
    setRestrictedCounts({ all: 0, publicHidden: 0, accountSuspended: 0 });
    setRestrictedLoaded(false);
    setRestrictedLoading(false);
    setRestrictedError('');
    setRestrictedPage(1);
    setUserActionError(null);
  }, [token, connected]);
  useEffect(() => { void loadAppeals(); }, [loadAppeals]);
  useEffect(() => {
    void loadRestrictedUsers();
    const timer = connected ? window.setInterval(() => void loadRestrictedUsers(), 60_000) : undefined;
    return () => {
      if (timer !== undefined) window.clearInterval(timer);
      restrictedRequest.current++;
    };
  }, [connected, loadRestrictedUsers]);

  const openRestriction = (user: User, kind: RestrictionKind) => {
    const active = kind === 'public_hidden' ? user.publicHidden : user.accountSuspended;
    setRestrictionReason(active.reason ?? '');
    setDuration(active.expiresAt ? Math.max(1, Math.ceil((new Date(active.expiresAt).valueOf() - Date.now()) / 60_000)) : kind === 'account_suspended' && !active.active ? 1440 : null);
    setRestrictionError('');
    setUserActionError(null);
    setPendingUser({ user, kind });
  };

  const refreshUserViews = async () => {
    await Promise.all([loadRestrictedUsers(), loadAppeals(), query.trim() ? searchUsers() : Promise.resolve()]);
  };

  const saveRestriction = async () => {
    if (!pendingUser || savingRestriction) return;
    setSavingRestriction(true);
    setRestrictionError('');
    try {
      const response = await fetch('/api/user-management/restriction', {
        method: 'POST',
        headers: { 'X-Admin-Token': token, 'Content-Type': 'application/json' },
        body: JSON.stringify({ userHash: pendingUser.user.userHash, restrictionType: pendingUser.kind, active: true, reason: restrictionReason, durationMinutes: duration }),
      });
      await readJson<Restriction>(response);
      setPendingUser(null);
      await refreshUserViews();
    } catch (error) {
      setRestrictionError(error instanceof Error ? error.message : '设置限制失败');
    } finally {
      setSavingRestriction(false);
    }
  };

  const removeRestriction = async (user: User, kind: RestrictionKind) => {
    if (busyUser) return;
    setBusyUser(user.userHash);
    setUserActionError(null);
    try {
      const response = await fetch('/api/user-management/restriction', {
      method: 'POST',
      headers: { 'X-Admin-Token': token, 'Content-Type': 'application/json' },
      body: JSON.stringify({ userHash: user.userHash, restrictionType: kind, active: false }),
      });
      await readJson<Restriction>(response);
      await refreshUserViews();
    } catch (error) {
      setUserActionError({ userHash: user.userHash, message: error instanceof Error ? error.message : '解除限制失败' });
    } finally {
      setBusyUser(null);
    }
  };

  const reviewAppeal = async (appeal: Appeal, status: 'reviewing' | 'accepted' | 'rejected') => {
    setBusyAppeal(appeal.id);
    setAppealError('');
    try {
      const response = await fetch(`/api/user-management/appeals/${appeal.id}`, {
        method: 'POST',
        headers: { 'X-Admin-Token': token, 'Content-Type': 'application/json' },
        body: JSON.stringify({ status, reply: appealReply[appeal.id] ?? '' }),
      });
      await readJson<Appeal>(response);
      await refreshUserViews();
    } catch (error) {
      setAppealError(error instanceof Error ? error.message : '处理申诉失败');
    } finally {
      setBusyAppeal(null);
    }
  };

  const restrictionTitle = pendingUser?.kind === 'account_suspended' ? '暂停账户使用' : '停止公开展示';
  const restrictedPageCount = Math.max(1, Math.ceil(restrictedTotal / restrictedPageSize));

  return (
    <section className="scroll-mt-28" id="user-management">
      <div className="psq-rise mt-7 flex flex-col gap-4">
        <Card className="psq-glass-card overflow-hidden rounded-2xl border-primary/10 shadow-[0_16px_50px_rgb(30_76_142/5%)]">
          <CardHeader className="border-b border-primary/[0.07] pb-4 sm:flex sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.16em] text-primary"><Shield className="size-3.5" /> Account controls</div>
              <CardTitle className="text-xl font-black">用户管理</CardTitle>
              <CardDescription className="mt-1">按用户名查找账号资料，管理公开展示与账户使用状态。</CardDescription>
            </div>
            <Badge className="w-fit border-primary/10 bg-primary/5 text-primary" variant="outline"><ShieldCheck className="mr-1 size-3.5" />{connected ? '已连接' : '连接后可管理'}</Badge>
          </CardHeader>
          <CardContent className="p-4 sm:p-6">
            <form className="flex flex-col gap-2 sm:flex-row" onSubmit={(event) => void searchUsers(event)}>
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input className="psq-glass-control h-11 rounded-xl border-primary/12 pl-9" disabled={!connected} maxLength={40} onChange={(event) => setQuery(event.target.value)} placeholder="输入用户名或昵称" value={query} />
              </div>
              <Button className="h-11 rounded-xl px-5" disabled={!connected || searching || !query.trim()} type="submit">
                {searching ? <LoaderCircle className="animate-spin" /> : <Search />}
                搜索用户
              </Button>
            </form>
            {searchError && <p className="mt-3 text-sm text-muted-foreground" role="status">{searchError}</p>}
            <div className="mt-4 space-y-3">
              {users.map((user, index) => <ManagedUserCard key={user.userHash} user={user} index={index} disabled={!connected || Boolean(busyUser) || savingRestriction} working={busyUser === user.userHash} error={userActionError?.userHash === user.userHash ? userActionError.message : undefined} onRestrict={openRestriction} onRemove={(item, kind) => void removeRestriction(item, kind)} />)}
            </div>
          </CardContent>
        </Card>

        <Card className="psq-glass-card overflow-hidden rounded-2xl border-primary/10 shadow-[0_16px_50px_rgb(30_76_142/5%)]">
          <CardHeader className="gap-3 border-b border-primary/[0.07] pb-4 sm:flex sm:flex-row sm:items-end sm:justify-between">
            <div className="min-w-0">
              <div className="mb-2 flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.16em] text-primary"><UsersRound className="size-3.5" /> Restricted accounts</div>
              <CardTitle className="text-lg font-black leading-relaxed sm:text-xl">已禁止公开展示 / 暂停账号用户列表</CardTitle>
              <CardDescription className="mt-1">查看当前生效的限制，直接修改时长或解除。</CardDescription>
            </div>
            <Button className="h-9 w-fit shrink-0 rounded-xl" disabled={!connected || restrictedLoading} onClick={() => void loadRestrictedUsers()} variant="outline"><RefreshCw className={restrictedLoading ? 'animate-spin' : ''} />刷新列表</Button>
          </CardHeader>
          <CardContent className="space-y-4 p-4 sm:p-6">
            <div aria-label="受限用户类型" className="grid grid-cols-3 gap-1.5 rounded-xl border border-primary/10 bg-primary/[0.035] p-1">
              {([
                ['all', '全部', restrictedCounts.all],
                ['public_hidden', '禁止公开展示', restrictedCounts.publicHidden],
                ['account_suspended', '暂停账号', restrictedCounts.accountSuspended],
              ] as const).map(([key, label, count]) => <Button aria-pressed={restrictionFilter === key} className="h-auto min-h-10 min-w-0 flex-wrap gap-1.5 rounded-lg px-2 py-2 text-xs" disabled={!connected} key={key} onClick={() => { setRestrictionFilter(key); setRestrictedPage(1); }} variant={restrictionFilter === key ? 'secondary' : 'ghost'}><span>{label}</span><span className="rounded-md bg-primary/[0.06] px-1.5 py-0.5 text-[10px] tabular-nums">{restrictedLoaded ? count : '—'}</span></Button>)}
            </div>
            <form className="flex flex-col gap-2 sm:flex-row" onSubmit={(event) => {
              event.preventDefault();
              if (!connected || restrictedLoading) return;
              const nextQuery = restrictedQueryInput.trim();
              if (nextQuery === restrictedQuery && restrictedPage === 1) void loadRestrictedUsers();
              else { setRestrictedQuery(nextQuery); setRestrictedPage(1); }
            }}>
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input aria-label="搜索受限用户" className="psq-glass-control h-10 rounded-xl border-primary/12 pl-9" disabled={!connected} maxLength={40} onChange={(event) => setRestrictedQueryInput(event.target.value)} placeholder="在受限用户中搜索用户名或昵称" value={restrictedQueryInput} />
              </div>
              <div className="flex gap-2">
                <Button className="h-10 flex-1 rounded-xl px-4 sm:flex-none" disabled={!connected || restrictedLoading} type="submit"><Search />筛选</Button>
                {restrictedQuery && <Button className="h-10 rounded-xl" disabled={!connected || restrictedLoading} onClick={() => { setRestrictedQueryInput(''); setRestrictedQuery(''); setRestrictedPage(1); }} type="button" variant="ghost">清空</Button>}
              </div>
            </form>
            {restrictedError && <p className="text-sm text-destructive" role="alert">{restrictedError}</p>}
            <div aria-busy={restrictedLoading} className="space-y-3">
              {!connected ? <div className="grid min-h-32 place-items-center rounded-xl border border-dashed border-primary/15 px-4 text-center text-sm text-muted-foreground">连接后台后即可查看受限用户</div> : restrictedLoading && !restrictedLoaded ? <div className="grid min-h-32 place-items-center text-sm text-muted-foreground" role="status"><span className="flex items-center gap-2"><LoaderCircle className="size-5 animate-spin" />加载受限用户</span></div> : restrictedUsers.length ? restrictedUsers.map((user, index) => <ManagedUserCard key={user.userHash} user={user} index={index} disabled={restrictedLoading || Boolean(restrictedError) || Boolean(busyUser) || savingRestriction} working={busyUser === user.userHash} error={userActionError?.userHash === user.userHash ? userActionError.message : undefined} onRestrict={openRestriction} onRemove={(item, kind) => void removeRestriction(item, kind)} />) : !restrictedError && <div className="grid min-h-32 place-items-center rounded-xl border border-dashed border-primary/15 px-4 text-center text-sm text-muted-foreground"><span>{restrictedQuery ? '没有匹配的受限用户' : restrictionFilter === 'public_hidden' ? '暂无被禁止公开展示的用户' : restrictionFilter === 'account_suspended' ? '暂无被暂停账号的用户' : '暂无受限用户'}</span></div>}
            </div>
            {connected && restrictedLoaded && <div className="flex flex-col gap-3 border-t border-primary/[0.07] pt-3 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
              <p aria-live="polite">共 <span className="font-semibold tabular-nums text-foreground">{restrictedTotal}</span> 位用户 · 每页 {restrictedPageSize} 位</p>
              <div className="flex items-center justify-between gap-3 sm:justify-end">
                <Button aria-label="受限用户上一页" className="h-8 rounded-lg px-3 text-xs" disabled={restrictedLoading || restrictedPage <= 1} onClick={() => setRestrictedPage((page) => page - 1)} variant="outline"><ChevronLeft />上一页</Button>
                <span className="tabular-nums">{restrictedPage} / {restrictedPageCount}</span>
                <Button aria-label="受限用户下一页" className="h-8 rounded-lg px-3 text-xs" disabled={restrictedLoading || restrictedPage >= restrictedPageCount} onClick={() => setRestrictedPage((page) => page + 1)} variant="outline">下一页<ChevronRight /></Button>
              </div>
            </div>}
          </CardContent>
        </Card>

        <Card className="psq-glass-card overflow-hidden rounded-2xl border-primary/10 shadow-[0_16px_50px_rgb(30_76_142/5%)]">
          <CardHeader className="border-b border-primary/[0.07] pb-4 sm:flex sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.16em] text-primary"><ShieldAlert className="size-3.5" /> Appeals</div>
              <CardTitle className="text-xl font-black">用户申诉</CardTitle>
              <CardDescription className="mt-1">查看用户提交的说明和图片材料，可受理、通过或驳回。</CardDescription>
            </div>
            <div className="flex gap-1 rounded-xl border border-primary/10 bg-primary/[0.035] p-1">
              {[['pending', '待处理'], ['reviewing', '处理中'], ['all', '全部']].map(([key, label]) => <Button className="h-8 rounded-lg px-3 text-xs" key={key} onClick={() => setAppealFilter(key)} variant={appealFilter === key ? 'secondary' : 'ghost'}>{label}</Button>)}
            </div>
          </CardHeader>
          <CardContent className="space-y-3 p-4 sm:p-6">
            {appealError && <p className="text-sm text-destructive" role="alert">{appealError}</p>}
            {appealsLoading ? <div className="grid min-h-32 place-items-center text-sm text-muted-foreground"><LoaderCircle className="size-5 animate-spin" /></div> : appeals.length === 0 ? (
              <div className="grid min-h-32 place-items-center rounded-xl border border-dashed border-primary/15 px-4 text-center text-sm text-muted-foreground"><div><ShieldCheck className="mx-auto size-6 text-primary/40" /><p className="mt-2">暂无{appealFilter === 'pending' ? '待处理' : ''}申诉</p></div></div>
            ) : appeals.map((appeal) => (
              <article className="psq-glass-subcard rounded-2xl border p-4 sm:p-5" key={appeal.id}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <UserAvatar avatar={appeal.avatar} name={appeal.alias || '用户'} />
                    <div className="min-w-0">
                      <h3 className="truncate text-sm font-bold">{appeal.nickname || appeal.alias || '用户'}</h3>
                      <p className="mt-1 text-[11px] text-muted-foreground">{appeal.alias ? `@${appeal.alias} · ` : ''}RKS {appeal.rks.toFixed(2)} · 课题 {challengeModeLabel(appeal.challengeModeRank)}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground"><Badge variant="outline">{appeal.restrictionType === 'account_suspended' ? '账户暂停' : '停止公开展示'}</Badge><span>{appeal.status === 'reviewing' ? '处理中' : appeal.status === 'accepted' ? '已通过' : appeal.status === 'rejected' ? '已驳回' : '待处理'}</span></div>
                </div>
                <p className="mt-3 whitespace-pre-wrap break-words rounded-xl bg-background/55 p-3 text-sm leading-6">{appeal.body}</p>
                <AppealImages appeal={appeal} onView={setViewer} token={token} />
                {(appeal.status === 'pending' || appeal.status === 'reviewing') ? <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                  <Textarea className="min-h-16 rounded-xl border-primary/12" maxLength={4000} disabled={busyAppeal === appeal.id} onChange={(event) => setAppealReply((current) => ({ ...current, [appeal.id]: event.target.value }))} placeholder="填写给用户的处理说明（可选）" value={appealReply[appeal.id] ?? ''} />
                  <div className="flex shrink-0 flex-wrap gap-2 sm:flex-col sm:justify-end">
                    {appeal.status === 'pending' && <Button className="h-9 rounded-xl" disabled={busyAppeal === appeal.id} onClick={() => void reviewAppeal(appeal, 'reviewing')} variant="outline"><Clock3 />开始处理</Button>}
                    <Button className="h-9 rounded-xl" disabled={busyAppeal === appeal.id} onClick={() => void reviewAppeal(appeal, 'accepted')}><Check />通过并解除限制</Button>
                    <Button className="h-9 rounded-xl" disabled={busyAppeal === appeal.id} onClick={() => void reviewAppeal(appeal, 'rejected')} variant="ghost">驳回申诉</Button>
                  </div>
                </div> : appeal.adminReply && <p className="mt-3 text-sm text-muted-foreground">处理说明：{appeal.adminReply}</p>}
                <p className="mt-2 flex items-center gap-1 text-[11px] text-muted-foreground"><Clock3 className="size-3" />提交于 {dateLabel(appeal.createdAt)}</p>
              </article>
            ))}
          </CardContent>
        </Card>
      </div>

      <Dialog onOpenChange={(open) => { if (!savingRestriction) setPendingUser(open ? pendingUser : null); }} open={Boolean(pendingUser)}>
        <DialogContent className="psq-glass-dialog max-h-[90dvh] overflow-y-auto rounded-[22px] sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle className="text-xl font-black">{restrictionTitle}</DialogTitle>
            <DialogDescription>{pendingUser ? `${displayName(pendingUser.user)} · ${pendingUser.kind === 'account_suspended' ? '此账号将无法登录或使用应用服务，成绩同步停止公开展示' : '成绩将从排行榜、求／给建议及谱面评级达成率中隐藏，无法发布求建议或建议评论'}` : ''}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <label className="block space-y-2 text-xs font-semibold text-muted-foreground">限制原因<RestrictionReasonField onChange={setRestrictionReason} value={restrictionReason} /></label>
            <div className="space-y-2">
              <p className="text-xs font-semibold text-muted-foreground">限制时长</p>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
                {durations.map((item) => <Button className="h-9 rounded-xl px-2 text-xs" key={item.label} onClick={() => setDuration(item.minutes)} variant={duration === item.minutes ? 'secondary' : 'outline'}>{item.label}</Button>)}
              </div>
              <label className="flex items-center gap-3 text-xs text-muted-foreground">自定义时长（分钟）<Input className="h-9 w-36 rounded-xl" min={1} max={129600} step={1} type="number" placeholder="留空为长期" value={duration ?? ''} onChange={(event) => setDuration(event.target.value ? Number(event.target.value) : null)} /></label>
              <p className="text-[11px] text-muted-foreground">选择长期后，限制持续到管理员手动解除。</p>
            </div>
            {restrictionError && <p className="text-sm text-destructive" role="alert">{restrictionError}</p>}
          </div>
          <DialogFooter className="gap-2 sm:gap-2">
            <Button className="rounded-xl" disabled={savingRestriction} onClick={() => setPendingUser(null)} variant="outline">取消</Button>
            <Button className="rounded-xl" disabled={savingRestriction || !restrictionReason.trim() || (duration !== null && (!Number.isInteger(duration) || duration < 1 || duration > 129600))} onClick={() => void saveRestriction()} variant={pendingUser?.kind === 'account_suspended' ? 'destructive' : 'default'}>{savingRestriction && <LoaderCircle className="animate-spin" />}{pendingUser?.kind === 'account_suspended' ? '确认暂停' : '确认限制'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog onOpenChange={(open) => { if (!open) setViewer(null); }} open={Boolean(viewer)}>
        <DialogContent className="psq-glass-dialog max-h-[90dvh] max-w-3xl rounded-2xl p-3 sm:p-5">
          <DialogHeader className="sr-only"><DialogTitle>申诉图片</DialogTitle><DialogDescription>查看用户提交的申诉材料</DialogDescription></DialogHeader>
          {viewer && <img alt="申诉图片" className="max-h-[78dvh] w-full rounded-xl object-contain" src={viewer} />}
        </DialogContent>
      </Dialog>
    </section>
  );
}

function ManagedUserCard({ user, index, disabled, working, error, onRestrict, onRemove }: {
  user: User;
  index: number;
  disabled: boolean;
  working: boolean;
  error?: string;
  onRestrict: (user: User, kind: RestrictionKind) => void;
  onRemove: (user: User, kind: RestrictionKind) => void;
}) {
  const name = displayName(user);
  return (
    <article className="psq-glass-subcard animate-in fade-in slide-in-from-bottom-1 rounded-2xl border p-4 duration-300 motion-reduce:animate-none sm:p-5" style={{ animationDelay: `${Math.min(index, 8) * 35}ms` }}>
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div className="flex min-w-0 items-center gap-3.5">
          <UserAvatar avatar={user.avatar} name={name} />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="max-w-full truncate text-sm font-bold" title={name}>{name}</h3>
              {user.publicHidden.active && <Badge className="border-amber-500/20 bg-amber-500/8 text-amber-700"><EyeOffIcon />禁止公开展示</Badge>}
              {user.accountSuspended.active && <Badge className="border-destructive/20 bg-destructive/8 text-destructive"><Ban className="mr-1 size-3" />暂停中</Badge>}
            </div>
            <p className="mt-1 truncate text-[11px] text-muted-foreground">@{user.alias || '未设置用户名'} · {user.userHash.slice(0, 8)}••••</p>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-2 xl:min-w-[390px]">
          <Metric label="RKS" value={user.rks.toFixed(2)} />
          <Metric label="课题模式" value={challengeModeLabel(user.challengeModeRank)} />
          <Metric label="排行榜" value={user.leaderboardRank === null ? ((user.publicHidden.active || user.accountSuspended.active) ? '已停止展示' : '未上榜') : `第 ${user.leaderboardRank} 名`} />
        </div>
      </div>
      {(user.publicHidden.active || user.accountSuspended.active) && <div className="mt-3 grid gap-2 sm:grid-cols-2">
        {user.publicHidden.active && <RestrictionSummary title="公开展示限制" restriction={user.publicHidden} />}
        {user.accountSuspended.active && <RestrictionSummary title="账户暂停" restriction={user.accountSuspended} />}
      </div>}
      <fieldset className="mt-4 flex flex-wrap gap-2 border-t border-primary/[0.07] pt-3 disabled:opacity-60" disabled={disabled}>
        <Button className="h-9 rounded-xl" onClick={() => onRestrict(user, 'public_hidden')} variant={user.publicHidden.active ? 'outline' : 'secondary'}>
          {user.publicHidden.active ? <ShieldCheck /> : <ShieldAlert />}
          {user.publicHidden.active ? '更新公开限制' : '停止公开展示'}
        </Button>
        {user.publicHidden.active && <Button className="h-9 rounded-xl" onClick={() => onRemove(user, 'public_hidden')} variant="ghost"><UnlockKeyhole />解除展示限制</Button>}
        <Button className="h-9 rounded-xl" onClick={() => onRestrict(user, 'account_suspended')} variant={user.accountSuspended.active ? 'outline' : 'destructive'}>
          {user.accountSuspended.active ? <ShieldCheck /> : <Ban />}
          {user.accountSuspended.active ? '更新暂停时长' : '暂停账户使用'}
        </Button>
        {user.accountSuspended.active && <Button className="h-9 rounded-xl" onClick={() => onRemove(user, 'account_suspended')} variant="ghost"><UnlockKeyhole />恢复账户</Button>}
        {working && <LoaderCircle aria-label="正在更新用户状态" className="size-4 self-center animate-spin text-primary" />}
      </fieldset>
      {error && <p className="mt-2 text-sm text-destructive" role="alert">{error}</p>}
    </article>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl border border-primary/[0.08] bg-background/50 px-2.5 py-2"><p className="text-[10px] text-muted-foreground">{label}</p><p className="mt-0.5 truncate text-sm font-bold tabular-nums">{value}</p></div>;
}

function RestrictionSummary({ title, restriction }: { title: string; restriction: Restriction }) {
  return <div className="rounded-xl border border-amber-500/10 bg-amber-500/[0.035] px-3 py-2 text-xs"><p className="font-semibold">{title} · {restriction.expiresAt ? `至 ${dateLabel(restriction.expiresAt)}` : '长期'}</p>{restriction.reason && <p className="mt-1 line-clamp-2 text-muted-foreground">{restriction.reason}</p>}</div>;
}

function EyeOffIcon() {
  return <Ban className="mr-1 size-3" />;
}

function RestrictionReasonField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <Textarea className="min-h-24 rounded-xl border-primary/12" maxLength={1000} onChange={(event) => onChange(event.target.value)} placeholder="说明采取限制的原因，用户将在客户端看到" value={value} />;
}
