package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

@Serializable
data class SuggestionAuthor(
    val nickname: String,
    val avatar: String? = null,
    val challengeModeRank: Int? = null,
    val rks: Double = 0.0,
)

@Serializable
data class SuggestionComment(
    val id: String,
    val text: String = "",
    val imageUrl: String? = null,
    val author: SuggestionAuthor,
    val createdAt: String,
)

@Serializable
data class SuggestionPost(
    val id: String,
    val description: String,
    val imageUrl: String,
    val author: SuggestionAuthor,
    val createdAt: String,
    val comments: List<SuggestionComment> = emptyList(),
)
