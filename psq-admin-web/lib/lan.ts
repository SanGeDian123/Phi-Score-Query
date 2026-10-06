// LAN access helpers.
//
// `vinext dev` binds to 0.0.0.0, so the very same server is reachable from a
// phone as long as the console can tell the phone which address to open. The
// Node side knows the machine's LAN IPv4 addresses; the browser side only knows
// the address the current page was loaded from. The Vite config injects the
// Node list as a global (see `vite.config.ts`) so both sides share one source.

declare const __PSQ_LAN_HOSTS__: unknown;

/** IPv4 addresses of this machine on real (non-loopback) interfaces. */
export function lanAddresses(): string[] {
  try {
    const injected =
      typeof __PSQ_LAN_HOSTS__ === 'undefined' ? null : __PSQ_LAN_HOSTS__;
    if (Array.isArray(injected)) {
      return injected.filter(
        (item): item is string => typeof item === 'string' && item.length > 0,
      );
    }
  } catch {
    // The global is not defined in this environment (e.g. a plain build).
  }
  return [];
}

/** Loopback hosts mean "this computer only"; a phone can never use them. */
export function isLoopbackHost(hostname: string): boolean {
  const name = hostname.trim().toLowerCase().replace(/^\[|\]$/g, '');
  return (
    name === 'localhost' ||
    name === '::1' ||
    name === '0.0.0.0' ||
    name.startsWith('127.')
  );
}

/**
 * Addresses a phone on the same network can open: the address this page was
 * loaded from (when that is already a LAN one) plus every LAN IPv4 address of
 * the server machine.
 */
export function accessCandidates(
  currentHost: string | null | undefined,
  currentPort: string | null | undefined,
): string[] {
  const suffix = currentPort ? `:${currentPort}` : '';
  const candidates: string[] = [];
  if (currentHost && !isLoopbackHost(currentHost)) {
    candidates.push(`http://${currentHost}${suffix}`);
  }
  for (const address of lanAddresses()) {
    candidates.push(`http://${address}${suffix}`);
  }
  return [...new Set(candidates)];
}
