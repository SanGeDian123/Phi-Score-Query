import {
  apiError,
  fetchPsq,
  jsonNoStore,
  readAdminToken,
  readUpstreamJson,
} from '@/lib/psq-server';

export const dynamic = 'force-dynamic';
type Context = { params: Promise<{ path?: string[] }> };
const uuidPattern = /^[0-9a-f-]{36}$/i;

async function proxy(request: Request, context: Context): Promise<Response> {
  const token = readAdminToken(request);
  if (!token) return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });
  const { path = [] } = await context.params;
  const url = new URL(request.url);
  let upstreamPath: string;
  let isImage = false;

  if (path.length === 1 && path[0] === 'search' && request.method === 'GET') {
    const query = (url.searchParams.get('query') ?? '').trim();
    if (!query || Array.from(query).length > 40) {
      return jsonNoStore({ error: '请输入 1–40 个字符的用户名' }, { status: 422 });
    }
    upstreamPath = `/api/v2/admin/users/management/search?query=${encodeURIComponent(query)}`;
  } else if (path.length === 1 && path[0] === 'restricted' && request.method === 'GET') {
    const query = (url.searchParams.get('query') ?? '').trim();
    const restrictionType = url.searchParams.get('restrictionType') ?? 'all';
    const page = Number(url.searchParams.get('page') ?? '1');
    if (Array.from(query).length > 40 || !['all', 'public_hidden', 'account_suspended'].includes(restrictionType) || !Number.isSafeInteger(page) || page < 1 || page > 0xffffffff) {
      return jsonNoStore({ error: '请检查受限用户筛选条件' }, { status: 422 });
    }
    const params = new URLSearchParams({ query, restrictionType, page: String(page) });
    upstreamPath = `/api/v2/admin/users/management/restricted?${params}`;
  } else if (path.length === 1 && path[0] === 'appeals' && request.method === 'GET') {
    const status = url.searchParams.get('status') ?? 'pending';
    if (!['pending', 'reviewing', 'accepted', 'rejected', 'all'].includes(status)) {
      return jsonNoStore({ error: '申诉状态无效' }, { status: 400 });
    }
    upstreamPath = `/api/v2/admin/users/management/appeals?status=${encodeURIComponent(status)}`;
  } else if (path.length === 4 && path[0] === 'appeals' && uuidPattern.test(path[1]) && path[2] === 'images' && /^[0-2]$/.test(path[3]) && request.method === 'GET') {
    upstreamPath = `/api/v2/admin/users/management/appeals/${path[1]}/images/${path[3]}`;
    isImage = true;
  } else if (path.length === 1 && path[0] === 'restriction' && request.method === 'POST') {
    upstreamPath = '/api/v2/admin/users/management/restriction';
  } else if (path.length === 2 && path[0] === 'appeals' && uuidPattern.test(path[1]) && request.method === 'POST') {
    upstreamPath = `/api/v2/admin/users/management/appeals/${path[1]}`;
  } else {
    return jsonNoStore({ error: '不支持的用户管理操作' }, { status: 404 });
  }

  try {
    let body: string | undefined;
    if (request.method === 'POST') {
      const data = await request.json() as Record<string, unknown>;
      if (path[0] === 'restriction') {
        if (typeof data.userHash !== 'string' || !['public_hidden', 'account_suspended'].includes(String(data.restrictionType)) || typeof data.active !== 'boolean' || (data.reason !== undefined && typeof data.reason !== 'string') || (data.durationMinutes !== undefined && data.durationMinutes !== null && !Number.isSafeInteger(data.durationMinutes))) {
          return jsonNoStore({ error: '请检查用户限制参数' }, { status: 422 });
        }
      } else if (!['reviewing', 'accepted', 'rejected'].includes(String(data.status)) || (data.reply !== undefined && typeof data.reply !== 'string')) {
        return jsonNoStore({ error: '请检查申诉处理内容' }, { status: 422 });
      }
      body = JSON.stringify(data);
    }
    const response = await fetchPsq(upstreamPath, token, { method: request.method, body });
    if (isImage && response.ok) {
      return new Response(response.body, {
        headers: {
          'Content-Type': response.headers.get('Content-Type') || 'application/octet-stream',
          'Cache-Control': 'private, no-store',
          'X-Content-Type-Options': 'nosniff',
        },
      });
    }
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) {
    return apiError(error);
  }
}

export const GET = proxy;
export const POST = proxy;
