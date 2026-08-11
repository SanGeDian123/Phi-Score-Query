package xyz.plcliangpicup.phigrosscore.data

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs

data class ChartRksSolution(
    val chartConstant: Double,
    val accuracy: Double,
    val chartRks: Double,
)

data class AccountRksProjection(
    val currentChartRks: Double,
    val targetChartRks: Double,
    val currentAccountRks: Double,
    val accountIncrease: Double,
    val projectedAccountRks: Double,
    val best27Floor: Double,
    val best27Increase: Double,
    val ap3Increase: Double,
    val wasInBest27: Boolean,
    val entersBest27: Boolean,
    val entersAp3: Boolean,
)

/** Phigros 单曲 RKS；ACC 低于 70% 时贡献为 0。 */
fun calculateChartRks(chartConstant: Double, accuracy: Double): Double {
    require(chartConstant > 0.0) { "谱面定数必须大于 0" }
    require(accuracy in 0.0..100.0) { "ACC 必须在 0 到 100 之间" }
    if (accuracy < 70.0) return 0.0
    return ((accuracy - 55.0) / 45.0).pow(2) * chartConstant
}

/** 定数、ACC、单曲 RKS 中任意缺少一项时，根据其余两项求解。 */
fun solveChartRks(
    chartConstant: Double?,
    accuracy: Double?,
    chartRks: Double?,
): ChartRksSolution {
    require(listOf(chartConstant, accuracy, chartRks).count { it != null } == 2) {
        "请填写任意两项并留空一项"
    }
    return when {
        chartConstant == null -> {
            val acc = requireNotNull(accuracy)
            val rks = requireNotNull(chartRks)
            require(acc in 70.0..100.0) { "反推定数时 ACC 需在 70 到 100 之间" }
            require(rks >= 0.0) { "单曲 RKS 不能小于 0" }
            val factor = ((acc - 55.0) / 45.0).pow(2)
            require(factor > 0.0) { "当前 ACC 无法反推定数" }
            ChartRksSolution(rks / factor, acc, rks)
        }

        accuracy == null -> {
            val constant = chartConstant
            val rks = requireNotNull(chartRks)
            require(constant > 0.0) { "谱面定数必须大于 0" }
            require(rks in 0.0..constant) { "单曲 RKS 必须在 0 到谱面定数之间" }
            val acc = if (rks == 0.0) 70.0 else 45.0 * sqrt(rks / constant) + 55.0
            ChartRksSolution(constant, acc, rks)
        }

        else -> {
            val constant = chartConstant
            val acc = accuracy
            ChartRksSolution(constant, acc, calculateChartRks(constant, acc))
        }
    }
}

