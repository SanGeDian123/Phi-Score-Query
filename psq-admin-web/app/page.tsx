'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity,
  BellRing,
  CalendarClock,
  Check,
  CircleCheck,
  Gauge,
  KeyRound,
  LayoutDashboard,
  ListFilter,
  LoaderCircle,
  LockKeyhole,
  LogOut,
  Pencil,
  Plus,
  Radio,
  RefreshCw,
  Send,
  Server,
  ShieldCheck,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import { Line, LineChart, Area, AreaChart, CartesianGrid, XAxis, YAxis } from 'recharts';

import { FeedbackPanel } from '@/components/feedback-panel';
import { LanAccessButton, LanAccessCard } from '@/components/lan-access';
import { UserManagementPanel } from '@/components/user-management';
import { Badge } from '@/components/ui/badge';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogMedia,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart';
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

type Announcement = {
  id: string;
  title: string;
  body: string;
  publishedAt: string;
};

type AnnouncementList = {
  items: Announcement[];
};

type PendingAnnouncementAction =
  | { kind: 'create'; title: string; body: string }
  | { kind: 'update'; id: string; title: string; body: string }
  | { kind: 'delete'; announcement: Announcement };

type DauRow = {
  date: string;
  activeUsers: number;
  activeIps: number;
};

type UserCountRow = {
  date: string;
  totalUsers: number;
};

type DashboardData = {
  todayCheckins?: number;
  dailyActiveCheckinRate?: number;
  totalCheckinRate?: number;
  checkinTrend: Array<{date: string; count: number}>;
  timezone: string;
  generatedAt: string;
  totalUsers: number;
  todayDau: number;
  yesterdayDau: number;
  dauChange: number | null;
  activeRate: number;
  requestSuccessRate: number;
  todayRequests: number;
  userCountTrend: UserCountRow[];
  dau: DauRow[];
  announcement: Announcement | null;
};

const chartConfig = {
  activeUsers: {
    label: '活跃用户',
    color: 'var(--psq-mint)',
  },
} satisfies ChartConfig;

const userCountChartConfig = {
  totalUsers: {
    label: '总用户数',
    color: 'var(--psq-blue)',
  },
} satisfies ChartConfig;

function number(value: number | undefined): string {
  return value === undefined ? '—' : value.toLocaleString('zh-CN');
}

function percent(value: number | undefined): string {
  return value === undefined ? '—' : `${value.toFixed(2)}%`;
}

function dateLabel(value: string): string {
  const [, month, day] = value.split('-');
  return month && day ? `${month}/${day}` : value;
}

function announcementTimestamp(value: string): number {
  const normalized = value
    .replace(/^(\d{4}-\d{2}-\d{2}) /, '$1T')
    .replace(/ ([+-]\d{2}:\d{2})$/, '$1');
  const parsed = Date.parse(normalized);
  return Number.isFinite(parsed) ? parsed : 0;
}

function sortAnnouncements(items: Announcement[]): Announcement[] {
  return [...items].sort(
    (left, right) =>
      announcementTimestamp(right.publishedAt) -
        announcementTimestamp(left.publishedAt) ||
      right.id.localeCompare(left.id),
  );
}

function announcementDateLabel(value: string): string {
  const timestamp = announcementTimestamp(value);
  if (!timestamp) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(timestamp);
}

