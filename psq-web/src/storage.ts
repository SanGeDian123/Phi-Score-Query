const open = () => new Promise<IDBDatabase>((resolve, reject) => {
  const req = indexedDB.open('psq-web', 1);
  req.onupgradeneeded = () => req.result.createObjectStore('cache');
  req.onsuccess = () => resolve(req.result);
  req.onerror = () => reject(req.error);
});
export async function readCache<T>(key: string): Promise<T | null> {
  try { const db = await open(); return await new Promise<T | null>((resolve, reject) => { const req = db.transaction('cache').objectStore('cache').get(key); req.onsuccess = () => { db.close(); resolve(req.result ?? null); }; req.onerror = () => { db.close(); reject(req.error); }; }); } catch { return null; }
}
export async function writeCache(key: string, value: unknown) {
  try { const db = await open(); await new Promise<void>((resolve, reject) => { const tx = db.transaction('cache', 'readwrite'); tx.objectStore('cache').put(value, key); tx.oncomplete = () => { db.close(); resolve(); }; tx.onerror = () => { db.close(); reject(tx.error); }; }); } catch {}
}
export async function clearAccountCache(key: string) {
  try { const db = await open(); const tx = db.transaction('cache', 'readwrite'); const req = tx.objectStore('cache').openCursor(); req.onsuccess = () => { const c = req.result; if (c) { if (String(c.key).endsWith(':' + key)) c.delete(); c.continue(); } }; await new Promise<void>(resolve => { tx.oncomplete = () => { db.close(); resolve(); }; }); } catch {}
}
export function localRead<T>(key: string, fallback: T): T { try { return JSON.parse(localStorage.getItem('psq:' + key) || 'null') ?? fallback; } catch { return fallback; } }
export function localWrite(key: string, value: unknown) { try { localStorage.setItem('psq:' + key, JSON.stringify(value)); } catch {} }
