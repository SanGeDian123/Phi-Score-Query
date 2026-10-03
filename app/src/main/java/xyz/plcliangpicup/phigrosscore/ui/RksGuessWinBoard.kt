package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RksGuessWinBoard(state: AppUiState, onRefresh: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val board = state.rksGuessLeaderboard
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().animateContentSize(appTween(180)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("累计胜场榜", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !state.rksGuessLeaderboardLoading) {
                    Icon(Icons.Default.Refresh, "刷新胜场榜", tint = AppAccent)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的胜场", color = AppTextMuted, modifier = Modifier.weight(1f))
                if (board == null) Text("—", color = AppTextMuted)
                else AnimatedRksNumber(board.myWins.toDouble(), fontSize = 24.sp, color = AppAccent,
                    fontWeight = FontWeight.SemiBold, fractionDigits = 0)
            }
            Text("单人 + 公开匹配 · 前 100 名", color = AppTextMuted, fontSize = 12.sp)
            if (state.rksGuessLeaderboardLoading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            state.rksGuessLeaderboardError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = onRefresh, enabled = !state.rksGuessLeaderboardLoading) { Text("重新加载") }
            }
            if (board != null && board.items.isEmpty()) Text("暂无胜场记录，完成一场胜利即可上榜", color = AppTextMuted, fontSize = 13.sp)
            board?.items?.take(3)?.forEach { RksGuessWinRow(it) }
            if (board != null && board.items.size > 3) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) "收起榜单" else "查看完整榜单")
                }
                AnimatedVisibility(expanded,
                    enter = fadeIn(appTween(120)) + expandVertically(appTween(180)),
                    exit = fadeOut(appTween(100)) + shrinkVertically(appTween(160))) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(board.items.drop(3), key = { it.rank }) { RksGuessWinRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RksGuessWinRow(row: xyz.plcliangpicup.phigrosscore.data.RksGuessWinRank) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (row.isMe) AppAccent.copy(alpha = .08f) else AppSurfaceRaised.copy(alpha = .45f))
        .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(row.rank.toString(), color = if (row.rank <= 3) AppAccent else AppTextMuted,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.nickname + if (row.isMe) " · 我" else "", fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text("单人 ${row.singleWins} · 匹配 ${row.publicWins}", color = AppTextMuted, fontSize = 11.sp)
        }
        Text("${row.totalWins} 胜", color = AppAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
