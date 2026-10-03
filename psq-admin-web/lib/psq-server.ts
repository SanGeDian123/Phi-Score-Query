const DEFAULT_PSQ_ORIGIN = 'https://api.plc-liangpi-cup.xyz';

export function readAdminToken(request: Request): string | null {
  const token = request.headers.get('x-admin-token')?.trim() ?? '';
  return token.length > 0 && token.length <= 1024 ? token : null;
}

export function jsonNoStore(data: unknown, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set('Cache-Control', 'no-store');
  headers.set('Content-Type', 'application/json; charset=utf-8');
  return new Response(JSON.stringify(data), { ...init, headers });
}

export async function fetchPsq(
  path: string,
  token: string,
  init: RequestInit = {},
): Promise<Response> {
  if (!path.startsWith('/api/v2/') && path !== '/health') {
    throw new Error('不允许的 PSQ API 路径');
  }
  const origin = (process.env.PSQ_API_ORIGIN ?? DEFAULT_PSQ_ORIGIN).replace(
    /\/$/,
    '',
  );
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  headers.set('X-Admin-Token', token);
  if (init.body) headers.set('Content-Type', 'application/json');

  return fetch(`${origin}${path}`, {
    ...init,
    headers,
    cache: 'no-store',
    redirect: 'manual',
    signal: AbortSignal.timeout(12_000),
  });
}

export async function readUpstreamJson<T>(response: Response): Promise<T> {
  const text = await response.text();
  if (!response.ok) {
    let detail = `PSQ 服务器返回 ${response.status}`;
    try {
      const problem = JSON.parse(text) as { detail?: string; title?: string };
      detail = problem.detail || problem.title || detail;
    } catch {
      // Keep the sanitized status-only fallback.
    }
    throw new PsqUpstreamError(detail, response.status);
  }
  return JSON.parse(text) as T;
}

export class PsqUpstreamError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export function apiError(error: unknown): Response {
  if (error instanceof PsqUpstreamError) {
    const status = error.status === 401 || error.status === 403 ? 401 : 502;
    return jsonNoStore({ error: error.message }, { status });
  }
  const message =
    error instanceof Error ? error.message : '连接 PSQ 服务器失败';
  return jsonNoStore({ error: message }, { status: 502 });
}
