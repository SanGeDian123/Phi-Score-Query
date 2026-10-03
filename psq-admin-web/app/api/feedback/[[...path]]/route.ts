import { apiError, fetchPsq, jsonNoStore, readAdminToken, readUpstreamJson } from '@/lib/psq-server';

export const dynamic = 'force-dynamic';
type Context = { params: Promise<{ path?: string[] }> };

async function proxy(request: Request, context: Context): Promise<Response> {
  const token = readAdminToken(request);
  if (!token) return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });
  const { path = [] } = await context.params;
  const idPattern = /^[0-9a-f-]{36}$/i;
  const isImage = path.length === 3 && path[1] === 'images' && /^[0-2]$/.test(path[2]);
  if (path.length && (!idPattern.test(path[0]) || !(path.length === 1 || isImage))) {
    return jsonNoStore({ error: '无效的反馈路径' }, { status: 400 });
  }
  if ((request.method === 'POST' && path.length !== 1) || (request.method === 'GET' && path.length === 1)) {
    return jsonNoStore({ error: '不支持的操作' }, { status: 405 });
  }
  try {
    const offset = Number(new URL(request.url).searchParams.get('offset') || '0');
    if (!Number.isSafeInteger(offset) || offset < 0 || offset > 0xffffffff) return jsonNoStore({ error: '无效的分页参数' }, { status: 400 });
    let body: string | undefined;
    if (request.method === 'POST') {
      const data = await request.json() as { status?: unknown; reply?: unknown; revision?: unknown };
      if (!['pending', 'processing', 'resolved'].includes(String(data.status)) || typeof data.reply !== 'string' || data.reply.length > 4000 || !Number.isSafeInteger(data.revision)) {
        return jsonNoStore({ error: '请检查反馈状态及回复内容' }, { status: 422 });
      }
      body = JSON.stringify(data);
    }
    const response = await fetchPsq(`/api/v2/admin/feedback${path.length ? '/' + path.join('/') : '?offset=' + offset}`, token, { method: request.method, body });
    if (isImage && response.ok) {
      return new Response(response.body, { headers: { 'Content-Type': response.headers.get('Content-Type') || 'application/octet-stream', 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff' } });
    }
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) { return apiError(error); }
}
export const GET = proxy;
export const POST = proxy;
