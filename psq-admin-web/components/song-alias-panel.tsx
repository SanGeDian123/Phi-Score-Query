'use client';

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from 'react';
import {
  Check,
  LoaderCircle,
  Music2,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Tags,
  Trash2,
} from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';

type SongAliases = {
  songId: string;
  name: string;
  composer: string;
  aliases: string[];
  revision: string;
};
type AliasDraft = {
  aliases: string[];
  revision: string;
  conflict?: boolean;
  latestAliases?: string[];
};
type AliasEditor = { songId: string; index: number | null; value: string };
type PendingAction =
  | { kind: 'delete'; songId: string; index: number; alias: string }
  | {
      kind: 'save';
      songId: string;
      name: string;
      aliases: string[];
      revision: string;
    };
type StatusMessage = {
  kind: 'success' | 'error';
  text: string;
  songId?: string;
};

const maxAliases = 200;
const maxAliasCharacters = 128;
const controlCharacters = /[\u0000-\u001f\u007f-\u009f]/u;

function sameAliases(left: string[], right: string[]): boolean {
  return (
    left.length === right.length &&
    left.every((alias, index) => alias === right[index])
  );
}

function validateAliases(aliases: string[]): string | null {
  if (aliases.length > maxAliases) return '每首曲目最多保存 200 条别名';
  const seen = new Set<string>();
  for (const alias of aliases) {
    if (controlCharacters.test(alias)) return '别名不能包含控制字符';
    const cleanAlias = alias.trim();
    const length = Array.from(cleanAlias).length;
    if (length < 1 || length > maxAliasCharacters)
      return '每条别名须为 1–128 个字符';
    if (seen.has(cleanAlias)) return '同一曲目不能保存完全相同的别名';
    seen.add(cleanAlias);
  }
  return null;
}

function isSongAliases(value: unknown): value is SongAliases {
  if (value === null || typeof value !== 'object') return false;
  const item = value as Record<string, unknown>;
  return (
    typeof item.songId === 'string' &&
    typeof item.name === 'string' &&
    typeof item.composer === 'string' &&
    typeof item.revision === 'string' &&
    Array.isArray(item.aliases) &&
    item.aliases.every((alias) => typeof alias === 'string')
  );
}

async function responseJson(response: Response): Promise<unknown> {
  const payload: unknown = await response.json();
  if (!response.ok) {
    const error =
      payload !== null && typeof payload === 'object'
        ? (payload as { error?: unknown; detail?: unknown }).error ||
          (payload as { detail?: unknown }).detail
        : null;
    throw new Error(
      typeof error === 'string' ? error : `请求失败（${response.status}）`,
    );
  }
  return payload;
}

