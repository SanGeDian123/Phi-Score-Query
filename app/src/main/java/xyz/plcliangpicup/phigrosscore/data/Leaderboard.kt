package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.delay
import kotlin.math.abs

internal suspend fun fetchConsistentLeaderboard(
    snapshot: B30Snapshot,
    fetchTop: suspend () -> LeaderboardResponse,
    fetchMe: suspend () -> LeaderboardMe,
): LeaderboardSnapshot {
    repeat(3) { attempt ->
        val response = fetchTop()
        val me = fetchMe()
        val ownEntry = response.items.firstOrNull { it.rank == me.rank }
        val ownEntryExpected = me.rank in 1..minOf(response.total, PLAYER_LEADERBOARD_LIMIT)
        if (abs(me.score - snapshot.totalRks) < 0.0000001 &&
            response.total == me.total &&
            (!ownEntryExpected || ownEntry != null) &&
            (ownEntry == null || abs(ownEntry.score - me.score) < 0.0000001)
        ) {
            return LeaderboardSnapshot(response.items, me, snapshot.playerProfile, snapshot)
        }
        // 兼容旧后端的异步落盘，也重读 top/me 两次请求之间发生变化的榜单。
        if (attempt < 2) delay(200L * (attempt + 1))
    }
    throw IllegalStateException("排行榜与当前成绩尚未同步，请稍后刷新")
}
