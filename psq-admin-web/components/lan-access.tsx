'use client';

import { useCallback, useEffect, useState } from 'react';
import { Check, Copy, QrCode, RefreshCw, Smartphone, Wifi } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { accessCandidates } from '@/lib/lan';
import { qrCodeSvg } from '@/lib/qr-encoder';

type NetworkInfo = {
  urls: string[];
  port: string | null;
  openedLocally: boolean;
  hasLanAddress: boolean;
};

/** A QR code rendered as a data URI so any phone camera can scan it. */
function QrImage({ value, size = 220 }: { value: string; size?: number }) {
  const svg = qrCodeSvg(value, { scale: 4, quietZone: 4 });
  if (!svg) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- an inline SVG data URI needs no image optimisation.
    <img
      alt={`访问地址二维码：${value}`}
      className="rounded-2xl border border-primary/10 bg-white p-2 shadow-[0_10px_30px_rgb(23_63_122/10%)]"
      height={size}
      src={`data:image/svg+xml;utf8,${encodeURIComponent(svg)}`}
      width={size}
    />
  );
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  const copy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      // Clipboard access can be blocked; fall back to a temporary selection.
      const field = document.createElement('textarea');
      field.value = value;
      field.style.position = 'fixed';
      field.style.opacity = '0';
      document.body.appendChild(field);
      field.select();
      try {
        // Deprecated but the only fallback when the async clipboard is blocked
        // (for example on a plain http:// LAN origin).
        // eslint-disable-next-line @typescript-eslint/no-deprecated -- clipboard fallback.
        document.execCommand('copy');
      } catch {
        // Nothing else to try; the address stays visible on screen.
      }
      field.remove();
    }
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  }, [value]);

  return (
    <Button
      className="psq-glass-control h-9 shrink-0 rounded-xl px-3"
      onClick={() => void copy()}
      size="sm"
      type="button"
      variant="outline"
    >
      {copied ? <Check /> : <Copy />}
      {copied ? '已复制' : '复制'}
    </Button>
  );
}

