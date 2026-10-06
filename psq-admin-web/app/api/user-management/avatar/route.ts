import { createHash } from 'node:crypto';

export const dynamic = 'force-dynamic';

export async function GET(request: Request): Promise<Response> {
  const name = new URL(request.url).searchParams.get('name')?.trim() ?? '';
  if (!/^[\p{L}\p{N}_.-]{1,80}$/u.test(name)) {
    return new Response(null, { status: 400, headers: { 'Cache-Control': 'no-store' } });
  }
  const hash = createHash('sha256').update(name, 'utf8').digest('hex');
  const origin = (process.env.PSQ_API_ORIGIN ?? 'https://api.plc-liangpi-cup.xyz').replace(/\/$/, '');
  try {
    const response = await fetch(`${origin}/avatar/${hash}.png`, {
      cache: 'no-store',
      redirect: 'manual',
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok || !response.headers.get('content-type')?.startsWith('image/')) {
      return new Response(null, { status: response.status === 404 ? 404 : 502, headers: { 'Cache-Control': 'private, no-store' } });
    }
    return new Response(response.body, {
      headers: {
        'Content-Type': response.headers.get('content-type') ?? 'image/png',
        'Cache-Control': 'private, max-age=3600',
        'X-Content-Type-Options': 'nosniff',
      },
    });
  } catch {
    return new Response(null, { status: 502, headers: { 'Cache-Control': 'no-store' } });
  }
}
