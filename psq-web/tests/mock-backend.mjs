import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const catalog = JSON.parse(await readFile(new URL('../public/data/catalog.json', import.meta.url), 'utf8'));
const picture = await readFile(new URL('../public/icons/icon-512.png', import.meta.url));
const recordList = catalog.items.flatMap(s => ['EZ', 'HD', 'IN', 'AT'].flatMap(d => {
  const constant = s.chartConstants[d.toLowerCase()]; return constant >= 14 ? [{ songId: s.id, difficulty: d, chart_constant: constant, accuracy: 97.5, score: 977000, is_full_combo: false, rks: Math.pow(42.5 / 45, 2) * constant }] : [];
})).sort((a, b) => b.chart_constant - a.chart_constant).slice(0, 60);
recordList.slice(0, 6).forEach(r => Object.assign(r, { accuracy: 100, score: 1000000, is_full_combo: true, rks: r.chart_constant }));
const ap = recordList.slice(0, 3), best = [...recordList].sort((a, b) => b.rks - a.rks).slice(0, 27);
const save = { playerNickname: '本地验证账户', gradeCounts: { AT: { C: 30, FC: 6, P: 6 }, IN: { C: 30, FC: 0, P: 0 } }, save: { user: { avatar: 'Introduction' }, summaryParsed: { challenge_mode_rank: 413 }, updatedAt: new Date().toISOString(), game_record: {} }, rks: { totalRks: [...best, ...ap].reduce((n, r) => n + r.rks, 0) / 30, b30Charts: [...best, ...ap] } };
for (const record of recordList) (save.save.game_record[record.songId] ||= []).push(record);
save.save.game_record['unknown-test-song'] = [{ difficulty: 'AT', accuracy: 100, score: 1000000, chart_constant: 20 }];
const author = { nickname: '本地验证玩家', avatar: 'Introduction', rks: 16.4, challengeModeRank: 413 }, sampleSong = catalog.items.find(s => s.id === ap[0].songId);
export function createMockBackend() {
  const history = [], posts = [{ id: 'test-post', description: '本地验证：请给这份成绩一些建议。', author, createdAt: new Date().toISOString(), imageUrl: '/suggestion-media/test.png', comments: [], canDelete: true }], feedback = [];
  let signed = false, game = null, qrPoll = 0;
  const send = (res, status, data) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
  const server = createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost'), path = url.pathname, chunks = [];
      for await (const c of req) chunks.push(c); const raw = Buffer.concat(chunks), multipart = req.headers['content-type']?.startsWith('multipart/');
      const data = raw.length && !multipart ? JSON.parse(raw) : {};
      history.push({ path, search: url.search, method: req.method, headers: req.headers, data, raw });
      if (path === '/api/v2/auth/session/exchange') return send(res, String(data.sessionToken).startsWith('psq-local-test-') ? 200 : 401, { accessToken: 'mock-access', expiresIn: 3600, detail: '测试凭证无效' });
      if (path === '/api/v2/auth/session/refresh') return send(res, 200, { accessToken: 'mock-access', expiresIn: 3600 });
      if (path === '/api/v2/auth/session/logout') return send(res, 200, { ok: true });
      if (path === '/api/v2/auth/qrcode') return send(res, 200, { qrId: 'test-qr', qrcodeBase64: 'data:image/png;base64,' + picture.toString('base64'), verificationUrl: 'https://www.taptap.cn/account/device?code=test' });
      if (path === '/api/v2/auth/qrcode/test-qr/status') return send(res, 200, ++qrPoll >= 2 ? { status: 'Confirmed', sessionToken: 'psq-local-test-qr-session' } : { status: 'Pending', retryAfter: 3 });
      if (path === '/api/v2/songs/catalog') { if (req.headers['if-none-match']) { res.writeHead(304, { ETag: '"' + catalog.version + '"' }); return res.end(); } res.setHeader('ETag', '"' + catalog.version + '"'); return send(res, 200, catalog); }
      if (path === '/app-announcement/index.json') return send(res, 200, { items: [] });
      if (path === '/app-update/latest.json') return send(res, 200, { versionName: 'Pre-0.9.8.0', versionCode: 112, changelog: ['新增RKS趋势图，在概览页面点击RKS数字即可进入。'] });
      if (path === '/_ill/ill/slow-test.png') {
        res.writeHead(200, { 'Content-Type': 'image/png' });
        const chunk = Buffer.alloc(65536); res.write(chunk); let count = 0;
        const timer = setInterval(() => { res.write(chunk); if (++count >= 40) { clearInterval(timer); res.end(); } }, 5);
        res.once('close', () => clearInterval(timer)); return;
      }
      if (path.startsWith('/avatar/') && !/^\/avatar\/[a-f0-9]{64}\.png$/.test(path)) return send(res, 404, { detail: '头像文件名必须为哈希' });
      if (path.startsWith('/_ill/') || path.startsWith('/avatar/') || path.startsWith('/suggestion-media/')) { res.writeHead(200, { 'Content-Type': 'image/png' }); return res.end(picture); }
      if (path.startsWith('/api/v2/users/me/moderation/')) return send(res, 200, path.endsWith('/appeals') ? { id: 'test-appeal', status: 'pending' } : { publicHidden: false, accountSuspended: false });
      if (req.headers.authorization !== 'Bearer mock-access') return send(res, 401, { detail: '仅接受独立测试会话' });
      if (path === '/api/v2/rks/history') {
        const offset = Number(data.cursor || 0), limit = Math.min(200, Number(data.limit || 50)), total = 650;
        const items = Array.from({ length: Math.max(0, Math.min(limit, total - offset)) }, (_, i) => ({ rks: 16.8 - (offset + i) * .0008, rksJump: .0008, createdAt: new Date(Date.now() - (offset + i) * 7200000).toISOString() }));
        return send(res, 200, { items, total, currentRks: 16.8, peakRks: 16.8, hasMore: offset + limit < total, nextCursor: offset + limit < total ? String(offset + limit) : undefined });
      }
      if (path === '/api/v2/save') return send(res, 200, save);
      if (path === '/api/v2/songs/search') return send(res, 200, { items: url.searchParams.get('q') === '别名验证' ? [sampleSong] : catalog.items.filter(s => s.name.toLowerCase().includes((url.searchParams.get('q') || '').toLowerCase())) });
      if (path === '/api/v2/songs/achievement-rates') return send(res, 200, { songId: url.searchParams.get('song_id'), songName: '本地验证谱面', difficulty: url.searchParams.get('difficulty'), total: 100, rates: ['AP', 'FC', 'V', 'S', 'A', 'B', 'C', 'F'].map((grade, i) => ({ grade, count: i * 5, rate: i * .05 })), mine: { score: 977000, grade: 'V', exceededRate: .8, exceededCount: 80, topRate: .2 } });
      if (path === '/api/v2/leaderboard/rks/me') return send(res, 200, { rank: 42, score: save.rks.totalRks, percentile: 97.375, total: 1600 });
      if (path === '/api/v2/leaderboard/rks/top') { const offset = Number(url.searchParams.get('cursor') || url.searchParams.get('offset') || 0), limit = Number(url.searchParams.get('limit') || 1000); return send(res, 200, { total: 1600, nextCursor: String(offset + limit), items: Array.from({ length: Math.min(limit, 1600 - offset) }, (_, i) => ({ rank: offset + i + 1, nickname: '验证玩家 ' + (offset + i + 1), avatar: 'Introduction', score: 18 - (offset + i) / 5000, challengeModeRank: 413 })) }); }
      if (path === '/api/v2/checkin/leaderboard') return send(res, 200, { items: [{ rank: 1, nickname: '本地验证玩家', totalDays: 45 }] });
      if (path === '/api/v2/checkin') { if (req.method === 'POST') signed = true; const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date()); return send(res, 200, { serverDate: date, month: url.searchParams.get('month') || date.slice(0, 7), totalCoin: signed ? 145 : 120, totalDays: signed ? 6 : 5, dates: signed ? [date] : [], today: signed ? { date, coin: 25, luck: 87, quoteIndex: 0, chart: { songId: sampleSong.id, songName: sampleSong.name, difficulty: ap[0].difficulty, targetScore: 1000000, targetAccuracy: 100 } } : null }); }
      if (path.startsWith('/api/v2/image/')) { if (data.custom && (data.scores?.length !== 36 || url.searchParams.get('template') !== 'phi-plugin')) return send(res, 400, { detail: '必须提交 36 张谱面' }); res.writeHead(200, { 'Content-Type': 'image/png' }); return res.end(picture); }
      if (path === '/api/v2/games/rks-guess/leaderboard') return send(res, 200, { items: Array.from({ length: 5 }, (_, i) => ({ rank: i + 1, nickname: i === 0 ? '本地验证玩家' : '验证玩家 ' + (i + 1), totalWins: 10 - i, singleWins: 5 - i, publicWins: 5, isMe: i === 0 })), myWins: 10 });
      if (path.startsWith('/api/v2/games/rks-guess/')) {
        const clues = ap.slice(0, 2).map((r, i) => ({ ...r, chartConstant: r.chart_constant, slot: 'B' + (i + 1), songName: catalog.items.find(s => s.id === r.songId).name }));
        if (path.endsWith('/match')) game = { gameId: 'test-game', mode: data.mode, status: 'playing', role: 'A', myNickname: '本地验证账户', round: 1, totalRounds: 5, myScore: 0, opponentScore: 0, deadlineAtEpochMs: Date.now() + 60000, clues, clueHistory: [{ round: 1, clues }], opponentReady: true };
        if (!game) return send(res, 404, { detail: '游戏不存在' });
        if (path.endsWith('/answer')) { game.myAnswer = data.answer; game.status = game.round === 5 ? 'finished' : 'round_result'; game.myScore++; game.lastRound = { round: game.round, myAnswer: data.answer, myElapsedMs: 1200, outcome: 'win', resolution: 'target_tolerance' }; if (game.status === 'finished') Object.assign(game, { winner: 'A', targetRks: 16.08 }); }
        if (path.endsWith('/next') && game.status === 'round_result') { game.round++; game.status = 'playing'; game.myAnswer = null; game.deadlineAtEpochMs = Date.now() + 60000; game.clueHistory.push({ round: game.round, clues }); }
        if (path.endsWith('/leave')) { game = null; return send(res, 200, { ok: true }); } return send(res, 200, game);
      }
      if (path.startsWith('/api/v2/suggestions/')) {
        if (path.endsWith('/notifications')) return send(res, 200, { checkedAt: new Date().toISOString(), items: [] });
        if (path.endsWith('/mine') || path.endsWith('/commented')) return send(res, 200, posts);
        if (path.endsWith('/random')) return send(res, posts.length ? 200 : 404, posts[0] || { detail: '暂无其他求助' });
        const form = multipart ? await new Response(raw, { headers: { 'Content-Type': req.headers['content-type'] } }).formData() : null;
        if (path === '/api/v2/suggestions/posts' && req.method === 'POST') { const p = { id: 'test-post-' + posts.length, description: String(form.get('description')), author, createdAt: new Date().toISOString(), imageUrl: '/suggestion-media/test.png', comments: [], canDelete: true }; posts.push(p); return send(res, 200, p); }
        if (path.endsWith('/comments') && req.method === 'POST') { const p = posts.find(p => path.includes('/' + p.id + '/')), comment = { id: 'test-comment-' + p.comments.length, text: String(form.get('text')), author, createdAt: new Date().toISOString(), canDelete: true }; p.comments.push(comment); return send(res, 200, comment); }
        if (req.method === 'DELETE') { res.writeHead(204); return res.end(); }
        return send(res, 200, posts.find(p => path.endsWith('/' + p.id)) || posts[0]);
      }
      if (path.startsWith('/api/v2/feedback')) {
        if (path === '/api/v2/feedback' && req.method === 'POST') { if (data.images.some(i => i.startsWith('data:'))) return send(res, 400, { detail: '图片应为纯 Base64' }); const item = { ...data, status: 'pending', reply: '', revision: 1, readRevision: 1, imageCount: data.images.length, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }; feedback.unshift(item); return send(res, 200, item); }
        if (/\/images\//.test(path)) { res.writeHead(200, { 'Content-Type': 'image/png' }); return res.end(picture); }
        if (path.endsWith('/read')) return send(res, 200, true);
        return send(res, 200, feedback);
      }
      send(res, 404, { detail: '未定义测试接口' });
    } catch { send(res, 500, { detail: '测试请求无效' }); }
  });
  return { server, history };
}
if (process.argv[1] === fileURLToPath(import.meta.url)) { const { server } = createMockBackend(); const port = Number(process.env.PSQ_MOCK_PORT || 4511); server.listen(port, '127.0.0.1', () => console.log('PSQ isolated mock backend ready on 127.0.0.1:' + port)); }
