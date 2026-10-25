import { useEffect, useRef, useState } from 'react';
import { Download, KeyRound, LoaderCircle, QrCode, Share2 } from 'lucide-react';
import { request } from './api';
import { Modal, saveBlob } from './components';
import { usePSQ } from './state';
export default function Login({ install }: { install: () => void }) {
  const { authenticated } = usePSQ();
  const [tokenOpen, setTokenOpen] = useState(false), [token, setToken] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [qr, setQr] = useState<any>(null), [qrStatus, setQrStatus] = useState('等待扫码确认'), [png, setPng] = useState<Blob | null>(null);
  const operation = useRef(0);
  const qrSource = qr?.qrcodeBase64?.startsWith('data:image/') ? qr.qrcodeBase64 : qr?.qrcodeBase64 ? 'data:image/svg+xml;base64,' + qr.qrcodeBase64 : '';
  useEffect(() => {
    if (!qr?.qrId) return;
    let stopped = false, timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      let seconds = 3;
      try {
        const v = await request('/api/auth/qr/' + qr.qrId); if (stopped) return;
        if (v.loggedIn) { setQr(null); await authenticated(v); return; }
        const status = String(v.status).toLowerCase();
        if (['expired', 'failed', 'error'].includes(status)) { setError(v.message || '二维码已失效，请重新登录'); setQr(null); return; }
        setQrStatus(status === 'scanned' ? '已扫码，请在 TapTap 确认' : '等待扫码确认');
        seconds = Math.min(15, Math.max(2, Number(v.retryAfter) || 3));
      } catch (e) { if (stopped) return; setError((e as Error).message); if ((e as any).status === 400 || (e as any).status === 404) { setQr(null); return; } seconds = 5; }
      if (!stopped) timer = setTimeout(poll, seconds * 1000);
    };
    timer = setTimeout(poll, 2500); return () => { stopped = true; clearTimeout(timer); };
  }, [qr?.qrId]);
  useEffect(() => {
    setPng(null); if (!qrSource) return;
    let stopped = false; const image = new Image(); image.onload = () => { if (stopped) return; const c = document.createElement('canvas'); c.width = 600; c.height = 600; const ctx = c.getContext('2d')!; ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, 600, 600); ctx.drawImage(image, 30, 30, 540, 540); c.toBlob(v => { if (!stopped) setPng(v); }, 'image/png'); }; image.src = qrSource; return () => { stopped = true; };
  }, [qrSource]);
  const login = async () => { setBusy(true); setError(''); try { const state = await request('/api/auth/login', { sessionToken: token.trim() }); setToken(''); setTokenOpen(false); await authenticated(state); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } };
  const startQr = async () => { const id = ++operation.current; setBusy(true); setError(''); try { const data = await request('/api/auth/qr', {}); if (id === operation.current) { setQr(data); setQrStatus('等待扫码确认'); } } catch (e) { if (id === operation.current) setError((e as Error).message); } finally { if (id === operation.current) setBusy(false); } };
  const save = async (share = false) => { if (!png) return; try { await saveBlob(png, 'PSQ-TapTap.png', share); } catch (e) { if ((e as Error).name !== 'AbortError') setError('保存失败，请重试'); } };
  return <main className="login-page"><div className="login-content"><div className="phi-mark" aria-hidden="true">Φ</div><h1>Phi Score Query</h1><p className="login-caption">轻松查看你的 Phigros 成绩</p>{!qr ? <div className="login-actions"><button className="button primary" onClick={startQr} disabled={busy}><QrCode size={21} />使用 TapTap 扫码登录</button><button className="button outline" onClick={() => { setTokenOpen(true); setError(''); }} disabled={busy}><KeyRound size={20} />使用 SessionToken 登录</button></div> : <div className="qr-panel"><img className="qr-image" alt="TapTap 登录二维码" src={qrSource} /><div className="button-row equal"><button className="button primary" disabled={!png} onClick={() => save()}><Download size={17} />保存二维码</button><button className="button outline" disabled={!png} onClick={() => save(true)}><Share2 size={17} />分享</button></div><p className="accent">{qrStatus}</p><p className="muted small">保存二维码后前往 TapTap 扫描，或使用另一台设备扫描二维码。</p><button className="text-button" onClick={() => { operation.current++; setQr(null); setError(''); }}>取消</button></div>}{busy && <LoaderCircle className="spin accent" aria-label="正在连接" />}{error && !tokenOpen && <p className="error-message" role="alert">{error}</p>}<button className="text-button small install-link" onClick={install}>添加到主屏幕</button></div>{tokenOpen && <Modal title="SessionToken 登录" dismissDisabled={busy} onClose={() => { if (!busy) { setToken(''); setTokenOpen(false); } }}><form className="stack" onSubmit={e => { e.preventDefault(); login(); }}><label className="field"><span>SessionToken</span><input type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} placeholder="请输入 SessionToken" autoFocus /></label>{error && <p className="error-message" role="alert">{error}</p>}<div className="modal-actions"><button type="button" className="text-button" disabled={busy} onClick={() => { setToken(''); setTokenOpen(false); }}>取消</button><button className="button primary" disabled={busy || !token.trim()}>{busy ? '正在登录…' : '登录'}</button></div></form></Modal>}</main>;
}
