package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

@Serializable
data class UserModerationStatus(
    val publicHidden: Boolean = false,
    val publicReason: String? = null,
    val publicExpiresAt: String? = null,
    val accountSuspended: Boolean = false,
    val accountReason: String? = null,
    val accountExpiresAt: String? = null,
    val publicAppealStatus: String? = null,
    val publicAppealReply: String? = null,
    val accountAppealStatus: String? = null,
    val accountAppealReply: String? = null,
)

@Serializable
data class ModerationAppealDraft(
    val sessionToken: String,
    val restrictionType: String,
    val body: String,
    val images: List<String>,
)

@Serializable
data class ModerationAppealReceipt(
    val id: String,
    val status: String,
    val createdAt: String,
)