export function SongAliasPanel({
  token,
  connected,
}: {
  token: string;
  connected: boolean;
}) {
  const [items, setItems] = useState<SongAliases[]>([]);
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, AliasDraft>>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<StatusMessage | null>(null);
  const [editor, setEditor] = useState<AliasEditor | null>(null);
  const [editorError, setEditorError] = useState('');
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(
    null,
  );
  const loadController = useRef<AbortController | null>(null);
  const saveController = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    if (!connected || !token) return;
    loadController.current?.abort();
    const controller = new AbortController();
    loadController.current = controller;
    setLoading(true);
    setMessage(null);
    try {
      const response = await fetch('/api/song-aliases', {
        headers: { 'X-Admin-Token': token },
        cache: 'no-store',
        signal: controller.signal,
      });
      const payload = await responseJson(response);
      const nextItems =
        payload !== null && typeof payload === 'object'
          ? (payload as { items?: unknown }).items
          : null;
      if (!Array.isArray(nextItems) || !nextItems.every(isSongAliases)) {
        throw new Error('曲目别名数据格式无效，请检查后端版本');
      }
      if (controller.signal.aborted) return;
      setItems(nextItems);
      setDrafts((current) => {
        const next = { ...current };
        for (const song of nextItems) {
          const draft = next[song.songId];
          if (!draft) continue;
          if (sameAliases(draft.aliases, song.aliases)) {
            delete next[song.songId];
          } else if (draft.revision !== song.revision || draft.conflict) {
            next[song.songId] = {
              ...draft,
              revision: song.revision,
              conflict: false,
              latestAliases: song.aliases,
            };
          }
        }
        return next;
      });
      setSelectedId((current) =>
        current && nextItems.some((item) => item.songId === current)
          ? current
          : null,
      );
    } catch (error) {
      if (!controller.signal.aborted)
        setMessage({
          kind: 'error',
          text: error instanceof Error ? error.message : '曲目别名加载失败',
        });
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [connected, token]);

  useEffect(() => {
    setItems([]);
    setSelectedId(null);
    setDrafts({});
    setMessage(null);
    setEditor(null);
    setPendingAction(null);
    setLoading(false);
    setSaving(false);
    if (connected) void load();
    return () => {
      loadController.current?.abort();
      saveController.current?.abort();
    };
  }, [connected, load]);

  const filteredItems = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return items;
    return items.filter((item) =>
      [
        item.name,
        item.songId,
        ...item.aliases,
        ...(drafts[item.songId]?.aliases ?? []),
      ].some((value) => value.toLowerCase().includes(keyword)),
    );
  }, [items, drafts, query]);
  const selected = items.find((item) => item.songId === selectedId) ?? null;
  const selectedDraft = selected ? drafts[selected.songId] : undefined;
  const aliases = selectedDraft?.aliases ?? selected?.aliases ?? [];
  const dirty = !!selected && !sameAliases(aliases, selected.aliases);
  const busy = loading || saving;
  const visibleMessage =
    message && (!message.songId || message.songId === selectedId)
      ? message
      : null;

  function updateDraft(song: SongAliases, nextAliases: string[]) {
    setDrafts((current) => {
      const next = { ...current };
      if (sameAliases(nextAliases, song.aliases)) {
        delete next[song.songId];
      } else {
        next[song.songId] = {
          ...current[song.songId],
          aliases: nextAliases,
          revision: current[song.songId]?.revision ?? song.revision,
        };
      }
      return next;
    });
    setMessage(null);
  }

  function applyAlias(event: FormEvent) {
    event.preventDefault();
    if (!editor || busy) return;
    const song = items.find((item) => item.songId === editor.songId);
    if (!song) return;
    const nextAliases = [...(drafts[song.songId]?.aliases ?? song.aliases)];
    const value = editor.value;
    if (editor.index === null) nextAliases.push(value);
    else nextAliases[editor.index] = value;
    const error = validateAliases(nextAliases);
    if (error) {
      setEditorError(error);
      return;
    }
    updateDraft(
      song,
      nextAliases.map((alias) => alias.trim()),
    );
    setEditor(null);
  }

  function requestSave() {
    if (!selected || !dirty || busy || selectedDraft?.conflict) return;
    const error = validateAliases(aliases);
    if (error) {
      setMessage({ kind: 'error', text: error, songId: selected.songId });
      return;
    }
    const cleanAliases = aliases.map((alias) => alias.trim());
    setPendingAction({
      kind: 'save',
      songId: selected.songId,
      name: selected.name,
      aliases: cleanAliases,
      revision: selectedDraft?.revision ?? selected.revision,
    });
  }

  async function confirmAction() {
    const action = pendingAction;
    if (!action || busy) return;
    const song = items.find((item) => item.songId === action.songId);
    if (!song) return;
    if (action.kind === 'delete') {
      const currentAliases = drafts[song.songId]?.aliases ?? song.aliases;
      updateDraft(
        song,
        currentAliases.filter((_, index) => index !== action.index),
      );
      setPendingAction(null);
      return;
    }
    const controller = new AbortController();
    saveController.current = controller;
    setSaving(true);
    setMessage(null);
    try {
      const response = await fetch('/api/song-aliases', {
        method: 'PUT',
        headers: { 'X-Admin-Token': token, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          songId: action.songId,
          aliases: action.aliases,
          revision: action.revision,
        }),
        cache: 'no-store',
        signal: controller.signal,
      });
      if (response.status === 409 && !controller.signal.aborted) {
        setDrafts((current) => ({
          ...current,
          [action.songId]: {
            ...current[action.songId],
            aliases: action.aliases,
            revision: action.revision,
            conflict: true,
          },
        }));
        setPendingAction(null);
      }
      const saved = await responseJson(response);
      if (!isSongAliases(saved) || saved.songId !== action.songId)
        throw new Error('保存响应格式无效，请刷新确认同步结果');
      if (controller.signal.aborted) return;
      setItems((current) =>
        current.map((item) => (item.songId === saved.songId ? saved : item)),
      );
      setDrafts((current) => {
        const next = { ...current };
        delete next[saved.songId];
        return next;
      });
      setPendingAction(null);
      setMessage({
        kind: 'success',
        songId: saved.songId,
        text: '别名已同步，曲目搜索已生效',
      });
    } catch (error) {
      if (!controller.signal.aborted)
        setMessage({
          kind: 'error',
          songId: action.songId,
          text: error instanceof Error ? error.message : '保存失败，请重试',
        });
    } finally {
      if (!controller.signal.aborted) setSaving(false);
    }
  }

  return (
    <Card
      id="song-aliases"
      className="psq-glass-card mt-6 min-w-0 scroll-mt-36 lg:scroll-mt-6"
    >
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 px-4 sm:px-6">
        <div className="min-w-0 space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <Tags className="size-5 text-primary" />
            曲目别名
          </CardTitle>
          <CardDescription>选择曲目，管理用于搜索的别名</CardDescription>
        </div>
        <Button
          variant="outline"
          disabled={!connected || busy}
          onClick={() => void load()}
        >
          {loading ? <LoaderCircle className="animate-spin" /> : <RefreshCw />}
          刷新曲库
        </Button>
      </CardHeader>
      <CardContent className="space-y-4 px-4 sm:px-6">
        {visibleMessage && (
          <p
            role={visibleMessage.kind === 'error' ? 'alert' : 'status'}
            className={`break-words rounded-xl border p-3 text-sm ${visibleMessage.kind === 'error' ? 'border-destructive/20 bg-destructive/5 text-destructive' : 'border-primary/15 bg-primary/5 text-primary'}`}
          >
            {visibleMessage.text}
          </p>
        )}
        {!connected ? (
          <p className="py-6 text-sm text-muted-foreground">
            连接管理台后管理曲目别名
          </p>
        ) : loading && !items.length ? (
          <p className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <LoaderCircle className="size-4 animate-spin" />
            正在加载曲库…
          </p>
        ) : !items.length ? (
          <p className="py-6 text-sm text-muted-foreground">暂无曲目数据</p>
        ) : (
          <div className="grid min-w-0 gap-4 md:grid-cols-[minmax(220px,0.8fr)_minmax(0,1.2fr)] xl:grid-cols-[320px_minmax(0,1fr)]">
            <div className="min-w-0 space-y-3">
              <label className="relative block" htmlFor="song-alias-search">
                <span className="sr-only">搜索曲目</span>
                <Search className="pointer-events-none absolute top-3.5 left-3 size-4 text-muted-foreground" />
                <Input
                  id="song-alias-search"
                  className="psq-glass-control h-11 pl-9"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="曲名、ID 或别名"
                  disabled={saving}
                />
              </label>
              <p className="text-xs text-muted-foreground">
                共 {items.length} 首 · 当前显示 {filteredItems.length} 首
              </p>
              <div
                className="psq-glass-subcard max-h-64 space-y-1 overflow-y-auto rounded-xl border p-1.5 md:max-h-[580px]"
                aria-label="曲目列表"
                aria-busy={loading}
              >
                {filteredItems.length === 0 ? (
                  <p className="p-4 text-sm text-muted-foreground">
                    未找到匹配曲目
                  </p>
                ) : (
                  filteredItems.map((song) => {
                    const songDraft = drafts[song.songId];
                    const unsaved =
                      !!songDraft &&
                      !sameAliases(songDraft.aliases, song.aliases);
                    return (
                      <button
                        key={song.songId}
                        type="button"
                        disabled={busy}
                        aria-pressed={song.songId === selectedId}
                        onClick={() => setSelectedId(song.songId)}
                        className={`w-full min-w-0 rounded-lg p-3 text-left transition-colors focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-60 ${song.songId === selectedId ? 'bg-primary/10 text-primary' : 'hover:bg-primary/5'}`}
                      >
                        <span className="flex items-start justify-between gap-2">
                          <span className="min-w-0 break-words text-sm font-semibold">
                            {song.name}
                          </span>
                          {unsaved && (
                            <span className="shrink-0 text-[10px] text-amber-700">
                              未保存
                            </span>
                          )}
                        </span>
                        <span className="mt-1 block break-all text-[11px] text-muted-foreground">
                          {song.songId}
                        </span>
                        <span className="mt-1 block text-[11px] text-muted-foreground">
                          {song.aliases.length} 条别名
                        </span>
                      </button>
                    );
                  })
                )}
              </div>
            </div>
            <div className="min-w-0 rounded-2xl border border-primary/10 bg-primary/[0.025] p-4 sm:p-5">
              {!selected ? (
                <div className="flex min-h-48 flex-col items-center justify-center gap-3 text-center text-muted-foreground">
                  <Music2 className="size-7 text-primary/50" />
                  <p className="text-sm">选择一首曲目查看和编辑别名</p>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 space-y-1">
                      <h3 className="break-words text-base font-bold">
                        {selected.name}
                      </h3>
                      <p className="break-all text-xs text-muted-foreground">
                        {selected.songId}
                      </p>
                      <p className="break-words text-xs text-muted-foreground">
                        {selected.composer}
                      </p>
                    </div>
                    <Badge
                      variant="outline"
                      className={
                        selectedDraft?.conflict
                          ? 'border-destructive/25 text-destructive'
                          : dirty
                            ? 'border-amber-600/25 text-amber-700'
                            : 'border-primary/20 text-primary'
                      }
                    >
                      {selectedDraft?.conflict
                        ? '版本冲突'
                        : dirty
                          ? '未保存'
                          : '已同步'}
                    </Badge>
                  </div>
                  {selectedDraft?.conflict && (
                    <div
                      role="alert"
                      className="space-y-2 rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm"
                    >
                      <p>
                        其他管理员已修改这首曲目的别名。草稿已保留，请刷新曲库并核对最新内容后保存。
                      </p>
                      <Button
                        variant="outline"
                        disabled={busy}
                        onClick={() => void load()}
                      >
                        <RefreshCw />
                        刷新并保留草稿
                      </Button>
                    </div>
                  )}
                  {selectedDraft?.latestAliases && (
                    <details className="rounded-xl border border-primary/15 bg-background/50 p-3 text-sm">
                      <summary className="cursor-pointer font-medium text-primary">
                        已载入最新版本，草稿已保留 · 查看服务器当前别名
                      </summary>
                      <p className="mt-2 text-xs text-muted-foreground">
                        核对后保存将以当前草稿替换服务器别名。
                      </p>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {selectedDraft.latestAliases.length ? (
                          selectedDraft.latestAliases.map((alias) => (
                            <Badge
                              key={alias}
                              variant="outline"
                              className="max-w-full whitespace-normal break-all"
                            >
                              {alias}
                            </Badge>
                          ))
                        ) : (
                          <span className="text-muted-foreground">
                            暂无别名
                          </span>
                        )}
                      </div>
                    </details>
                  )}
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-xs text-muted-foreground">
                      {aliases.length} / {maxAliases} 条别名
                    </p>
                    <Button
                      variant="outline"
                      disabled={busy || aliases.length >= maxAliases}
                      onClick={() => {
                        setEditor({
                          songId: selected.songId,
                          index: null,
                          value: '',
                        });
                        setEditorError('');
                      }}
                    >
                      <Plus />
                      添加别名
                    </Button>
                  </div>
                  {aliases.length === 0 ? (
                    <div className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">
                      暂无别名，可添加新的搜索名称
                    </div>
                  ) : (
                    <ol className="max-h-[420px] space-y-2 overflow-y-auto pr-1">
                      {aliases.map((alias, index) => (
                        <li
                          key={`${index}-${alias}`}
                          className="psq-glass-subcard flex min-w-0 items-center gap-2 rounded-xl border p-2.5"
                        >
                          <span className="w-6 shrink-0 text-center text-[11px] tabular-nums text-muted-foreground">
                            {index + 1}
                          </span>
                          <span className="min-w-0 flex-1 break-all text-sm">
                            {alias}
                          </span>
                          <Button
                            size="icon-sm"
                            variant="ghost"
                            disabled={busy}
                            aria-label={`编辑别名 ${alias}`}
                            onClick={() => {
                              setEditor({
                                songId: selected.songId,
                                index,
                                value: alias,
                              });
                              setEditorError('');
                            }}
                          >
                            <Pencil className="size-4" />
                          </Button>
                          <Button
                            size="icon-sm"
                            variant="ghost"
                            disabled={busy}
                            className="text-destructive hover:bg-destructive/5"
                            aria-label={`删除别名 ${alias}`}
                            onClick={() =>
                              setPendingAction({
                                kind: 'delete',
                                songId: selected.songId,
                                index,
                                alias,
                              })
                            }
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        </li>
                      ))}
                    </ol>
                  )}
                  <div className="flex flex-col gap-3 border-t border-primary/10 pt-4 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-xs text-muted-foreground">
                      {dirty
                        ? '草稿尚未同步，切换曲目会保留本次修改'
                        : '当前别名与服务器一致'}
                    </p>
                    <Button
                      className="w-full shrink-0 sm:w-auto"
                      disabled={busy || !dirty || selectedDraft?.conflict}
                      onClick={requestSave}
                    >
                      {saving ? (
                        <LoaderCircle className="animate-spin" />
                      ) : (
                        <Save />
                      )}
                      保存全部别名
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </CardContent>

      <Dialog
        open={editor !== null}
        onOpenChange={(open) => {
          if (!open && !busy) setEditor(null);
        }}
      >
        <DialogContent
          className="psq-glass-dialog max-h-[90dvh] overflow-y-auto rounded-[22px] p-5 sm:max-w-md sm:p-6"
          showCloseButton={!busy}
        >
          <DialogHeader>
            <DialogTitle>
              {editor?.index === null ? '添加曲目别名' : '编辑曲目别名'}
            </DialogTitle>
            <DialogDescription>
              输入用于搜索的名称，每条最多 128 个字符
            </DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={applyAlias}>
            <label
              className="block space-y-2 text-sm"
              htmlFor="song-alias-value"
            >
              <span>别名</span>
              <Input
                autoFocus
                id="song-alias-value"
                className="psq-glass-control h-11"
                disabled={busy}
                value={editor?.value ?? ''}
                onPaste={(event) => {
                  if (controlCharacters.test(event.clipboardData.getData('text'))) {
                    event.preventDefault();
                    setEditorError('别名不能包含控制字符');
                  }
                }}
                onChange={(event) => {
                  setEditor(
                    (current) =>
                      current && { ...current, value: event.target.value },
                  );
                  setEditorError('');
                }}
                placeholder="输入别名"
              />
            </label>
            <div className="flex justify-end text-xs text-muted-foreground">
              {Array.from(editor?.value.trim() ?? '').length} /{' '}
              {maxAliasCharacters}
            </div>
            {editorError && (
              <p role="alert" className="text-sm text-destructive">
                {editorError}
              </p>
            )}
            <DialogFooter className="-mx-5 -mb-5 px-5 sm:-mx-6 sm:-mb-6 sm:px-6">
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => setEditor(null)}
              >
                取消
              </Button>
              <Button type="submit" disabled={busy}>
                <Check />
                应用到草稿
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open && !saving) setPendingAction(null);
        }}
      >
        <DialogContent
          className="psq-glass-dialog max-h-[90dvh] overflow-y-auto rounded-[22px] p-5 sm:max-w-lg sm:p-6"
          showCloseButton={!saving}
        >
          <DialogHeader>
            <DialogTitle>
              {pendingAction?.kind === 'delete'
                ? '删除这条别名？'
                : '保存全部别名？'}
            </DialogTitle>
            <DialogDescription>
              {pendingAction?.kind === 'delete'
                ? '删除会先应用到草稿，保存全部别名后同步到服务器。'
                : '保存后将同步整份别名列表，并立即用于曲目搜索。'}
            </DialogDescription>
          </DialogHeader>
          {pendingAction?.kind === 'delete' ? (
            <p className="psq-glass-subcard break-all rounded-xl p-3 text-sm">
              {pendingAction.alias}
            </p>
          ) : pendingAction?.kind === 'save' ? (
            <div className="space-y-3">
              <p className="break-words font-semibold">{pendingAction.name}</p>
              <p className="break-all text-xs text-muted-foreground">
                {pendingAction.songId} · {pendingAction.aliases.length} 条别名
              </p>
              <div className="max-h-60 overflow-y-auto rounded-xl border p-3">
                {pendingAction.aliases.length ? (
                  <ol className="space-y-2 text-sm">
                    {pendingAction.aliases.map((alias, index) => (
                      <li key={alias} className="break-all">
                        <span className="mr-2 text-xs text-muted-foreground">
                          {index + 1}.
                        </span>
                        {alias}
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="text-sm text-destructive">
                    保存后将清空这首曲目的全部别名。
                  </p>
                )}
              </div>
            </div>
          ) : null}
          {visibleMessage?.kind === 'error' && (
            <p role="alert" className="break-words text-sm text-destructive">
              {visibleMessage.text}
            </p>
          )}
          <DialogFooter className="-mx-5 -mb-5 px-5 sm:-mx-6 sm:-mb-6 sm:px-6">
            <Button
              variant="outline"
              disabled={saving}
              onClick={() => setPendingAction(null)}
            >
              返回
            </Button>
            <Button
              variant={
                pendingAction?.kind === 'delete' ? 'destructive' : 'default'
              }
              disabled={busy}
              onClick={() => void confirmAction()}
            >
              {saving ? (
                <LoaderCircle className="animate-spin" />
              ) : pendingAction?.kind === 'delete' ? (
                <Trash2 />
              ) : (
                <Save />
              )}
              {pendingAction?.kind === 'delete' ? '删除别名' : '确认保存'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
