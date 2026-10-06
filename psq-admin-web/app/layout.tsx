import type { Metadata, Viewport } from 'next';

import './globals.css';

export const metadata: Metadata = {
  title: 'PSQ Server Console',
  description: 'PSQ 服务器数据与公告管理控制台',
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_ORIGIN ?? 'https://api.plc-liangpi-cup.xyz',
  ),
  openGraph: {
    title: 'PSQ Server Console',
    description: 'PSQ 服务器数据与公告管理控制台',
    images: [{ url: '/og.png', width: 1200, height: 630, alt: 'PSQ Server Console' }],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'PSQ Server Console',
    description: 'PSQ 服务器数据与公告管理控制台',
    images: ['/og.png'],
  },
};

// Mobile Safari/Chrome must be told to use the device width; without this the
// page renders at a 980px fallback width and phones show a shrunken desktop
// layout. `viewportFit: cover` also lets safe-area padding work on notched
// phones.
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#f4f7fb',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
