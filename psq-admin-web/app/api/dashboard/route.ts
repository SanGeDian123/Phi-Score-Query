import {
  apiError,
  fetchPsq,
  jsonNoStore,
  readAdminToken,
  readUpstreamJson,
} from '@/lib/psq-server';

export const dynamic = 'force-dynamic';

type DashboardResponse = {
  checkinTrend?: Array<{ date: string; count: number; totalUsers: number; rate: number }>;
  timezone: string;
  totalUsers: number;
  userCountTrend: Array<{ date: string; totalUsers: number }>;
  dau: Array<{ date: string; activeUsers: number; activeIps: number }>;
  httpTotals: Array<{
    date: string;
    total: number;
    errors: number;
    errorRate: number;
    serverErrors: number;
    serverErrorRate: number;
  }>;
};

type Announcement = {
  id: string;
  title: string;
  body: string;
  publishedAt: string;
};

function shanghaiDate(): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function offsetDate(date: string, offsetDays: number): string {
  const parsed = new Date(`${date}T00:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + offsetDays);
  return parsed.toISOString().slice(0, 10);
}

export async function GET(request: Request): Promise<Response> {
  const token = readAdminToken(request);
  if (!token)
    return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  const today = shanghaiDate();
  const start = offsetDate(today, -13);
  const range = `start=${start}&end=${today}&timezone=Asia%2FShanghai`;

  try {
    const [dashboardResponse, announcementResponse] = await Promise.all([
      fetchPsq(`/api/v2/admin/dashboard?${range}`, token),
      fetchPsq('/api/v2/admin/announcement', token),
    ]);

    const dashboard =
      await readUpstreamJson<DashboardResponse>(dashboardResponse);
    const announcement = announcementResponse.ok
      ? await readUpstreamJson<Announcement | null>(announcementResponse)
      : announcementResponse.status === 404
        ? null
        : await readUpstreamJson<Announcement | null>(announcementResponse);

    const todayDau =
      dashboard.dau.find((row) => row.date === today)?.activeUsers ?? 0;
    const yesterday = offsetDate(today, -1);
    const yesterdayDau =
      dashboard.dau.find((row) => row.date === yesterday)?.activeUsers ?? 0;
    const todayHttp = dashboard.httpTotals.find((row) => row.date === today);
    const activeRate =
      dashboard.totalUsers > 0 ? (todayDau / dashboard.totalUsers) * 100 : 0;
    const dauChange =
      yesterdayDau > 0
        ? ((todayDau - yesterdayDau) / yesterdayDau) * 100
        : null;
    const requestSuccessRate = todayHttp
      ? Math.max(0, (1 - todayHttp.serverErrorRate) * 100)
      : 100;

    return jsonNoStore({
      checkinTrend: dashboard.checkinTrend ?? [],
      todayCheckins: dashboard.checkinTrend?.find((r) => r.date === today)?.count,
      checkinRate: dashboard.checkinTrend?.find((r) => r.date === today)?.rate,
      timezone: dashboard.timezone,
      generatedAt: new Date().toISOString(),
      totalUsers: dashboard.totalUsers,
      userCountTrend: dashboard.userCountTrend ?? [],
      todayDau,
      yesterdayDau,
      dauChange,
      activeRate,
      requestSuccessRate,
      todayRequests: todayHttp?.total ?? 0,
      dau: dashboard.dau,
      announcement,
    });
  } catch (error) {
    return apiError(error);
  }
}
