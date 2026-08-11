package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

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
    val b30Values: List<String> = List(RKS_DRAFT_SLOT_COUNT) { "" },
    val p30Values: List<String> = List(RKS_DRAFT_SLOT_COUNT) { "" },
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
        b30Values = normalizeSlots(b30Values),
        p30Values = normalizeSlots(p30Values),
    )

    private fun normalizeSlots(values: List<String>): List<String> =
        List(RKS_DRAFT_SLOT_COUNT) { index -> normalizeDecimal(values.getOrNull(index).orEmpty()) }

    private fun normalizeDecimal(value: String): String =
        value.takeIf { it.isEmpty() || it.matches(DECIMAL_DRAFT_PATTERN) }.orEmpty()

    companion object {
        private val VALID_MODES = setOf("three_value", "growth", "custom")
        private val VALID_GROWTH_METRICS = setOf("acc", "rks")
        private val VALID_CUSTOM_RANKINGS = setOf("b30", "p30")
        private val DECIMAL_DRAFT_PATTERN = Regex("\\d{0,3}(\\.\\d{0,8})?")
    }
}

private const val RKS_DRAFT_SLOT_COUNT = 30
