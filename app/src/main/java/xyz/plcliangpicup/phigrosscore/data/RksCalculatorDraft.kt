package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

private const val RKS_DRAFT_SLOT_COUNT = 36

fun customRankingSlotCount(ranking: String, style: B30ImageStyle): Int = CUSTOM_RANKING_SLOT_COUNT

@Serializable
data class CustomChartDraft(
    /** manual keeps the single-RKS entry path; catalog means one-click chart fill. */
    val source: String = "manual",
    val songId: String = "",
    val songName: String = "",
    val difficulty: String = "",
    val chartConstant: String = "",
    val score: String = "",
    val accuracy: String = "",
) {
    fun normalized(): CustomChartDraft = copy(
        source = source.takeIf { it in VALID_SOURCES } ?: "manual",
        songId = songId.trim().take(128),
        songName = songName.trim().take(120),
        difficulty = difficulty.trim().uppercase().takeIf { it in VALID_DIFFICULTIES }.orEmpty(),
        chartConstant = normalizeDecimal(chartConstant),
        score = normalizeInteger(score),
        accuracy = normalizeDecimal(accuracy),
    )

    private companion object {
        val VALID_SOURCES = setOf("manual", "catalog")
        val VALID_DIFFICULTIES = setOf("EZ", "HD", "IN", "AT")
        val DECIMAL_PATTERN = Regex("\\d{0,3}(\\.\\d{0,8})?")
        val INTEGER_PATTERN = Regex("\\d{0,7}")

        fun normalizeDecimal(value: String): String =
            value.takeIf { it.isEmpty() || it.matches(DECIMAL_PATTERN) }.orEmpty()

        fun normalizeInteger(value: String): String =
            value.takeIf { it.isEmpty() || it.matches(INTEGER_PATTERN) }.orEmpty()
    }
}

@Serializable
data class RksCalculatorDraft(
    val mode: String = "three_value",
    val threeConstant: String = "",
    val threeAccuracy: String = "",
    val threeRks: String = "",
    val growthConstant: String = "",
    val growthMetric: String = "acc",
    val growthCurrentValue: String = "",
    val growthTargetAccuracy: String = "",
    val growthAccountRks: String = "",
    val customRanking: String = "b30",
    val customInputMode: String = "manual",
    val b30Values: List<String> = List(RKS_DRAFT_SLOT_COUNT) { "" },
    val p30Values: List<String> = List(RKS_DRAFT_SLOT_COUNT) { "" },
    val customB30Charts: List<CustomChartDraft> = List(RKS_DRAFT_SLOT_COUNT) { CustomChartDraft() },
    val customP30Charts: List<CustomChartDraft> = List(RKS_DRAFT_SLOT_COUNT) { CustomChartDraft() },
    val playNoteCount: String = "",
    val playPerfectCount: String = "",
    val playGoodCount: String = "",
    val playBadCount: String = "",
    val playMissCount: String = "",
    val playMaxCombo: String = "",
) {
    fun normalized(): RksCalculatorDraft = copy(
        mode = mode.takeIf { it in VALID_MODES } ?: "three_value",
        threeConstant = normalizeDecimal(threeConstant),
        threeAccuracy = normalizeDecimal(threeAccuracy),
        threeRks = normalizeDecimal(threeRks),
        growthConstant = normalizeDecimal(growthConstant),
        growthMetric = growthMetric.takeIf { it in VALID_GROWTH_METRICS } ?: "acc",
        growthCurrentValue = normalizeDecimal(growthCurrentValue),
        growthTargetAccuracy = normalizeDecimal(growthTargetAccuracy),
        growthAccountRks = normalizeDecimal(growthAccountRks),
        customRanking = customRanking.takeIf { it in VALID_CUSTOM_RANKINGS } ?: "b30",
        customInputMode = customInputMode.takeIf { it in VALID_CUSTOM_INPUT_MODES } ?: "manual",
        b30Values = normalizeSlots(b30Values),
        p30Values = normalizeSlots(p30Values),
        customB30Charts = normalizeChartSlots(customB30Charts),
        customP30Charts = normalizeChartSlots(customP30Charts),
        playNoteCount = normalizeInteger(playNoteCount),
        playPerfectCount = normalizeInteger(playPerfectCount),
        playGoodCount = normalizeInteger(playGoodCount),
        playBadCount = normalizeInteger(playBadCount),
        playMissCount = normalizeInteger(playMissCount),
        playMaxCombo = normalizeInteger(playMaxCombo),
    )

    private fun normalizeSlots(values: List<String>): List<String> =
        List(RKS_DRAFT_SLOT_COUNT) { index -> normalizeDecimal(values.getOrNull(index).orEmpty()) }

    private fun normalizeChartSlots(values: List<CustomChartDraft>): List<CustomChartDraft> =
        List(RKS_DRAFT_SLOT_COUNT) { index -> values.getOrNull(index)?.normalized() ?: CustomChartDraft() }

    private fun normalizeDecimal(value: String): String =
        value.takeIf { it.isEmpty() || it.matches(DECIMAL_DRAFT_PATTERN) }.orEmpty()

    private fun normalizeInteger(value: String): String =
        value.takeIf { it.isEmpty() || it.matches(INTEGER_DRAFT_PATTERN) }.orEmpty()

    companion object {
        private val VALID_MODES = setOf("three_value", "growth", "score_acc")
        private val VALID_GROWTH_METRICS = setOf("acc", "rks")
        private val VALID_CUSTOM_RANKINGS = setOf("b30", "p30")
        private val VALID_CUSTOM_INPUT_MODES = setOf("manual", "catalog")
        private val DECIMAL_DRAFT_PATTERN = Regex("\\d{0,3}(\\.\\d{0,8})?")
        private val INTEGER_DRAFT_PATTERN = Regex("\\d{0,7}")
    }
}