/** 使用服务器返回的真实 B27 + AP3，模拟目标成绩写入存档后的综合 RKS。 */
fun projectAccountRksIncrease(
    chartConstant: Double,
    currentAccuracy: Double? = null,
    currentChartRks: Double? = null,
    targetAccuracy: Double,
    currentAccountRks: Double,
    b30Items: List<B30Item>,
): AccountRksProjection {
    require((currentAccuracy == null) xor (currentChartRks == null)) {
        "当前 ACC 与当前单曲 RKS 请选择其一"
    }
    require(targetAccuracy in 0.0..100.0) { "目标 ACC 必须在 0 到 100 之间" }
    require(currentAccountRks >= 0.0) { "账号综合 RKS 不能小于 0" }
    require(b30Items.isNotEmpty()) { "暂无真实 B30，请先刷新成绩" }
    val current = currentChartRks ?: calculateChartRks(chartConstant, requireNotNull(currentAccuracy))
    require(current in 0.0..chartConstant) { "当前单曲 RKS 必须在 0 到谱面定数之间" }
    val target = calculateChartRks(chartConstant, targetAccuracy).coerceAtLeast(current)

    val currentBest27 = b30Items.filter { it.section.equals("BEST", ignoreCase = true) }.take(27)
    val currentAp3 = b30Items.filter { it.section.equals("AP", ignoreCase = true) }.take(3)
    val best27Simulation = simulateContribution(
        currentItems = currentBest27,
        chartConstant = chartConstant,
        currentChartRks = current,
        targetChartRks = target,
        limit = 27,
        candidateEligible = true,
    )
    val currentIsAp = currentAccuracy?.let { it >= 100.0 }
        ?: (abs(current - chartConstant) <= RKS_MATCH_TOLERANCE)
    val targetIsAp = currentIsAp || targetAccuracy >= 100.0
    val ap3Simulation = simulateContribution(
        currentItems = currentAp3,
        chartConstant = chartConstant,
        currentChartRks = current,
        targetChartRks = target,
        limit = 3,
        candidateEligible = targetIsAp,
    )
    val best27Increase = (best27Simulation.newSum - best27Simulation.currentSum).coerceAtLeast(0.0)
    val ap3Increase = (ap3Simulation.newSum - ap3Simulation.currentSum).coerceAtLeast(0.0)
    val increase = (best27Increase + ap3Increase) / 30.0
    return AccountRksProjection(
        currentChartRks = current,
        targetChartRks = target,
        currentAccountRks = currentAccountRks,
        accountIncrease = increase,
        projectedAccountRks = currentAccountRks + increase,
        best27Floor = currentBest27.lastOrNull()?.rks ?: 0.0,
        best27Increase = best27Increase / 30.0,
        ap3Increase = ap3Increase / 30.0,
        wasInBest27 = best27Simulation.removedCurrent,
        entersBest27 = best27Simulation.candidateIncluded,
        entersAp3 = ap3Simulation.candidateIncluded,
    )
}

private const val RKS_MATCH_TOLERANCE = 0.005_001

private data class ContributionSimulation(
    val currentSum: Double,
    val newSum: Double,
    val removedCurrent: Boolean,
    val candidateIncluded: Boolean,
)

private fun simulateContribution(
    currentItems: List<B30Item>,
    chartConstant: Double,
    currentChartRks: Double,
    targetChartRks: Double,
    limit: Int,
    candidateEligible: Boolean,
): ContributionSimulation {
    val currentSum = currentItems.sumOf(B30Item::rks)
    if (!candidateEligible) {
        return ContributionSimulation(currentSum, currentSum, removedCurrent = false, candidateIncluded = false)
    }
    val values = currentItems.map(B30Item::rks).toMutableList()
    val matchingIndex = currentItems.indices
        .filter { index ->
            val item = currentItems[index]
            val itemConstant = item.chartConstant
            abs(item.rks - currentChartRks) <= RKS_MATCH_TOLERANCE &&
                (itemConstant == null || abs(itemConstant - chartConstant) <= RKS_MATCH_TOLERANCE)
        }
        .minByOrNull { index -> abs(currentItems[index].rks - currentChartRks) }
    if (matchingIndex != null) values.removeAt(matchingIndex)
    values += targetChartRks
    val selected = values.sortedDescending().take(limit)
    val targetOccurrenceCount = selected.count { abs(it - targetChartRks) <= RKS_MATCH_TOLERANCE }
    val previousTargetOccurrenceCount = currentItems.count { abs(it.rks - targetChartRks) <= RKS_MATCH_TOLERANCE }
    return ContributionSimulation(
        currentSum = currentSum,
        newSum = selected.sum(),
        removedCurrent = matchingIndex != null,
        candidateIncluded = matchingIndex != null || targetOccurrenceCount > previousTargetOccurrenceCount,
    )
}

fun calculateCustomCompositeRks(values: List<Double>, denominator: Int = 30): Double {
    require(denominator > 0) { "槽位数必须大于 0" }
    require(values.size <= denominator) { "填写的数据超过 $denominator 个槽位" }
    require(values.all { it >= 0.0 }) { "单曲 RKS 不能小于 0" }
    return values.sum() / denominator.toDouble()
}
