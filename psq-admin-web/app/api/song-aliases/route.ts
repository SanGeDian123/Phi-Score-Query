import {
  apiError,
  fetchPsq,
  jsonNoStore,
  PsqUpstreamError,
  readAdminToken,
  readUpstreamJson,
} from '@/lib/psq-server';

export const dynamic = 'force-dynamic';

const upstreamPath = '/api/v2/admin/songs/aliases';
const controlCharacters = /[\u0000-\u001f\u007f-\u009f]/u;

function aliasApiError(error: unknown): Response {
  if (error instanceof PsqUpstreamError) {
    return jsonNoStore({ error: error.message }, { status: error.status });
  }
  if (error instanceof SyntaxError) {
    return jsonNoStore({ error: '请求正文必须为有效的 JSON' }, { status: 400 });
  }
  return apiError(error);
}

export async function GET(request: Request): Promise<Response> {
  const token = readAdminToken(request);
  if (!token)
    return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  try {
    const response = await fetchPsq(upstreamPath, token);
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) {
    return aliasApiError(error);
  }
}

export async function PUT(request: Request): Promise<Response> {
  const token = readAdminToken(request);
  if (!token)
    return jsonNoStore({ error: '请输入管理员令牌' }, { status: 401 });

  try {
    const body: unknown = await request.json();
    if (body === null || typeof body !== 'object' || Array.isArray(body)) {
      return jsonNoStore({ error: '请检查曲目别名保存参数' }, { status: 422 });
    }
    const { songId, aliases, revision } = body as Record<string, unknown>;
    if (
      typeof songId !== 'string' ||
      !songId.trim() ||
      typeof revision !== 'string' ||
      !revision ||
      !Array.isArray(aliases) ||
      aliases.some((alias) => typeof alias !== 'string')
    ) {
      return jsonNoStore(
        { error: '请检查曲目 ID、别名列表和版本信息' },
        { status: 422 },
      );
    }
    if (aliases.length > 200) {
      return jsonNoStore(
        { error: '每首曲目最多保存 200 条别名' },
        { status: 422 },
      );
    }
    for (const alias of aliases as string[]) {
      if (controlCharacters.test(alias)) {
        return jsonNoStore(
          { error: '别名不能包含控制字符' },
          { status: 422 },
        );
      }
      const length = Array.from(alias.trim()).length;
      if (length < 1 || length > 128) {
        return jsonNoStore(
          { error: '每条别名须为 1–128 个字符' },
          { status: 422 },
        );
      }
    }
    const cleanAliases = (aliases as string[]).map((alias) => alias.trim());
    if (new Set(cleanAliases).size !== cleanAliases.length) {
      return jsonNoStore(
        { error: '同一曲目不能保存完全相同的别名' },
        { status: 422 },
      );
    }
    const response = await fetchPsq(upstreamPath, token, {
      method: 'PUT',
      body: JSON.stringify({
        songId: songId.trim(),
        aliases: cleanAliases,
        revision,
      }),
    });
    return jsonNoStore(await readUpstreamJson(response));
  } catch (error) {
    return aliasApiError(error);
  }
}
