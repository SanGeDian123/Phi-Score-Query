import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Dices, LoaderCircle, RefreshCw, Trophy } from 'lucide-react';
import { api } from '../api';
import { Art, Confirm, Loading, PageHeader, Progress } from '../components';
import { fmt, type Song } from '../core';
import { usePSQ } from '../state';
import { localRead, localWrite } from '../storage';
import { reducedMotion, useContentMotion } from '../motion';

const singleRules = '游戏开始时，每回合随机给出2条目标玩家的B30成绩，玩家需根据B30数据提交目标玩家的RKS估计值。一轮游戏共5回合，提交的答案与目标玩家的RKS准确值的差值小于0.10即可获胜。\n\n玩家本人的成绩数据不会进入随机池，每回合提供的B30数据不会重复。';
const publicRules = '2名玩家准备后开始游戏，每回合随机给出2条对方玩家的B30成绩，玩家需根据B30数据提交对方玩家的RKS估计值。一轮游戏共5回合，每回合结束时，提交的答案与对方玩家的RKS准确值差值较小的一方获得1分。命中对方玩家的RKS准确值，或游戏结束时分数较高的一方获胜。\n\n每回合提供的B30数据不会重复。当第5回合结算满足平局条件时，进入第6回合。';

function WinRow({ row }: { row: any }) {
  return <div className={'guess-win-row' + (row.isMe ? ' is-me' : '')}><strong className={row.rank <= 3 ? 'accent' : 'muted'}>{row.rank}</strong><div><h3>{row.nickname}{row.isMe ? ' · 我' : ''}</h3><p className="muted">单人 {row.singleWins} · 匹配 {row.publicWins}</p></div><strong className="accent">{row.totalWins} 胜</strong></div>;
}
function WinBoard({ board, loading, error, refresh }: { board: any; loading: boolean; error: string; refresh: () => void }) {
  const [expanded, setExpanded] = useState(false);
  return <section className="card guess-win-board"><div className="section-heading"><h2>累计胜场榜</h2><button className="icon-button accent" aria-label="刷新胜场榜" disabled={loading} onClick={refresh}><RefreshCw size={22} className={loading ? 'spin' : ''} /></button></div><div className="section-heading guess-my-wins"><span className="muted">我的胜场</span><strong className="accent">{board?.myWins?.totalWins ?? board?.myWins ?? '—'}</strong></div><p className="muted small">单人 + 公开匹配 · 前 100 名</p>{loading && <Loading />}{error && <><p className="error-message" role="alert">{error}</p><button className="text-button" onClick={refresh}>重新加载</button></>}{board && !board.items?.length && <p className="muted small">暂无胜场记录，完成一场胜利即可上榜</p>}{board?.items?.slice(0, 3).map((row: any) => <WinRow key={row.rank} row={row} />)}{board?.items?.length > 3 && <><button className="text-button full" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>{expanded ? '收起榜单' : '查看完整榜单'}</button>{expanded && <div className="guess-full-board">{board.items.slice(3, 100).map((row: any) => <WinRow key={row.rank} row={row} />)}</div>}</>}</section>;
}
function GameHeader({ game, seconds }: { game: any; seconds: number }) {
  const count = Math.max(1, Math.min(6, game.totalRounds || 5));
  const settled = ['round_result', 'finished'].includes(game.status) ? game.round : game.round - 1;
  const timer = game.status === 'waiting' ? '暂未开始' : game.status === 'round_result' ? game.mode === 'public' ? '确认 ' + seconds + 's' : '已结算' : game.status === 'finished' ? '已结束' : seconds > 0 ? '剩余 ' + seconds + 's' : '结算中';
  return <div className="card guess-game-header"><div className="section-heading"><h2>第 {String(Math.max(1, game.round || 1)).padStart(2, '0')} / {String(game.totalRounds || 5).padStart(2, '0')} 回合</h2><strong className={seconds > 0 && seconds <= 10 && game.status !== 'finished' ? 'timer-urgent' : 'accent'}>{timer}</strong></div>{game.status !== 'waiting' && <div className="guess-round-progress" aria-label={'第 ' + game.round + ' 回合，共 ' + count + ' 回合'}>{Array.from({ length: count }, (_, index) => <Fragment key={index}>{index > 0 && <span className={'guess-round-line' + (index <= settled ? ' settled' : '')} />}<span className={'guess-round-dot' + (index + 1 <= settled ? ' settled' : '') + (game.status !== 'finished' && index + 1 === game.round ? ' current' : '')} /></Fragment>)}</div>}</div>;
}
function RoundSummary({ game }: { game: any }) {
  const result = game.lastRound;
  if (!result) return null;
  const outcome = game.mode === 'single' ? result.outcome === 'win' ? '本回合命中：估算结果在胜利范围内' : '本回合未命中' : result.outcome === 'win' ? '本回合你获胜' : result.outcome === 'lose' ? '本回合对方玩家获胜' : '本回合双方各得 1 分';
  return <section className={'card guess-round-summary outcome-' + result.outcome}><h2>{result.outcome === 'win' ? <CheckCircle2 size={18} /> : <Dices size={18} />}第 {result.round} 回合结果</h2><p>{outcome}</p><strong>答案公布</strong><p className="muted">{game.myNickname}(你)：{result.myAnswer == null ? '未提交' : fmt(result.myAnswer, 2)}</p>{game.mode === 'public' && <p className="muted">对方玩家：{result.opponentAnswer == null ? '未提交' : fmt(result.opponentAnswer, 2)}</p>}</section>;
}
function FinishedHero({ game }: { game: any }) {
  const won = game.winner === game.role;
  const title = won ? '你赢了' : ['A', 'B'].includes(game.winner) ? '对方玩家获胜' : game.winner === 'none' ? game.finishReason?.includes('判负') ? '你输了' : game.mode === 'single' ? '挑战失败' : '本局未分出胜负' : '本局平局';
  const outcome = won ? 'win' : ['A', 'B'].includes(game.winner) || title === '你输了' ? 'lose' : 'draw';
  return <section className={'card guess-finished outcome-' + outcome}>{won ? <Trophy size={34} /> : <Dices size={34} />}<h2>{title}</h2><p className="muted">{game.finishReason || '游戏结束'}</p><strong>最终比分：{game.myScore} : {game.opponentScore}</strong></section>;
}

