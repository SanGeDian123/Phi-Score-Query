const challengeColors = ['', '绿', '蓝', '红', '金', '彩'] as const;

export function challengeModeLabel(rank: number | null): string {
  if (rank === null || !Number.isInteger(rank) || rank <= 0) return '—';
  const color = challengeColors[Math.floor(rank / 100)];
  return color ? `${color}${String(rank % 100).padStart(2, '0')}` : '—';
}