/** Address list plus QR codes for the addresses a phone can actually open. */
function LanAccessBody({ network, preview }: { network: NetworkInfo; preview: boolean }) {
  const [showQr, setShowQr] = useState(false);
  const [primary, ...rest] = network.urls;
  if (!primary) return null;

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <code className="min-w-0 flex-1 truncate rounded-xl border border-primary/12 bg-white/70 px-3 py-2 text-[13px] font-semibold text-primary">
            {primary}
          </code>
          <CopyButton value={primary} />
        </div>
        {rest.length > 0 && (
          <details className="text-xs text-muted-foreground">
            <summary className="cursor-pointer select-none">还有其他 {rest.length} 个地址</summary>
            <ul className="mt-2 space-y-1.5">
              {rest.map((url) => (
                <li className="flex items-center gap-2" key={url}>
                  <code className="min-w-0 flex-1 truncate rounded-lg border border-primary/10 bg-white/60 px-2.5 py-1.5">
                    {url}
                  </code>
                  <CopyButton value={url} />
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          className="h-9 rounded-xl px-3"
          onClick={() => setShowQr((open) => !open)}
          size="sm"
          type="button"
          variant={showQr ? 'outline' : 'default'}
        >
          <QrCode />
          {showQr ? '收起二维码' : '显示二维码'}
        </Button>
        {preview && (
          <span className="text-[11px] text-muted-foreground">
            手机需与本机连接同一个 Wi-Fi。
          </span>
        )}
      </div>

      {showQr && (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-primary/10 bg-white/45 p-4">
          <QrImage value={primary} />
          <p className="text-[11px] text-muted-foreground">
            用手机相机或微信扫一扫打开
          </p>
        </div>
      )}
    </div>
  );
}

function useNetworkInfo() {
  const [network, setNetwork] = useState<NetworkInfo | null>(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    try {
      const response = await fetch('/api/network', { cache: 'no-store' });
      if (!response.ok) throw new Error(String(response.status));
      setNetwork((await response.json()) as NetworkInfo);
      setFailed(false);
    } catch {
      // Fall back to the address this page was opened with; the build-time
      // address list is included by `accessCandidates`.
      const urls = accessCandidates(window.location.hostname, window.location.port);
      setNetwork({
        urls,
        port: window.location.port,
        openedLocally: true,
        hasLanAddress: urls.length > 0,
      });
      setFailed(true);
    }
  }, []);

  useEffect(() => {
    // Reads an external system (the server's address list, then `window`) and
    // publishes it as state; there is no render-time source for it.
    // eslint-disable-next-line react-compiler/react-compiler -- state is set from a fetch callback, not synchronously.
    void load();
  }, [load]);

  return {
    info:
      network ??
      ({ urls: [], port: null, openedLocally: true, hasLanAddress: false } satisfies NetworkInfo),
    failed,
    reload: load,
  };
}

/** In-page card: shows the phone address, with one extra tap for the QR code. */
export function LanAccessCard() {
  const { info, failed, reload } = useNetworkInfo();

  if (!info.hasLanAddress) {
    return (
      <Card className="psq-glass-card mt-4 ring-0" id="lan-access">
        <CardHeader className="gap-1 px-5 sm:px-6">
          <CardTitle className="flex items-center gap-2 text-base font-bold">
            <Smartphone className="size-[18px] text-primary" />
            手机访问
          </CardTitle>
          <CardDescription>
            未检测到局域网地址。请确认电脑已连接 Wi-Fi 或网线，然后重新启动后台服务。
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="psq-glass-card mt-4 ring-0" id="lan-access">
      <CardHeader className="gap-1 px-5 sm:px-6">
        <CardTitle className="flex items-center gap-2 text-base font-bold">
          <Smartphone className="size-[18px] text-primary" />
          手机访问
          <Badge className="border-[#12a985]/20 bg-[#12a985]/8 text-[#087f65]" variant="outline">
            <Wifi className="size-3" />
            局域网
          </Badge>
        </CardTitle>
        <CardDescription className="leading-5">
          在手机上打开下面的地址即可查看同一份后台数据；也可扫二维码直接进入。
          管理员令牌不会随地址传递，手机上仍需输入一次。
        </CardDescription>
      </CardHeader>
      <CardContent className="px-5 sm:px-6">
        <LanAccessBody network={info} preview />
        {failed && (
          <button
            className="mt-3 inline-flex items-center gap-1.5 text-[11px] text-muted-foreground underline-offset-2 hover:underline"
            onClick={() => void reload()}
            type="button"
          >
            <RefreshCw className="size-3" />
            地址可能不是最新的，点击重新检测
          </button>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * Header/connect-dialog entry point: a compact button that opens the same
 * information, including the QR code, without leaving the current screen.
 */
export function LanAccessButton({
  className = '',
  label = '手机访问',
}: {
  className?: string;
  label?: string;
}) {
  const [open, setOpen] = useState(false);
  const { info, failed, reload } = useNetworkInfo();

  return (
    <>
      <Button
        className={`psq-glass-control rounded-xl ${className}`}
        onClick={() => setOpen(true)}
        size="sm"
        type="button"
        variant="outline"
      >
        <Smartphone />
        <span className="hidden sm:inline">{label}</span>
      </Button>
      {open && (
        <div className="psq-modal-overlay" role="presentation">
          <div
            className="psq-glass-dialog psq-modal-in relative w-full max-w-[460px] rounded-[22px] border p-5 sm:p-6"
            role="dialog"
            aria-label="手机访问"
          >
            <div className="flex items-start gap-3">
              <div className="grid size-11 shrink-0 place-items-center rounded-2xl bg-primary text-primary-foreground">
                <Smartphone className="size-5" />
              </div>
              <div className="min-w-0">
                <h2 className="text-lg font-black tracking-tight">手机访问后台</h2>
                <p className="mt-1 text-xs leading-5 text-muted-foreground">
                  手机连接同一个 Wi-Fi 后，打开下面的地址或直接扫码。
                  电脑需要保持这个后台窗口开启。
                </p>
              </div>
            </div>

            <div className="mt-5">
              {info.hasLanAddress ? (
                <LanAccessBody network={info} preview={false} />
              ) : (
                <p className="rounded-xl border border-dashed border-primary/15 px-4 py-6 text-center text-sm text-muted-foreground">
                  未检测到局域网地址，请检查电脑的网络连接后重新启动后台服务。
                </p>
              )}
            </div>

            {failed && (
              <button
                className="mt-3 inline-flex items-center gap-1.5 text-[11px] text-muted-foreground hover:underline"
                onClick={() => void reload()}
                type="button"
              >
                <RefreshCw className="size-3" />
                重新检测地址
              </button>
            )}

            <div className="mt-5 flex justify-end">
              <Button className="rounded-xl" onClick={() => setOpen(false)} type="button">
                知道了
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