export function Guess({ go }: { go: (page: string) => void }) {
  const { account, notify, songs } = usePSQ();
  const key = 'guess-game:' + account;
  const [gameId, setGameId] = useState<string | null>(() => localRead(key, null)), [game, setGame] = useState<any>(null), [mode, setMode] = useState('single'), [answer, setAnswer] = useState(''), [busy, setBusy] = useState(false), [now, setNow] = useState(Date.now()), [confirmLeave, setConfirmLeave] = useState(false), [leaveDestination, setLeaveDestination] = useState<string | null>(null), [ranks, setRanks] = useState<any>(null), [rankLoading, setRankLoading] = useState(false), [rankError, setRankError] = useState('');
  const lock = useRef(false), actionPending = useRef(false), gameSeq = useRef(0), rankSeq = useRef(0), mounted = useRef(true), clueList = useRef<HTMLDivElement>(null);
  const loadRanks = async () => {
    const id = ++rankSeq.current; setRankLoading(true); setRankError('');
    try { const reply = await api('games/rks-guess/leaderboard'); if (mounted.current && id === rankSeq.current) setRanks(reply); }
    catch (e) { if (mounted.current && id === rankSeq.current) setRankError((e as Error).message); } finally { if (mounted.current && id === rankSeq.current) setRankLoading(false); }
  };
  useEffect(() => { mounted.current = true; loadRanks(); return () => { mounted.current = false; gameSeq.current++; rankSeq.current++; }; }, []);
  useEffect(() => localWrite(key, gameId), [gameId, key]);
  useEffect(() => {
    if (!gameId) return;
    let stopped = false;
    const poll = async () => {
      if (lock.current || actionPending.current || stopped || document.visibilityState !== 'visible') return;
      lock.current = true; const revision = gameSeq.current;
      try { const next = await api('games/rks-guess/' + gameId); if (!stopped && revision === gameSeq.current) setGame(next); }
      catch (e) { if (!stopped && revision === gameSeq.current && (e as any).status === 404) { setGameId(null); setGame(null); notify('本局游戏已结束'); } }
      finally { lock.current = false; }
    };
    poll(); const timer = setInterval(poll, 1200), clock = setInterval(() => setNow(Date.now()), 250);
    document.addEventListener('visibilitychange', poll);
    return () => { stopped = true; clearInterval(timer); clearInterval(clock); document.removeEventListener('visibilitychange', poll); };
  }, [gameId]);
  useEffect(() => setAnswer(''), [game?.round]);
  const clues = useMemo(() => {
    const visible = new Set(songs.map((song: Song) => song.id));
    const history = game?.clueHistory?.length ? game.clueHistory : game ? [{ round: game.round, clues: game.clues || [] }] : [];
    return [...history].sort((a: any, b: any) => a.round - b.round).flatMap((group: any) => group.clues.flatMap((clue: any, index: number) => visible.has(clue.songId) ? [{ round: group.round, index: index + 1, clue }] : []));
  }, [game?.clueHistory, game?.clues, game?.round, songs]);
  useEffect(() => { clueList.current?.scrollTo({ top: clueList.current.scrollHeight, behavior: reducedMotion() ? 'instant' : 'smooth' }); }, [clues.length]);
  const action = async (name: string, data: any = {}) => {
    if (actionPending.current) return false; actionPending.current = true; gameSeq.current++; setBusy(true);
    try {
      const reply = await api('games/rks-guess/' + (name === 'match' ? 'match' : gameId + '/' + name), data);
      if (!mounted.current) return false;
      if (name === 'leave') { setGame(null); setGameId(null); loadRanks(); }
      else { setGame(reply); setGameId(reply.gameId); setNow(Date.now()); if (name === 'match' || name === 'next') window.scrollTo({ top: 0 }); }
      return true;
    } catch (e) { if (mounted.current) notify((e as Error).message); return false; }
    finally { actionPending.current = false; if (mounted.current) setBusy(false); }
  };
  const seconds = Math.max(0, Math.min(60, Math.ceil(((game?.deadlineAtEpochMs || now) - now) / 1000)));
  const submitted = game?.myAnswer != null;
  const contentMotion = useContentMotion<HTMLElement>((game?.gameId || 'welcome') + ':' + (game?.status || mode) + ':' + (game?.round || 0), '.guess-content');
  const submit = (e: React.FormEvent) => { e.preventDefault(); const value = Number(answer); if (!answer.trim() || !Number.isFinite(value) || value < 0 || value > 30) { notify('请输入有效的 RKS'); return; } action('answer', { answer: value }); };
  const changeAnswer = (value: string) => { const normalized = value.replace(',', '.'); if (/^\d{0,2}(\.\d{0,2})?$/.test(normalized) && (!normalized || !Number.isFinite(Number(normalized)) || Number(normalized) <= 30)) setAnswer(normalized); };
  const leave = (destination: string | null = null) => {
    if (!gameId) { if (destination) go(destination); return; }
    if (game?.status === 'finished') { action('leave').then(ok => { if (ok && destination) go(destination); }); }
    else { setLeaveDestination(destination); setConfirmLeave(true); }
  };
  return <section ref={contentMotion} className="page readable guess-page"><PageHeader title="RKS 猜猜乐" onBack={() => leave('more')} />
    {!gameId ? <div className="guess-content">
      <p className="muted guess-intro">根据两条 B30 成绩线索，估算目标玩家的综合 RKS。</p><div className="guess-modes">{[['single', '单人模式'], ['public', '公开匹配']].map(([value, label]) => <button className={'chip' + (mode === value ? ' selected' : '')} key={value} aria-pressed={mode === value} onClick={() => setMode(value)}>{label}</button>)}</div>
      <section className="card guess-rules" key={mode}><h2>RKS猜猜乐 – 玩法说明[{mode === 'single' ? '单人模式' : '公开匹配'}]</h2><p className="pre-wrap">{mode === 'single' ? singleRules : publicRules}</p></section><button className="button primary full" disabled={busy} onClick={() => action('match', { mode })}>{busy && <LoaderCircle className="spin" size={18} />}{mode === 'single' ? '开始单人游戏' : '开始公开匹配'}</button><WinBoard board={ranks} loading={rankLoading} error={rankError} refresh={loadRanks} />
    </div> : !game ? <Loading text="正在恢复游戏…" /> : <div className="guess-content">
      <GameHeader game={game} seconds={seconds} />
      {game.status === 'waiting' ? <><div className="card guess-waiting"><LoaderCircle className="spin" size={54} /><h2>正在寻找另一位玩家</h2><div className="guess-waiting-dots"><i /><i /><i /></div><p className="muted">匹配成功后会自动开始第 1 回合。</p></div><button className="button outline full" onClick={() => action('leave')} disabled={busy}>取消匹配</button></> : game.status === 'playing' ? <>
        <div className="guess-player-statuses"><div><span className="muted">{game.myNickname}(你)</span><strong>{submitted ? fmt(game.myAnswer, 2) : '暂未提交答案'}</strong><span className="accent">得分 {game.myScore}</span></div>{game.mode === 'public' && <div><span className="muted">对方玩家</span><strong>{game.opponentSubmitted ? '已提交答案' : '暂未提交答案'}</strong><span className="accent">得分 {game.opponentScore}</span></div>}</div>
        <Progress className={'guess-countdown' + (seconds <= 10 ? ' urgent' : '')} max={60} value={seconds} label="回合剩余时间" /><section className="guess-clues"><h2 className="accent">线索记录</h2>{!clues.length ? <p className="muted small">正在准备本回合线索…</p> : <div className="guess-clue-list" ref={clueList}>{clues.map(({ round, index, clue }: any) => <div className="guess-clue-card" key={round + ':' + index + ':' + clue.slot} style={{ animationDelay: (index - 1) * 110 + 'ms' }}><Art songId={clue.songId} /><div className="guess-clue-copy"><p className="muted">第 {round} 回合 · 线索 #{String(index).padStart(2, '0')}</p><h3>{clue.slot} - {clue.songName}</h3><strong className="accent guess-clue-difficulty">[{clue.difficulty}]</strong><div className="guess-clue-score"><strong>{String(Math.round(clue.score)).padStart(7, '0')}</strong><span>{fmt(clue.accuracy, 2)}%</span></div><strong className="accent guess-clue-rks">定数 {fmt(clue.chartConstant, 2)}　→　单曲 RKS {fmt(clue.rks, 2)}</strong></div></div>)}</div>}</section>
        <form className={'card guess-answer' + (submitted ? ' submitted' : '')} onSubmit={submit}><h2 className="accent">答案提交</h2><p className="muted">{game.mode === 'single' ? '匿名目标玩家' : '对方玩家'}的 RKS 估计值（精确到小数点后 2 位）</p><div className="guess-answer-fields"><input aria-label="RKS 估计值" inputMode="decimal" value={answer} disabled={submitted || busy || seconds <= 0} onChange={e => changeAnswer(e.target.value)} /><button className="button primary" aria-label={submitted ? '已提交' : '提交答案'} disabled={busy || submitted || seconds <= 0 || !answer.trim() || !Number.isFinite(Number(answer))}>{busy ? <LoaderCircle className="spin" size={17} /> : submitted ? <CheckCircle2 size={19} /> : '提交答案'}</button></div>{(submitted || game.mode === 'public' && game.opponentSubmitted) && <p className={submitted ? 'muted' : 'accent'}>{submitted ? '你的答案：' + fmt(game.myAnswer, 2) + ' · 等待对方或回合结算' : '对方玩家已提交答案；具体数值将在回合结算后公开。'}</p>}</form><button className="button outline full" disabled={busy} onClick={() => leave()}>退出游戏</button>
      </> : game.status === 'round_result' ? <>
        <RoundSummary game={game} />{game.mode === 'public' && <><Progress className={'guess-countdown' + (seconds <= 10 ? ' urgent' : '')} max={60} value={seconds} label="回合确认剩余时间" /><p className="muted small center">{seconds}s 内确认</p></>}<button className="button primary full guess-next" disabled={busy || game.myRoundReady} onClick={() => action('next')}>{busy && <LoaderCircle className="spin" size={18} />}{game.myRoundReady ? '等待对方玩家进入下一回合' : (game.mode === 'single' ? '进入第 ' : '确认并进入第 ') + (game.round + 1) + ' 回合'}</button>{game.mode === 'public' && game.opponentRoundReady && !game.myRoundReady && <p className="accent small center">对方玩家已准备好进入下一回合</p>}<button className="button outline full" disabled={busy} onClick={() => leave()}>退出游戏</button>
      </> : game.status === 'finished' ? <>
        <FinishedHero game={game} />{game.mode === 'single' ? game.targetRks != null && <p className="accent">本局目标 RKS：{fmt(game.targetRks, 2)}</p> : <div className="button-row equal accent">{game.myRks != null && <p>你的 RKS：{fmt(game.myRks, 2)}</p>}{game.opponentRks != null && <p>对方玩家 RKS：{fmt(game.opponentRks, 2)}</p>}</div>}<RoundSummary game={game} /><button className="button primary full" disabled={busy} onClick={() => action('leave')}>返回小游戏</button>
      </> : <><p className="muted">游戏状态：{game.status}</p><button className="button outline full" onClick={() => action('leave')}>返回</button></>}
    </div>}
    {confirmLeave && <Confirm title="退出游戏？" body="进行中的游戏将结束。" onClose={() => setConfirmLeave(false)} onConfirm={() => { action('leave').then(ok => { if (ok && leaveDestination) go(leaveDestination); }); }} />}
  </section>;
}
