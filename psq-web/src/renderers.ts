import { artwork } from './api';
import { difficulties, fmt, grade, type Score, type Song } from './core';
import { PRODUCT_VERSION } from './pwa';
import chapterBackgrounds from './chapter-backgrounds.json';
const version = 'Phi Score Query · ' + PRODUCT_VERSION;
const colors: Record<string, string> = { EZ: '#00cb67', HD: '#00bcef', IN: '#ff1616', AT: '#969696' };
async function loadImage(url: string) {
  const image = new Image(); image.decoding = 'async';
  await new Promise<void>((resolve, reject) => { const timer = setTimeout(() => { image.src = ''; reject(new Error('图片加载超时')); }, 12000); image.onload = () => { clearTimeout(timer); resolve(); }; image.onerror = () => { clearTimeout(timer); reject(new Error('图片加载失败')); }; image.src = url; }); return image;
}
function cover(c: CanvasRenderingContext2D, img: HTMLImageElement, x: number, y: number, w: number, h: number) {
  const scale = Math.max(w / img.width, h / img.height), sw = w / scale, sh = h / scale;
  c.drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, x, y, w, h);
}
function fitImage(c: CanvasRenderingContext2D, img: HTMLImageElement, x: number, y: number, w: number, h: number) {
  const scale = Math.min(w / img.width, h / img.height); c.drawImage(img, x + (w - img.width * scale) / 2, y + (h - img.height * scale) / 2, img.width * scale, img.height * scale);
}
function box(c: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, radius: number, color: string, stroke?: string) {
  c.beginPath(); c.roundRect(x, y, w, h, radius); c.fillStyle = color; c.fill(); if (stroke) { c.strokeStyle = stroke; c.lineWidth = 3; c.stroke(); }
}
function text(c: CanvasRenderingContext2D, value: string, x: number, y: number, size: number, color = '#fff', maxWidth = 9999, align: CanvasTextAlign = 'left') {
  c.font = size + 'px PSQ'; while (c.measureText(value).width > maxWidth && size > 15) c.font = --size + 'px PSQ'; c.fillStyle = color; c.textAlign = align; c.fillText(value, x, y); c.textAlign = 'left';
}
async function canvas(width: number, height: number) { await document.fonts.load('24px PSQ'); const el = document.createElement('canvas'); el.width = width; el.height = height; return { el, c: el.getContext('2d')! }; }
function blob(el: HTMLCanvasElement) { return new Promise<Blob>((resolve, reject) => el.toBlob(v => v ? resolve(v) : reject(new Error('图片导出失败')), 'image/png')); }
export function luckText(luck: number) {
  const choices = luck >= 85 ? ['长风破浪会有时', '春风得意马蹄疾', '扶摇直上九万里'] : luck >= 50 ? ['行到水穷处，坐看云起时', '一蓑烟雨任平生', '人间有味是清欢'] : ['山重水复，柳暗花明', '莫愁前路无知己', '千磨万击还坚劲']; return choices[luck % 3];
}
export async function renderCheckin(record: any) {
  const { el, c } = await canvas(1920, 1080);
  const [img, poems] = await Promise.all([record.chart ? loadImage(artwork(record.chart.songId, true)).catch(() => null) : Promise.resolve(null), fetch('/data/checkin-poems.json').then(r => r.json())]);
  c.fillStyle = '#1d1f22'; c.fillRect(0, 0, 1920, 1080);
  if (img) { c.save(); c.filter = 'blur(22px)'; cover(c, img, -35, -35, 1990, 1150); c.restore(); c.fillStyle = 'rgba(24,24,27,.604)'; c.fillRect(0, 0, 1920, 1080); }
  const [, month, day] = record.date.split('-');
  text(c, month, 68, 174, 91, '#fff', 127); c.strokeStyle = '#fff'; c.lineWidth = 7; c.lineCap = 'round'; c.beginPath(); c.moveTo(218, 96); c.lineTo(132, 326); c.stroke(); text(c, day, 170, 329, 91, '#fff', 151);
  text(c, '签到成功', 345, 164, 67, '#00bce6'); text(c, 'Sign Successful', 345, 213, 35); text(c, 'Coin + ' + record.coin, 345, 330, 64);
  text(c, '今日幸运值', 1060, 135, 40); text(c, String(record.luck), 1060, 270, 132); text(c, luckText(record.luck), 1060, 340, 42, record.luck >= 85 ? '#ffce23' : record.luck >= 50 ? '#fff' : '#b3b8bf', 760);
  text(c, '今日谱面', 110, 420, 43);
  c.save(); c.beginPath(); c.roundRect(110, 450, 850, 478.125, 62); c.clip(); if (img) cover(c, img, 110, 450, 850, 478.125); else { c.fillStyle = '#2a313b'; c.fillRect(110, 450, 850, 478.125); text(c, record.chart ? '曲绘暂未加载' : '今日暂无可推分谱面', 180, 650, 48, '#fff', 760); } c.restore();
  if (record.chart) { text(c, record.chart.songName + ' [' + record.chart.difficulty + ']', 1060, 545, 46, '#fff', 760); text(c, '宜推分', 1060, 625, 45); text(c, String(record.chart.targetScore), 1060, 735, 92); text(c, fmt(record.chart.targetAccuracy, 2) + '%', 1065, 790, 46); }
  text(c, poems[((record.quoteIndex % poems.length) + poems.length) % poems.length], 80, 979, 37, '#fff', 1760); text(c, record.date, 80, 1040, 23, '#ccc'); text(c, version, 1840, 1040, 23, '#ffffffbe', 900, 'right');
  return { blob: await blob(el), artworkMissing: !!record.chart && !img };
}
export async function renderSong(song: Song, records: Score[], style: string) {
  const modern = style !== 'legacy', { el, c } = await canvas(modern ? 2560 : 2025, modern ? 1440 : 963);
  const img = await loadImage(artwork(song.id, true)).catch(() => null);
  if (!img) throw new Error('曲绘暂时不可用，请重试');
  c.fillStyle = '#19191c'; c.fillRect(0, 0, el.width, el.height);
  if (modern) {
    cover(c, img, 0, 0, 2560, 1440); c.fillStyle = '#18181b9a'; c.fillRect(0, 0, 2560, 1440);
    c.save(); c.beginPath(); c.roundRect(72, 188, 1215, 684, 72); c.clip(); cover(c, img, 72, 188, 1215, 684); c.restore(); box(c, 72, 188, 1215, 684, 72, 'transparent', '#000');
    text(c, song.name, 94, 990, 82, '#fff', 1170); text(c, song.composer, 94, 1073, 47, '#fff', 1170);
    const ds = difficulties.filter(d => d !== 'AT' || song.chartConstants.at != null || records.some(r => r.difficulty === 'AT'));
    const w = (1215 - 27 * (ds.length - 1)) / ds.length;
    for (let i = 0; i < ds.length; i++) {
      const d = ds[i], constant = song.chartConstants[d.toLowerCase() as keyof Song['chartConstants']], r = records.find(r => r.difficulty === d), y = 36 + i * 353;
      box(c, 72 + i * (w + 27), 1110, w, 142, 29, '#111214bc'); text(c, d, 94 + i * (w + 27), 1153, 34, colors[d]); text(c, fmt(constant, 1), 72 + i * (w + 27) + w / 2, 1226, 54, '#fff', w - 20, 'center');
      box(c, 1355, y, 1133, 307, 43, '#0d0f12cd', '#000'); text(c, d, 1389, y + 64, 48, colors[d]);
      if (!r) { text(c, 'NO DATA', 1921.5, y + 194, 116, '#fff', 953, 'center'); continue; }
      text(c, String(r.score), 1389, y + 220, 103, '#fff', 420);
      if (r.pushAcc != null && r.score < 1000000 && r.pushAcc > r.accuracy + .0001) { text(c, fmt(r.accuracy, 2) + '%', 1907.5, y + 163, 34, '#fff', 215, 'center'); text(c, '→ ' + fmt(r.pushAcc, 2) + '%', 1907.5, y + 217, 39, '#ffbf00', 235, 'center'); }
      else text(c, fmt(r.accuracy, 2) + '%', 1907.5, y + 203, 42, r.score === 1000000 ? '#ffbf00' : '#fff', 215, 'center');
      const icon = await loadImage('/grades/grade_' + grade(r.score, r.isFullCombo).toLowerCase() + '.png'); fitImage(c, icon, 2030, y + 103, 200, 152);
      text(c, fmt(r.chartConstant ?? constant, 2), 2361.5, y + 135, 44, '#fff', 190, 'center'); text(c, '↓', 2361.5, y + 187, 40, '#fff', 190, 'center'); text(c, fmt(r.rks, 2), 2361.5, y + 239, 44, '#fff', 190, 'center');
    }
    text(c, version, 2490, 1425, 19, '#ffffffb9', 1100, 'right');
  } else {
    const normalized = (v: string) => v.toLowerCase().replace(/[^\p{L}\p{N}]/gu, ''), chapter = normalized(song.chapter || '');
    const aliases = chapter === 'single' ? ['单曲'] : chapter === 'sidestory4' ? ['extrastory'] : [];
    const selected = chapterBackgrounds.find(entry => (chapter && normalized(entry.name).includes(chapter)) || aliases.some(alias => normalized(entry.name).includes(alias))) || chapterBackgrounds.find(entry => entry.name === 'AllSong');
    const chapterImage = selected ? await loadImage(selected.url).catch(() => null) : null;
    c.save(); c.filter = 'blur(20px)'; cover(c, img, -30, -30, 2085, 1023); c.restore(); c.fillStyle = '#ffffff69'; c.fillRect(0, 0, 2025, 963);
    c.save(); c.translate(1012.5, 481.5); c.scale(1.12, 1.12); c.translate(-924.5, -555.5);
    c.fillStyle = '#000'; c.beginPath(); c.moveTo(125, 354); c.lineTo(927, 354); c.lineTo(904, 442); c.lineTo(104, 442); c.closePath(); c.fill();
    c.save(); c.beginPath(); c.moveTo(435, 301); c.lineTo(941, 301); c.lineTo(927, 357); c.lineTo(421, 357); c.closePath(); c.clip();
    if (chapterImage) { c.filter = 'blur(5px)'; cover(c, chapterImage, 412, 298, 533, 62); c.filter = 'none'; }
    else { c.fillStyle = '#5c5b5b'; c.fillRect(412, 298, 533, 62); }
    c.fillStyle = '#00000060'; c.fillRect(412, 298, 533, 62); c.restore();
    text(c, song.chapter || 'Chapter Name', 681, 338, 26, '#fff', 470, 'center'); text(c, song.name, 140, 393, 32, '#fff', 665); text(c, song.composer, 140, 425, 22, '#fff', 665); cover(c, img, 105, 442, 800, 369);
    const columns = [1012, 1094, 1203, 1332, 1537, 1665, 1745], sizes = [40, 35, 35, 35, 23, 24];
    for (let i = 0; i < 4; i++) {
      const d = difficulties[i], r = records.find(r => r.difficulty === d), y = [300, 438, 583, 722][i]; box(c, 1012, y, 733, 89, 17, '#fdfeffe0', '#0a2836');
      const vals = [d, fmt(song.chartConstants[d.toLowerCase() as keyof Song['chartConstants']], 1), String(song.charts?.[d]?.notes ?? ''), r ? r.score.toLocaleString('en-US') : '', r ? fmt(r.accuracy, 2) + '%' : '', r ? fmt(r.rks, 2) : ''];
      vals.forEach((v, j) => { text(c, v, (columns[j] + columns[j + 1]) / 2, y + 58, sizes[j], '#000', columns[j + 1] - columns[j] - 16, 'center'); if (j > 0) { c.strokeStyle = '#0a2836'; c.lineWidth = 2; c.beginPath(); c.moveTo(columns[j], y + 15); c.lineTo(columns[j], y + 74); c.stroke(); } });
    }
    c.restore(); text(c, version, 1970, 943, 15, '#1c2932af', 900, 'right');
  }
  return blob(el);
}