function numericValue(value: string): number | null {
  const parsed = Number.parseFloat(value.replace(/[,%]/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
}

function AnimatedNumber({
  value,
  className = '',
}: {
  value: string;
  className?: string;
}) {
  const [current, setCurrent] = useState(value);
  const [incoming, setIncoming] = useState<string | null>(null);
  const [direction, setDirection] = useState<'up' | 'down'>('up');
  const latestValue = useRef(value);

  useEffect(() => {
    if (value === latestValue.current) return;

    const previous = latestValue.current;
    const previousNumber = numericValue(previous);
    const nextNumber = numericValue(value);
    setDirection(
      previousNumber !== null &&
        nextNumber !== null &&
        nextNumber < previousNumber
        ? 'down'
        : 'up',
    );
    setCurrent(previous);
    setIncoming(value);
    latestValue.current = value;

    const timer = window.setTimeout(() => {
      setCurrent(value);
      setIncoming(null);
    }, 420);
    return () => window.clearTimeout(timer);
  }, [value]);

  return (
    <span aria-live="polite" className={`psq-number-swap ${className}`}>
      <span
        className={
          incoming === null
            ? 'psq-number-value psq-number-static'
            : `psq-number-value psq-number-old psq-number-old-${direction}`
        }
      >
        {current}
      </span>
      {incoming !== null && (
        <span
          className={`psq-number-value psq-number-new psq-number-new-${direction}`}
        >
          {incoming}
        </span>
      )}
    </span>
  );
}

async function responseJson<T>(response: Response): Promise<T> {
  const payload = (await response.json()) as T & { error?: string };
  if (!response.ok) {
    throw new Error(payload.error || `请求失败（${response.status}）`);
  }
  return payload;
}

export default function Home() {
  const [token, setToken] = useState('');
  const [data, setData] = useState<DashboardData | null>(null);
  const [connected, setConnected] = useState(false);
  const [connectOpen, setConnectOpen] = useState(true);
  const [loading, setLoading] = useState(false);
  const [connectionError, setConnectionError] = useState('');
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [announcementsLoading, setAnnouncementsLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorMode, setEditorMode] = useState<'create' | 'edit'>('create');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftTitle, setDraftTitle] = useState('');
  const [draftBody, setDraftBody] = useState('');
  const [pendingAction, setPendingAction] =
    useState<PendingAnnouncementAction | null>(null);
  const [announcementBusy, setAnnouncementBusy] = useState(false);
  const loadingRef = useRef(false);
  const [announcementMessage, setAnnouncementMessage] = useState<{
    type: 'success' | 'error';
    text: string;
  } | null>(null);

  const chartData = useMemo(
    () =>
      (data?.dau ?? []).map((row) => ({
        ...row,
        label: dateLabel(row.date),
      })),
    [data],
  );

  const userCountChartData = useMemo(
    () =>
      (data?.userCountTrend ?? []).map((row) => ({
        ...row,
        label: dateLabel(row.date),
      })),
    [data],
  );

  const loadAnnouncements = useCallback(async (candidateToken: string) => {
    const cleanToken = candidateToken.trim();
    if (!cleanToken) return;
    setAnnouncementsLoading(true);
    try {
      const response = await fetch('/api/announcements', {
        headers: { 'X-Admin-Token': cleanToken },
        cache: 'no-store',
      });
      const result = await responseJson<AnnouncementList>(response);
      setAnnouncements(sortAnnouncements(result.items));
      setAnnouncementMessage(null);
    } catch (error) {
      setAnnouncementMessage({
        type: 'error',
        text: error instanceof Error ? error.message : '公告列表加载失败',
      });
    } finally {
      setAnnouncementsLoading(false);
    }
  }, []);

  const loadDashboard = useCallback(
    async (candidateToken: string, initializeForm: boolean) => {
      const cleanToken = candidateToken.trim();
      if (!cleanToken) {
        setConnectionError('请输入管理员令牌');
        return;
      }
      if (loadingRef.current) return;
      loadingRef.current = true;
      setLoading(true);
      setConnectionError('');
      try {
        const response = await fetch('/api/dashboard', {
          headers: { 'X-Admin-Token': cleanToken },
          cache: 'no-store',
        });
        const dashboard = await responseJson<DashboardData>(response);
        setData(dashboard);
        setToken(cleanToken);
        setConnected(true);
        setConnectOpen(false);
        if (initializeForm) void loadAnnouncements(cleanToken);
      } catch (error) {
        setConnectionError(
          error instanceof Error ? error.message : '连接 PSQ 服务器失败',
        );
        if (!connected) setConnectOpen(true);
      } finally {
        loadingRef.current = false;
        setLoading(false);
      }
    },
    [connected, loadAnnouncements],
  );

  useEffect(() => {
    if (!connected || !token) return;
    const timer = window.setInterval(() => {
      void loadDashboard(token, false);
    }, 5_000);
    return () => window.clearInterval(timer);
  }, [connected, loadDashboard, token]);

  function disconnect() {
    setToken('');
    setData(null);
    setAnnouncements([]);
    setConnected(false);
    setAnnouncementMessage(null);
    setEditorOpen(false);
    setPendingAction(null);
    setConnectOpen(true);
  }

  function openCreateAnnouncement() {
    setEditorMode('create');
    setEditingId(null);
    setDraftTitle('');
    setDraftBody('');
    setEditorOpen(true);
  }

  function openEditAnnouncement(announcement: Announcement) {
    setEditorMode('edit');
    setEditingId(announcement.id);
    setDraftTitle(announcement.title);
    setDraftBody(announcement.body);
    setEditorOpen(true);
  }

  function requestSaveAnnouncement() {
    const title = draftTitle.trim();
    const body = draftBody.trim();
    if (!title || !body) return;
    setPendingAction(
      editorMode === 'edit' && editingId
        ? { kind: 'update', id: editingId, title, body }
        : { kind: 'create', title, body },
    );
    setEditorOpen(false);
  }

  function cancelPendingAction() {
    const action = pendingAction;
    setPendingAction(null);
    if (action && action.kind !== 'delete') setEditorOpen(true);
  }

  async function executeAnnouncementAction() {
    const action = pendingAction;
    if (!action || announcementBusy) return;
    setAnnouncementBusy(true);
    setAnnouncementMessage(null);
    try {
      if (action.kind === 'delete') {
        const response = await fetch(
          `/api/announcements/${encodeURIComponent(action.announcement.id)}`,
          {
            method: 'DELETE',
            headers: { 'X-Admin-Token': token },
          },
        );
        if (!response.ok) await responseJson<{ error?: string }>(response);
        setAnnouncements((current) =>
          current.filter((item) => item.id !== action.announcement.id),
        );
        setAnnouncementMessage({
          type: 'success',
          text: `已删除公告“${action.announcement.title}”`,
        });
      } else {
        const isUpdate = action.kind === 'update';
        const response = await fetch(
          isUpdate
            ? `/api/announcements/${encodeURIComponent(action.id)}`
            : '/api/announcements',
          {
            method: isUpdate ? 'PUT' : 'POST',
            headers: {
              'Content-Type': 'application/json',
              'X-Admin-Token': token,
            },
            body: JSON.stringify({ title: action.title, body: action.body }),
          },
        );
        const saved = await responseJson<Announcement>(response);
        setAnnouncements((current) =>
          sortAnnouncements(
            isUpdate
              ? current.map((item) => (item.id === saved.id ? saved : item))
              : [saved, ...current],
          ),
        );
        setData((current) =>
          !current
            ? current
            : {
                ...current,
                announcement:
                  isUpdate && current.announcement?.id !== saved.id
                    ? current.announcement
                    : saved,
              },
        );
        setAnnouncementMessage({
          type: 'success',
          text: isUpdate
            ? `公告“${saved.title}”已更新`
            : `公告“${saved.title}”已发布并收纳到历史记录`,
        });
      }
      setPendingAction(null);
      setDraftTitle('');
      setDraftBody('');
      setEditingId(null);
    } catch (error) {
      setAnnouncementMessage({
        type: 'error',
        text:
          error instanceof Error ? error.message : '公告操作失败，请稍后重试',
      });
      setPendingAction(null);
      if (action.kind !== 'delete') setEditorOpen(true);
    } finally {
      setAnnouncementBusy(false);
    }
  }

  const metrics = [
    { label: '今日签到人数', value: number(data?.todayCheckins), note: '北京时间 · 每人每日一次', icon: CircleCheck, color: 'text-[#12a985]', glow: 'bg-[#12a985]/9' },
    { kind: 'checkin-rates' as const },
    {
      label: '总用户数',
      value: number(data?.totalUsers),
      note: '',
      icon: Users,
      color: 'text-[#315f9d]',
      glow: 'bg-[#315f9d]/9',
    },
    {
      label: '今日日活',
      value: number(data?.todayDau),
      note:
        data?.dauChange == null
          ? '较昨日暂无对比'
          : `${data.dauChange >= 0 ? '+' : ''}${data.dauChange.toFixed(1)}% 较昨日`,
      icon: Activity,
      color: 'text-[#087f65]',
      glow: 'bg-[#12a985]/9',
    },
    {
      label: '用户活跃率',
      value: percent(data?.activeRate),
      note: '',
      icon: Gauge,
      color: 'text-[#7656a8]',
      glow: 'bg-[#9a72cc]/9',
    },
    {
      label: '今日请求数',
      value: number(data?.todayRequests),
      note: '',
      icon: Server,
      color: 'text-[#315f9d]',
      glow: 'bg-[#315f9d]/9',
    },
    {
      label: '请求成功率',
      value: percent(data?.requestSuccessRate),
      note: '',
      icon: ShieldCheck,
      color: 'text-[#a56b0b]',
      glow: 'bg-[#e9a734]/10',
    },
  ];

  const canSaveAnnouncement =
    connected &&
    draftTitle.trim().length > 0 &&
    draftBody.trim().length > 0 &&
    draftTitle.length <= 120 &&
    draftBody.length <= 8000;

  return (
    <main className="relative min-h-screen overflow-hidden bg-background text-foreground">
      <div aria-hidden="true" className="psq-ambient">
        <span className="psq-orb psq-orb-one" />
        <span className="psq-orb psq-orb-two" />
        <span className="psq-grid-glow" />
      </div>

      <div className="relative mx-auto grid min-h-screen max-w-[1680px] lg:grid-cols-[248px_minmax(0,1fr)]">
        <aside className="psq-glass-sidebar hidden border-r px-5 py-6 lg:flex lg:flex-col">
          <div className="flex items-center gap-3 px-2">
            <div className="grid size-10 place-items-center rounded-xl bg-primary text-[17px] font-black italic text-primary-foreground shadow-[0_10px_24px_rgb(23_63_122/18%)]">
              P
            </div>
            <div>
              <p className="text-sm font-black tracking-[0.14em]">PSQ</p>
              <p className="mt-0.5 text-[11px] text-muted-foreground">
                SERVER CONSOLE
              </p>
            </div>
          </div>

          <nav aria-label="主导航" className="mt-10 space-y-1.5">
            <Button
              className="h-11 w-full justify-start gap-3 bg-primary/8 px-3 text-primary hover:bg-primary/12"
              onClick={() =>
                document
                  .getElementById('dashboard')
                  ?.scrollIntoView({ behavior: 'smooth' })
              }
              variant="ghost"
            >
              <LayoutDashboard />
              数据总览
            </Button>
            <Button
              className="h-11 w-full justify-start gap-3 px-3 text-muted-foreground hover:bg-primary/6 hover:text-primary"
              onClick={() =>
                document
                  .getElementById('announcement')
                  ?.scrollIntoView({ behavior: 'smooth' })
              }
              variant="ghost"
            >
              <BellRing />
              公告管理
            </Button>
            <Button variant="ghost" className="h-11 w-full justify-start gap-3 px-3 text-muted-foreground" onClick={() => document.getElementById('feedback')?.scrollIntoView({ behavior: 'smooth' })}><Send />用户反馈</Button>
            <Button variant="ghost" className="h-11 w-full justify-start gap-3 px-3 text-muted-foreground" onClick={() => document.getElementById('user-management')?.scrollIntoView({ behavior: 'smooth' })}><Users />用户管理</Button>
          </nav>

          <div className="mt-auto rounded-2xl border border-primary/10 bg-primary/[0.035] p-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-primary">
              <CircleCheck className="size-4" />
              管理连接已加密
            </div>
            <p className="mt-2 text-[11px] leading-5 text-muted-foreground">
              管理员令牌仅保留在当前页面内存中，关闭页面后自动清除。
            </p>
          </div>
        </aside>

        <section className="min-w-0">
          <header className="psq-glass-topbar top-0 z-20 flex h-[72px] items-center justify-between border-b px-4 sm:px-7 lg:px-9">
            <div className="flex items-center gap-3 lg:hidden">
              <div className="grid size-9 place-items-center rounded-xl bg-primary text-sm font-black italic text-primary-foreground">
                P
              </div>
              <span className="text-sm font-black tracking-[0.12em]">
                PSQ CONSOLE
              </span>
            </div>
            <div className="hidden items-center gap-2 text-xs text-muted-foreground lg:flex">
              <Radio className="size-4 text-primary" />
              管理控制台
              <span className="text-primary/20">/</span>
              数据总览
            </div>
            <div className="flex items-center gap-2">
              <Badge
                className={
                  connected
                    ? 'h-7 border-[#12a985]/20 bg-[#12a985]/7 px-2.5 text-[#087f65]'
                    : 'h-7 border-primary/10 bg-white/60 px-2.5 text-muted-foreground'
                }
                variant="outline"
              >
                <span
                  className={
                    connected
                      ? 'psq-status-dot size-1.5 rounded-full bg-[#12a985]'
                      : 'size-1.5 rounded-full bg-muted-foreground/50'
                  }
                />
                {connected ? '服务器在线' : '等待连接'}
              </Badge>
              <Button
                aria-label="刷新数据"
                className="psq-glass-control size-9 rounded-xl"
                disabled={!connected || loading}
                onClick={() => {
                  void loadDashboard(token, false);
                  void loadAnnouncements(token);
                }}
                size="icon"
                variant="outline"
              >
                <RefreshCw className={loading ? 'animate-spin' : ''} />
              </Button>
              {connected ? (
                <Button
                  className="psq-glass-control h-9 shrink-0 rounded-xl px-3"
                  onClick={disconnect}
                  variant="outline"
                >
                  <LogOut />
                  <span className="hidden sm:inline">断开连接</span>
                </Button>
              ) : (
                <Button
                  className="h-9 shrink-0 rounded-xl px-3"
                  onClick={() => setConnectOpen(true)}
                >
                  <KeyRound />
                  <span className="hidden sm:inline">连接服务器</span>
                </Button>
              )}
              <LanAccessButton />
            </div>
          </header>

          {/* Phones keep all three sections one tap away. */}
          <nav
            aria-label="页面导航"
            className="psq-glass-topbar sticky top-[72px] z-10 border-b lg:hidden"
          >
            <div className="flex items-center gap-1.5 overflow-x-auto px-4 py-2 sm:px-7">
              {[
                { id: 'dashboard', label: '总览', icon: LayoutDashboard },
                { id: 'announcement', label: '公告', icon: BellRing },
                { id: 'user-management', label: '用户管理', icon: Users },
                { id: 'feedback', label: '反馈', icon: Send },
              ].map(({ id, label, icon: Icon }) => (
                <Button
                  className="h-9 shrink-0 rounded-xl px-3.5 text-xs"
                  key={id}
                  onClick={() =>
                    document
                      .getElementById(id)
                      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                  }
                  variant="ghost"
                >
                  <Icon className="size-4" />
                  {label}
                </Button>
              ))}
              <span className="ml-auto shrink-0 pl-2">
                <LanAccessButton className="border-primary/15 bg-primary/6 text-primary" />
              </span>
            </div>
          </nav>

          <div className="px-4 py-6 sm:px-7 lg:px-9 lg:py-8" id="dashboard">
            <div className="psq-rise flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="mb-2 flex items-center gap-2">
                  <span className="text-[11px] font-bold uppercase tracking-[0.16em] text-primary">
                    Overview
                  </span>
                  <Badge
                    className="psq-glass-control border-primary/10 text-muted-foreground"
                    variant="outline"
                  >
                    {connected ? '实时数据' : '等待连接'}
                  </Badge>
                </div>
                <h1 className="text-2xl font-black tracking-tight sm:text-[30px]">
                  数据总览
                </h1>
                <p className="mt-1.5 text-sm text-muted-foreground">
                  实时掌握用户增长、活跃趋势与 PSQ 服务状态。
                </p>
              </div>
              <p className="text-xs text-muted-foreground">
                {data
                  ? `更新于 ${new Date(data.generatedAt).toLocaleTimeString('zh-CN', { hour12: false })} · ${data.timezone}`
                  : '数据时区 · Asia/Shanghai'}
              </p>
            </div>

            <div className="psq-rise psq-rise-delay-1 mt-6 grid grid-cols-2 gap-3 xl:grid-cols-4">
              {metrics.map((metric, index) => {
                if ('kind' in metric) {
                  return (
                    <Card
                      className="psq-glass-card col-span-2 ring-0 xl:col-span-1"
                      key={metric.kind}
                      style={{ animationDelay: `${90 + index * 55}ms` }}
                    >
                      <CardContent className="p-4 sm:p-5">
                        <div className="flex items-center gap-3">
                          <div className="psq-icon-float grid size-10 shrink-0 place-items-center rounded-xl bg-[#9a72cc]/9 text-[#9a72cc]">
                            <CalendarClock className="size-[18px]" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-[11px] font-semibold tracking-[0.08em] text-muted-foreground">
                              今日签到率
                            </p>
                            <p className="mt-1 text-[10px] text-muted-foreground">
                              北京时间 · 每人每日一次
                            </p>
                          </div>
                        </div>
                        <div className="mt-4 grid grid-cols-2 divide-x divide-border/70">
                          <div className="min-w-0 pr-3">
                            <p className="text-[10px] font-semibold text-muted-foreground sm:text-[11px]">
                              日活签到率
                            </p>
                            <AnimatedNumber
                              className="mt-1 whitespace-nowrap text-[clamp(16px,1.55vw,22px)] font-black tracking-[-0.025em] tabular-nums"
                              value={percent(data?.dailyActiveCheckinRate)}
                            />
                          </div>
                          <div className="min-w-0 pl-3">
                            <p className="text-[10px] font-semibold text-muted-foreground sm:text-[11px]">
                              总签到率
                            </p>
                            <AnimatedNumber
                              className="mt-1 whitespace-nowrap text-[clamp(16px,1.55vw,22px)] font-black tracking-[-0.025em] tabular-nums"
                              value={percent(data?.totalCheckinRate)}
                            />
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  );
                }
                const Icon = metric.icon;
                return (
                  <Card
                    className="psq-lift psq-glass-card py-0 ring-0"
                    key={metric.label}
                    style={{ animationDelay: `${90 + index * 55}ms` }}
                  >
                    <CardContent className="p-4 sm:p-5">
                      <div className="flex items-start justify-between gap-3">
                        <div
                          className={`psq-icon-float grid size-10 place-items-center rounded-xl ${metric.glow} ${metric.color}`}
                        >
                          <Icon className="size-[19px]" />
                        </div>
                        {metric.note && (
                          <span className="pt-1 text-right text-[11px] leading-4 text-muted-foreground">
                            {metric.note}
                          </span>
                        )}
                      </div>
                      <p className="mt-4 text-[11px] font-semibold tracking-[0.08em] text-muted-foreground sm:mt-5">
                        {metric.label}
                      </p>
                      <AnimatedNumber
                        className="mt-1 text-[24px] font-black tracking-[-0.025em] tabular-nums sm:text-[27px]"
                        value={metric.value}
                      />
                    </CardContent>
                  </Card>
                );
              })}
            </div>

            <Card className="psq-glass-card mt-4 ring-0">
              <CardHeader><CardTitle>签到人数</CardTitle><CardDescription>近 14 日 · 北京时间 · 每人每日一次。上方卡片分别按日活用户数与总用户数计算签到率。</CardDescription></CardHeader>
              <CardContent>
                {data?.checkinTrend?.length ? <ChartContainer className="h-[220px] w-full sm:h-[260px]" config={{count: {label: '签到人数',color: 'var(--psq-mint)'}}}>
                  <LineChart data={data.checkinTrend} margin={{left: 0,right: 12,top: 12,bottom: 0}}>
                    <CartesianGrid vertical={false}/><XAxis dataKey="date" minTickGap={18} tickFormatter={dateLabel}/><YAxis allowDecimals={false} width={34}/>
                    <ChartTooltip content={<ChartTooltipContent/>}/><Line type="monotone" dataKey="count" stroke="var(--psq-mint)" strokeWidth={2} dot={{r:3}}/>
                  </LineChart>
                </ChartContainer> : <p className="py-12 text-center text-sm text-muted-foreground">{connected ? '签到统计暂不可用，请先升级后端' : '连接服务器后查看签到趋势'}</p>}
              </CardContent>
            </Card>
            <div className="psq-rise psq-rise-delay-2 mt-4 grid items-start gap-4 xl:grid-cols-[minmax(0,1.55fr)_minmax(360px,.75fr)]">
              <Card className="psq-glass-card ring-0">
                <CardHeader className="gap-1 px-5 sm:px-6">
                  <CardTitle className="text-base font-bold">
                    每日活跃用户
                  </CardTitle>
                  <CardDescription>近 14 日去重活跃用户趋势</CardDescription>
                </CardHeader>
                <CardContent className="relative px-2 pb-2 sm:px-4">
                  {chartData.length > 0 ? (
                    <ChartContainer
                      className="h-[240px] w-full sm:h-[300px]"
                      config={chartConfig}
                    >
                      <AreaChart
                        data={chartData}
                        margin={{ left: 2, right: 10, top: 22, bottom: 0 }}
                      >
                        <defs>
                          <linearGradient
                            id="dau-fill"
                            x1="0"
                            x2="0"
                            y1="0"
                            y2="1"
                          >
                            <stop
                              offset="0%"
                              stopColor="var(--color-activeUsers)"
                              stopOpacity={0.32}
                            />
                            <stop
                              offset="100%"
                              stopColor="var(--color-activeUsers)"
                              stopOpacity={0.015}
                            />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 7" vertical={false} />
                        <XAxis
                          axisLine={false}
                          dataKey="label"
                          minTickGap={25}
                          tickLine={false}
                          tickMargin={12}
                        />
                        <YAxis
                          axisLine={false}
                          domain={[0, 'dataMax + 10']}
                          tickLine={false}
                          tickMargin={8}
                          width={34}
                        />
                        <ChartTooltip
                          content={<ChartTooltipContent indicator="line" />}
                          cursor={{
                            stroke: 'var(--psq-mint)',
                            strokeOpacity: 0.24,
                          }}
                        />
                        <Area
                          animationDuration={1050}
                          animationEasing="ease-out"
                          dataKey="activeUsers"
                          fill="url(#dau-fill)"
                          stroke="var(--color-activeUsers)"
                          strokeWidth={2.5}
                          type="monotone"
                        />
                      </AreaChart>
                    </ChartContainer>
                  ) : (
                    <div className="psq-glass-empty grid h-[240px] place-items-center rounded-xl sm:h-[300px]">
                      <div className="text-center">
                        <Activity className="mx-auto size-7 text-primary/35" />
                        <p className="mt-3 text-sm font-semibold">
                          连接后显示活跃趋势
                        </p>
                        <p className="mt-1 text-[11px] text-muted-foreground">
                          数据直接来自 PSQ 每日 DAU 聚合
                        </p>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card className="psq-glass-card xl:col-start-1 ring-0">
                <CardHeader className="gap-1 px-5 sm:px-6">
                  <CardTitle className="text-base font-bold">
                    总用户数趋势
                  </CardTitle>
                  <CardDescription>
                    近 14 日排行榜已登记用户累计数量
                  </CardDescription>
                </CardHeader>
                <CardContent className="relative px-2 pb-2 sm:px-4">
                  {userCountChartData.length > 0 ? (
                    <ChartContainer
                      className="h-[240px] w-full sm:h-[300px]"
                      config={userCountChartConfig}
                    >
                      <AreaChart
                        data={userCountChartData}
                        margin={{ left: 2, right: 10, top: 22, bottom: 0 }}
                      >
                        <defs>
                          <linearGradient
                            id="user-count-fill"
                            x1="0"
                            x2="0"
                            y1="0"
                            y2="1"
                          >
                            <stop
                              offset="0%"
                              stopColor="var(--color-totalUsers)"
                              stopOpacity={0.3}
                            />
                            <stop
                              offset="100%"
                              stopColor="var(--color-totalUsers)"
                              stopOpacity={0.015}
                            />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 7" vertical={false} />
                        <XAxis
                          axisLine={false}
                          dataKey="label"
                          minTickGap={25}
                          tickLine={false}
                          tickMargin={12}
                        />
                        <YAxis
                          axisLine={false}
                          domain={[0, 'dataMax + 10']}
                          tickLine={false}
                          tickMargin={8}
                          width={38}
                        />
                        <ChartTooltip
                          content={<ChartTooltipContent indicator="line" />}
                          cursor={{
                            stroke: 'var(--psq-blue)',
                            strokeOpacity: 0.24,
                          }}
                        />
                        <Area
                          animationDuration={1050}
                          animationEasing="ease-out"
                          dataKey="totalUsers"
                          fill="url(#user-count-fill)"
                          stroke="var(--color-totalUsers)"
                          strokeWidth={2.5}
                          type="monotone"
                        />
                      </AreaChart>
                    </ChartContainer>
                  ) : (
                    <div className="psq-glass-empty grid h-[240px] place-items-center rounded-xl sm:h-[300px]">
                      <div className="text-center">
                        <Users className="mx-auto size-7 text-primary/35" />
                        <p className="mt-3 text-sm font-semibold">
                          连接后显示用户增长趋势
                        </p>
                        <p className="mt-1 text-[11px] text-muted-foreground">
                          数据直接来自排行榜登记记录
                        </p>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card
                className="psq-glass-card xl:col-start-2 xl:row-span-2 xl:row-start-1 ring-0"
                id="announcement"
              >
                <CardHeader className="flex flex-col gap-3 px-4 sm:flex-row sm:items-start sm:justify-between sm:px-6">
                  <div className="flex min-w-0 items-start gap-3">
                    <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-[#315f9d]/9 text-[#315f9d]">
                      <ListFilter className="size-[18px]" />
                    </div>
                    <div className="min-w-0">
                      <CardTitle className="text-base font-bold">
                        公告管理
                      </CardTitle>
                      <CardDescription className="mt-1">
                        {connected
                          ? `共 ${announcements.length} 条公告，按发布时间最新优先`
                          : '连接后查看和快捷编辑全部 APP 公告'}
                        {announcementsLoading && announcements.length > 0 && (
                          <span className="ml-2 inline-flex items-center gap-1 text-primary">
                            <LoaderCircle className="size-3 animate-spin" />
                            刷新中
                          </span>
                        )}
                      </CardDescription>
                    </div>
                  </div>
                  <Button
                    className="h-10 shrink-0 rounded-xl px-3.5 font-bold sm:self-center"
                    disabled={!connected || announcementsLoading}
                    onClick={openCreateAnnouncement}
                  >
                    <Plus />
                    新增公告
                  </Button>
                </CardHeader>
                <CardContent className="px-4 sm:px-6">
                  {announcementMessage && (
                    <div
                      aria-live="polite"
                      className={
                        announcementMessage.type === 'success'
                          ? 'mb-3 flex items-start gap-2 rounded-xl border border-[#12a985]/15 bg-[#12a985]/7 px-3 py-2.5 text-xs text-[#087f65]'
                          : 'mb-3 flex items-start gap-2 rounded-xl border border-destructive/15 bg-destructive/6 px-3 py-2.5 text-xs text-destructive'
                      }
                    >
                      {announcementMessage.type === 'success' ? (
                        <Check className="mt-0.5 size-3.5 shrink-0" />
                      ) : (
                        <X className="mt-0.5 size-3.5 shrink-0" />
                      )}
                      <span className="min-w-0 break-all">
                        {announcementMessage.text}
                      </span>
                    </div>
                  )}

                  {announcementsLoading && announcements.length === 0 ? (
                    <div aria-label="正在加载公告" className="space-y-3">
                      {[0, 1, 2].map((item) => (
                        <div
                          className="psq-glass-empty h-[116px] animate-pulse rounded-xl"
                          key={item}
                        />
                      ))}
                    </div>
                  ) : !connected ? (
                    <div className="psq-glass-empty grid min-h-[310px] place-items-center rounded-xl border-dashed px-5 text-center">
                      <div>
                        <BellRing className="mx-auto size-7 text-primary/35" />
                        <p className="mt-3 text-sm font-semibold">
                          连接后管理公告
                        </p>
                        <p className="mt-1 text-xs leading-5 text-muted-foreground">
                          历史公告会按发布时间统一收纳在这里
                        </p>
                      </div>
                    </div>
                  ) : announcements.length === 0 ? (
                    <div className="psq-glass-empty grid min-h-[310px] place-items-center rounded-xl border-dashed px-5 text-center">
                      <div>
                        <BellRing className="mx-auto size-7 text-primary/35" />
                        <p className="mt-3 text-sm font-semibold">暂无公告</p>
                        <p className="mt-1 text-xs leading-5 text-muted-foreground">
                          发布第一条公告后，APP 会将它显示在公告页面中
                        </p>
                        <Button
                          className="mt-4 rounded-xl"
                          onClick={openCreateAnnouncement}
                          variant="outline"
                        >
                          <Plus />
                          新增公告
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="max-h-[650px] space-y-2.5 overflow-y-auto pr-1">
                      {announcements.map((announcement, index) => (
                        <article
                          className="psq-announcement-item psq-glass-subcard rounded-xl p-3.5"
                          key={announcement.id}
                          style={{
                            animationDelay: `${Math.min(index, 8) * 35}ms`,
                          }}
                        >
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-center gap-2">
                                {index === 0 && (
                                  <Badge className="border-[#12a985]/15 bg-[#12a985]/8 text-[#087f65]">
                                    最新
                                  </Badge>
                                )}
                                <h3 className="min-w-0 break-words text-sm font-bold">
                                  {announcement.title}
                                </h3>
                              </div>
                              <p className="mt-1.5 flex items-center gap-1.5 text-xs text-muted-foreground">
                                <CalendarClock className="size-3.5 shrink-0" />
                                {announcementDateLabel(
                                  announcement.publishedAt,
                                )}
                              </p>
                            </div>
                            <div className="flex shrink-0 items-center gap-1">
                              <Button
                                aria-label={`编辑公告：${announcement.title}`}
                                className="size-10 rounded-xl"
                                onClick={() =>
                                  openEditAnnouncement(announcement)
                                }
                                size="icon"
                                variant="ghost"
                              >
                                <Pencil />
                              </Button>
                              {index > 0 && (
                                <Button
                                  aria-label={`删除公告：${announcement.title}`}
                                  className="size-10 rounded-xl text-destructive hover:bg-destructive/8 hover:text-destructive"
                                  onClick={() =>
                                    setPendingAction({
                                      kind: 'delete',
                                      announcement,
                                    })
                                  }
                                  size="icon"
                                  variant="ghost"
                                >
                                  <Trash2 />
                                </Button>
                              )}
                            </div>
                          </div>
                          <p className="mt-2 line-clamp-3 whitespace-pre-wrap break-words text-xs leading-5 text-muted-foreground">
                            {announcement.body}
                          </p>
                        </article>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            <UserManagementPanel token={token} connected={connected} />

            <FeedbackPanel token={token} connected={connected} />

            <LanAccessCard />

            <footer className="psq-rise psq-rise-delay-3 mt-6 flex flex-col gap-2 border-t border-primary/[0.075] pt-5 text-[11px] text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
              <span>PSQ Server Console · 项目组内部管理</span>
              <span>统计口径：DAU / 排行榜已登记用户</span>
            </footer>
          </div>
        </section>
      </div>

      {connectOpen && (
        <div className="psq-modal-overlay">
          <form
            className="psq-glass-dialog psq-modal-in relative w-full max-w-[430px] rounded-[22px] border p-6 sm:p-7"
            onSubmit={(event) => {
              event.preventDefault();
              void loadDashboard(token, true);
            }}
          >
            <div className="flex items-start justify-between gap-4">
              <div className="grid size-12 place-items-center rounded-2xl bg-primary text-primary-foreground shadow-[0_12px_30px_rgb(23_63_122/22%)]">
                <LockKeyhole className="size-5" />
              </div>
              {connected && (
                <Button
                  aria-label="关闭连接窗口"
                  onClick={() => setConnectOpen(false)}
                  size="icon"
                  type="button"
                  variant="ghost"
                >
                  <X />
                </Button>
              )}
            </div>
            <h2 className="mt-6 text-xl font-black tracking-tight">
              连接 PSQ 服务器
            </h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              使用现有的{' '}
              <span className="font-semibold text-foreground">
                X-Admin-Token
              </span>{' '}
              读取统计数据并管理公告。
            </p>

            <label
              className="mt-6 block space-y-2 text-xs font-semibold text-muted-foreground"
              htmlFor="admin-token"
            >
              管理员令牌
              <Input
                autoComplete="off"
                className="psq-glass-control h-11 border-primary/15 px-3"
                id="admin-token"
                onChange={(event) => setToken(event.target.value)}
                placeholder="输入管理员令牌"
                type="password"
                value={token}
              />
            </label>

            {connectionError && (
              <div className="mt-4 flex items-start gap-2 rounded-xl border border-destructive/15 bg-destructive/6 px-3 py-2.5 text-xs text-destructive">
                <X className="mt-0.5 size-3.5 shrink-0" />
                {connectionError}
              </div>
            )}

            <Button
              className="psq-primary-button mt-5 h-11 w-full rounded-xl font-bold"
              disabled={loading}
              type="submit"
            >
              {loading ? <LoaderCircle className="animate-spin" /> : <Server />}
              {loading ? '正在连接…' : '连接并加载数据'}
            </Button>
            <div className="mt-4 flex items-center justify-center gap-2 text-[11px] text-muted-foreground">
              <ShieldCheck className="size-3.5 text-[#087f65]" />
              令牌不会写入本地存储
            </div>
          </form>
        </div>
      )}

      <Dialog
        onOpenChange={(open) => {
          if (!announcementBusy) setEditorOpen(open);
        }}
        open={editorOpen}
      >
        <DialogContent
          className="psq-glass-dialog max-h-[calc(100dvh-2rem)] max-w-2xl gap-5 overflow-y-auto rounded-[22px] border p-5 sm:p-6"
          showCloseButton={!announcementBusy}
        >
          <DialogHeader>
            <div className="mb-2 grid size-11 place-items-center rounded-xl bg-primary/8 text-primary">
              {editorMode === 'create' ? (
                <Plus className="size-5" />
              ) : (
                <Pencil className="size-5" />
              )}
            </div>
            <DialogTitle className="text-lg font-black">
              {editorMode === 'create' ? '新增 APP 公告' : '编辑 APP 公告'}
            </DialogTitle>
            <DialogDescription className="leading-6">
              {editorMode === 'create'
                ? '新公告会收纳到历史记录，并成为 APP 当前展示的最新公告。'
                : '修改会保留原公告 ID 和发布时间，已读状态不会被重置。'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <label
              className="block space-y-2 text-xs font-semibold text-muted-foreground"
              htmlFor="announcement-title"
            >
              公告标题
              <Input
                className="psq-glass-control h-11 border-primary/15 text-foreground"
                disabled={announcementBusy}
                id="announcement-title"
                maxLength={120}
                onChange={(event) => setDraftTitle(event.target.value)}
                placeholder="输入公告标题"
                value={draftTitle}
              />
            </label>
            <label
              className="block space-y-2 text-xs font-semibold text-muted-foreground"
              htmlFor="announcement-body"
            >
              公告正文
              <Textarea
                className="psq-glass-control min-h-[220px] resize-y border-primary/15 text-sm leading-6 text-foreground"
                disabled={announcementBusy}
                id="announcement-body"
                maxLength={8000}
                onChange={(event) => setDraftBody(event.target.value)}
                placeholder="输入公告正文"
                value={draftBody}
              />
            </label>
            <div className="flex items-center justify-between text-[11px] text-muted-foreground">
              <span>保存前需要二次确认</span>
              <span>{draftBody.length} / 8000</span>
            </div>
          </div>

          <DialogFooter className="-mx-5 -mb-5 px-5 sm:-mx-6 sm:-mb-6 sm:px-6">
            <Button
              disabled={announcementBusy}
              onClick={() => setEditorOpen(false)}
              variant="outline"
            >
              取消
            </Button>
            <Button
              className="font-bold"
              disabled={!canSaveAnnouncement || announcementBusy}
              onClick={requestSaveAnnouncement}
            >
              <Send />
              检查并保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog
        onOpenChange={(open) => {
          if (!open && !announcementBusy) cancelPendingAction();
        }}
        open={pendingAction !== null}
      >
        <AlertDialogContent className="psq-glass-dialog max-w-md rounded-[22px] border p-5">
          <AlertDialogHeader>
            <AlertDialogMedia
              className={
                pendingAction?.kind === 'delete'
                  ? 'bg-destructive/8 text-destructive'
                  : 'bg-primary/8 text-primary'
              }
            >
              {pendingAction?.kind === 'delete' ? (
                <Trash2 className="size-5" />
              ) : (
                <BellRing className="size-5" />
              )}
            </AlertDialogMedia>
            <AlertDialogTitle className="text-lg font-black">
              {pendingAction?.kind === 'delete'
                ? '确认删除这条公告？'
                : pendingAction?.kind === 'update'
                  ? '确认保存公告修改？'
                  : '确认发布这条公告？'}
            </AlertDialogTitle>
            <AlertDialogDescription className="leading-6">
              {pendingAction?.kind === 'delete'
                ? '删除后无法恢复，这条历史公告将从 APP 公告页面中移除。最新公告为保持未读弹窗逻辑稳定，不能直接删除。'
                : pendingAction?.kind === 'update'
                  ? '修改会同步到 APP 公告历史，公告 ID 与发布时间保持不变。'
                  : '发布后将收纳到公告历史，并成为最新公告；尚未读取新 ID 的 APP 用户仍只弹出这一条最新未读公告。'}
            </AlertDialogDescription>
          </AlertDialogHeader>

          {pendingAction && (
            <div className="psq-glass-subcard max-h-52 overflow-auto rounded-xl p-3 text-left text-xs">
              <p className="font-bold text-foreground">
                {pendingAction.kind === 'delete'
                  ? pendingAction.announcement.title
                  : pendingAction.title}
              </p>
              {pendingAction.kind !== 'delete' && (
                <p className="mt-2 whitespace-pre-wrap break-words leading-5 text-muted-foreground">
                  {pendingAction.body}
                </p>
              )}
            </div>
          )}

          <AlertDialogFooter className="-mx-5 -mb-5 px-5">
            <AlertDialogCancel disabled={announcementBusy}>
              {pendingAction?.kind === 'delete' ? '取消' : '返回修改'}
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={announcementBusy}
              onClick={() => void executeAnnouncementAction()}
              variant={
                pendingAction?.kind === 'delete' ? 'destructive' : 'default'
              }
            >
              {announcementBusy ? (
                <LoaderCircle className="animate-spin" />
              ) : pendingAction?.kind === 'delete' ? (
                <Trash2 />
              ) : (
                <Send />
              )}
              {pendingAction?.kind === 'delete' ? '确认删除' : '确认保存'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  );
}
