package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

@Serializable
data class RksGuessWinRank(
    val rank: Int,
    val nickname: String,
    val totalWins: Long,
    val singleWins: Long,
    val publicWins: Long,
    val isMe: Boolean = false,
)

@Serializable
data class RksGuessWinLeaderboard(val items: List<RksGuessWinRank> = emptyList(), val myWins: Long = 0)
