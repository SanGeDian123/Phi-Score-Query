import { validAvatarName } from './core';

export class ApiError extends Error {
  constructor(public status: number, message: string, public data: any = {}) { super(message); }
}
export async function request<T = any>(path: string, data?: unknown, method?: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, {
    method: method || (data === undefined ? 'GET' : 'POST'),
    credentials: 'same-origin',
    headers: { 'X-PSQ-Request': '1', ...(data !== undefined && !(data instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) },
    body: data === undefined ? undefined : data instanceof FormData ? data : JSON.stringify(data),
    cache: 'no-store',
    signal,
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    if (response.status === 401 && !path.startsWith('/api/auth/')) window.dispatchEvent(new Event('psq-auth-expired'));
    if (response.status === 403) window.dispatchEvent(new Event('psq-restricted'));
    throw new ApiError(response.status, problem.detail || problem.message || problem.title || ('请求失败：' + response.status), problem);
  }
  if (response.status === 204) return undefined as T;
  const content = await response.text();
  return (content ? JSON.parse(content) : undefined) as T;
}
export const api = <T = any>(path: string, data?: unknown, method?: string, signal?: AbortSignal) => request<T>('/api/backend/' + path, data, method, signal);
export const artwork = (id: string, full = false) => '/api/assets?path=' + encodeURIComponent('/_ill/' + (full ? 'ill' : 'illLow') + '/' + id + '.png');
export const avatarUrl = (value: unknown) => { const name = validAvatarName(value); return name ? '/api/avatar?name=' + encodeURIComponent(name) : ''; };
export function mediaUrl(value?: string) {
  if (!value) return '';
  try {
    const u = new URL(value, 'https://api.plc-liangpi-cup.xyz');
    if (u.origin !== 'https://api.plc-liangpi-cup.xyz' || !u.pathname.startsWith('/suggestion-media/')) return '';
    return '/api/media?path=' + encodeURIComponent(u.pathname);
  } catch { return ''; }
}
export async function imageRequest(path: string, data: unknown) {
  const response = await fetch('/api/backend/' + path, { method: 'POST', credentials: 'same-origin', headers: { 'X-PSQ-Request': '1', 'Content-Type': 'application/json' }, body: JSON.stringify(data), cache: 'no-store' });
  if (!response.ok) { const error = await response.json().catch(() => ({})); if (response.status === 401) window.dispatchEvent(new Event('psq-auth-expired')); if (response.status === 403) window.dispatchEvent(new Event('psq-restricted')); throw new ApiError(response.status, error.detail || error.message || '成绩图生成失败'); }
  return response.blob();
}
