import os from 'node:os';

import { sites } from '@openai/sites-vite-plugin';
import tailwindcss from '@tailwindcss/postcss';
import vinext from 'vinext';
import { defineConfig } from 'vite';
import hostingConfig from './.openai/hosting.json';

const SITE_CREATOR_PLACEHOLDER_DATABASE_ID =
  '00000000-0000-4000-8000-000000000000';

const { d1, r2 } = hostingConfig;

/**
 * IPv4 addresses a phone on the same network can use to reach this machine.
 * `vinext dev` binds to 0.0.0.0, so the console only needs to tell the phone
 * which address to open.
 *
 * Host-only adapter ranges (Hyper-V's 172.16/12 switch, WSL, VirtualBox) are
 * rejected by the phone, so real home/office ranges are ranked first and the
 * virtual ones are pushed to the back instead of being removed outright.
 */
function lanIpv4Addresses(): string[] {
  const preferred: string[] = [];
  const fallback: string[] = [];
  const collect = (address: string) => {
    const [first, second] = address.split('.').map(Number);
    const isVirtualSwitch = first === 172 && second >= 16 && second <= 31;
    (isVirtualSwitch ? fallback : preferred).push(address);
  };

  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries ?? []) {
      if (entry.family !== 'IPv4' || entry.internal) continue;
      if (entry.address.startsWith('169.254.')) continue;
      collect(entry.address);
    }
  }
  return [...preferred, ...fallback];
}

// macOS Seatbelt blocks FSEvents, so Codex previews need polling for HMR.
const isCodexSeatbeltSandbox = process.env.CODEX_SANDBOX === 'seatbelt';

const localBindingConfig = {
  main: 'vinext/server/fetch-handler',
  compatibility_flags: ['nodejs_compat'],
  d1_databases: d1
    ? [
        {
          binding: d1,
          database_name: 'site-creator-d1',
          database_id: SITE_CREATOR_PLACEHOLDER_DATABASE_ID,
        },
      ]
    : [],
  r2_buckets: r2
    ? [
        {
          binding: r2,
          bucket_name: 'site-creator-r2',
        },
      ]
    : [],
};

export default defineConfig(async () => {
  // Keep Wrangler and Miniflare state project-local. These are non-secret tool
  // settings; application environment belongs in ignored `.env*` files.
  process.env.WRANGLER_WRITE_LOGS ??= 'false';
  process.env.WRANGLER_LOG_PATH ??= '.wrangler/logs';
  process.env.MINIFLARE_REGISTRY_PATH ??= '.wrangler/registry';

  // Wrangler snapshots its log path while the Cloudflare plugin is imported.
  const { cloudflare } = await import('@cloudflare/vite-plugin');

  const lanHosts = lanIpv4Addresses();

  return {
    css: { postcss: { plugins: [tailwindcss()] } },
    // The console reads this global to show a phone the address to open.
    define: { __PSQ_LAN_HOSTS__: JSON.stringify(lanHosts) },
    server: {
      host: true,
      // Vite rejects requests whose Host header it does not recognise; the LAN
      // addresses are exactly the ones a phone will send.
      allowedHosts: lanHosts,
      ...(isCodexSeatbeltSandbox
        ? { watch: { useFsEvents: false, usePolling: true } }
        : {}),
    },
    plugins: [
      vinext(),
      sites(),
      cloudflare({
        viteEnvironment: { name: 'rsc', childEnvironments: ['ssr'] },
        config: localBindingConfig,
      }),
    ],
  };
});
