import {
  apiError,
  fetchPsq,
  jsonNoStore,
  readAdminToken,
  readUpstreamJson,
} from '@/lib/psq-server';

export const dynamic = 'force-dynamic';

export async function GET(request: Request): Promise<Response> {
  const token = readAdminToken(request);
  if (!token) return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  try {
    const response = await fetchPsq('/api/v2/admin/announcement', token);
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) {
    return apiError(error);
  }
}

export async function POST(request: Request): Promise<Response> {
  const token = readAdminToken(request);
  if (!token) return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  try {
    const body = (await request.json()) as { title?: unknown; body?: unknown };
    if (typeof body.title !== 'string' || typeof body.body !== 'string') {
      return jsonNoStore({ error: '公告标题和正文不能为空' }, { status: 422 });
    }
    const response = await fetchPsq('/api/v2/admin/announcement', token, {
      method: 'POST',
      body: JSON.stringify({ title: body.title, body: body.body }),
    });
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) {
    return apiError(error);
  }
}
