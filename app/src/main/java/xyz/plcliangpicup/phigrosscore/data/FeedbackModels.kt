package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

@Serializable
data class Feedback(
    val id: String, val title: String, val body: String,
    val status: String, val reply: String,
    val createdAt: String, val updatedAt: String,
    val revision: Long, val readRevision: Long, val imageCount: Int,
) {
    val statusLabel: String get() = when (status) {
        "processing" -> "处理中"
        "resolved" -> "已处理"
        else -> "待处理"
    }
}

@Serializable
data class FeedbackDraft(val id: String, val title: String, val body: String, val images: List<String>)

@Serializable
data class FeedbackUpdates(val cursor: String, val items: List<Feedback>)
