import {
  apiError,
  fetchPsq,
  jsonNoStore,
  readAdminToken,
  readUpstreamJson,
} from '@/lib/psq-server';

export const dynamic = 'force-dynamic';

type RouteContext = {
  params: Promise<{ id: string }>;
};

async function announcementPath(context: RouteContext): Promise<string> {
  const { id } = await context.params;
  const cleanId = id.trim();
  if (!cleanId || cleanId.length > 128) throw new Error('公告 ID 无效');
  return `/api/v2/admin/announcements/${encodeURIComponent(cleanId)}`;
}

export async function PUT(
  request: Request,
  context: RouteContext,
): Promise<Response> {
  const token = readAdminToken(request);
  if (!token)
    return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  try {
    const body = (await request.json()) as { title?: unknown; body?: unknown };
    if (typeof body.title !== 'string' || typeof body.body !== 'string') {
      return jsonNoStore({ error: '公告标题和正文不能为空' }, { status: 422 });
    }
    const response = await fetchPsq(await announcementPath(context), token, {
      method: 'PUT',
      body: JSON.stringify({ title: body.title, body: body.body }),
    });
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) {
    return apiError(error);
  }
}

export async function DELETE(
  request: Request,
  context: RouteContext,
): Promise<Response> {
  const token = readAdminToken(request);
  if (!token)
    return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  try {
    const response = await fetchPsq(await announcementPath(context), token, {
      method: 'DELETE',
    });
    if (!response.ok) await readUpstreamJson(response);
    return new Response(null, {
      status: 204,
      headers: { 'Cache-Control': 'no-store' },
    });
  } catch (error) {
    return apiError(error);
  }
}
