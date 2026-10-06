import { isLoopbackHost, lanAddresses } from '@/lib/lan';

export const dynamic = 'force-dynamic';

/**
 * Network access information for this console.
 *
 * `vinext dev` listens on 0.0.0.0, so the only thing a phone needs is the
 * address of this machine on the local network. Those addresses are injected at
 * build time by `vite.config.ts` because a worker runtime cannot enumerate its
 * own interfaces.
 */
export function GET(request: Request): Response {
  const headerHost = request.headers.get('host') ?? '';
  const hostHeader = headerHost.split(',')[0].trim();
  const port = hostHeader.includes(':')
    ? hostHeader.slice(headerHost.lastIndexOf(':') + 1)
    : null;
  const suffix = port ? `:${port}` : '';

  const addresses = lanAddresses();
  const urls = addresses.map((address) => `http://${address}${suffix}`);
  const requestedHostname = hostHeader.replace(/:\d+$/, '').replace(/^\[|\]$/g, '');

  return Response.json(
    {
      urls,
      port,
      /** True when the console was opened on this computer itself. */
      openedLocally: requestedHostname ? isLoopbackHost(requestedHostname) : true,
      /** Machine-readable hint so the UI does not have to guess. */
      hasLanAddress: urls.length > 0,
    },
    { headers: { 'Cache-Control': 'no-store' } },
  );
}
