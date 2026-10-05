package xyz.plcliangpicup.phigrosscore.data

import com.google.gson.stream.JsonReader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Noise times are chart seconds, and percentages deliberately remain unclamped. */
data class PracticeNoisePoint(val x: Float, val y: Float)
data class PracticeBlockMoveEvent(val endPosition: PracticeNoisePoint, val time: Double,
    val easeTypeX: Int = 0, val easeTypeY: Int = 0)
data class PracticeBlockRotateEvent(val anchor: PracticeNoisePoint, val time: Double,
    val easeType: Int = 0, val rotation: Float = 0f)
data class PracticeBlockScaleEvent(val anchor: PracticeNoisePoint, val time: Double,
    val easeTypeX: Int = 0, val easeTypeY: Int = 0, val scale: PracticeNoisePoint)
data class PracticeBlockArea(
    val topRightPercentage: PracticeNoisePoint,
    val bottomLeftPercentage: PracticeNoisePoint,
    val appearTime: Double,
    val enableTime: Double,
    val disableTime: Double,
    val disappearTime: Double,
    val isSubtract: Boolean = false,
    val moveEvents: List<PracticeBlockMoveEvent> = emptyList(),
    val rotateEvents: List<PracticeBlockRotateEvent> = emptyList(),
    val scaleEvents: List<PracticeBlockScaleEvent> = emptyList(),
)

internal fun readPracticeBlockArea(input: JsonReader): PracticeBlockArea {
    val area = input.practiceObject()
    fun events(key: String) = (area[key] as? JsonArray)?.map {
        require(it is JsonObject) { "噪域事件格式无效" }; it
    } ?: emptyList()
    return PracticeBlockArea(
        topRightPercentage = area.noisePoint("topRightPercentage"),
        bottomLeftPercentage = area.noisePoint("bottomLeftPercentage"),
        appearTime = area.noiseNumber("appearTime"),
        enableTime = area.noiseNumber("enableTime"),
        disableTime = area.noiseNumber("disableTime"),
        disappearTime = area.noiseNumber("disappearTime"),
        isSubtract = area["isSubtract"]?.jsonPrimitive?.booleanOrNull ?: false,
        moveEvents = events("moveEvents").map { PracticeBlockMoveEvent(it.noisePoint("endPosition"),
            it.noiseNumber("time"), it.noiseInt("easeTypeX"), it.noiseInt("easeTypeY")) }.sortedBy { it.time },
        rotateEvents = events("rotateEvents").map { PracticeBlockRotateEvent(it.noisePoint("anchor"),
            it.noiseNumber("time"), it.noiseInt("easeType"), it.noiseNumber("rotation").toFloat()) }.sortedBy { it.time },
        scaleEvents = events("scaleEvents").map { PracticeBlockScaleEvent(it.noisePoint("anchor"),
            it.noiseNumber("time"), it.noiseInt("easeTypeX"), it.noiseInt("easeTypeY"), it.noisePoint("scale")) }.sortedBy { it.time },
    )
}

private fun JsonObject.noiseNumber(key: String): Double {
    val value = this[key]?.jsonPrimitive?.doubleOrNull
    require(value != null && value.isFinite() && value.toFloat().isFinite()) { "噪域 $key 数据无效" }
    return value
}
private fun JsonObject.noiseInt(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.noisePoint(key: String): PracticeNoisePoint {
    val point = this[key] as? JsonObject ?: error("噪域 $key 坐标缺失")
    return PracticeNoisePoint(point.noiseNumber("x").toFloat(), point.noiseNumber("y").toFloat())
}

/** APK GetEase (0x1CC95E4 / 0x1CC92FC): 101 samples, then linear sample interpolation. */
fun practiceNoiseEasing(type: Int, progress: Float): Float {
    val scaled = progress.coerceIn(0f, 1f) * 100f
    val index = scaled.toInt().coerceIn(0, 100)
    if (index == 100) return noiseEaseSample(type, 100)
    val first = noiseEaseSample(type, index)
    return first + (noiseEaseSample(type, index + 1) - first) * (scaled - index)
}
private fun noiseEaseSample(type: Int, index: Int): Float {
    val x = index / 100f
    if (type == 13) return 0f
    if (type == 14) return 1f
    if (type !in 1..12) return x
    val exponent = (type + 2) / 3 + 1
    return when ((type - 1) % 3) {
        0 -> x.pow(exponent)
        1 -> 1f - (1f - x).pow(exponent)
        else -> if (index < 50) (index * 2 / 100f).pow(exponent) * .5f
            else if (index == 100) 1f else .5f + (1f - (1f - (index - 50) * 2 / 100f).pow(exponent)) * .5f
    }
}

enum class PracticeNoiseState { DISABLED, READY, ACTIVE }

/** Pixel coordinates use a lower-left origin, X right and Y up. */
data class PracticeNoiseRegion(
    val id: Int,
    val state: PracticeNoiseState,
    val isSubtract: Boolean,
    val center: PracticeNoisePoint,
    val halfSize: PracticeNoisePoint,
    val rotationDegrees: Float,
    val touchInset: PracticeNoisePoint,
    val minimumSize: Float,
) {
    private val radians = Math.toRadians(rotationDegrees.toDouble())
    private val cosine = cos(radians).toFloat()
    private val sine = sin(radians).toFloat()
    val corners: List<PracticeNoisePoint> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(point(-halfSize.x, -halfSize.y), point(halfSize.x, -halfSize.y),
            point(halfSize.x, halfSize.y), point(-halfSize.x, halfSize.y))
    }
    private fun point(x: Float, y: Float) = PracticeNoisePoint(center.x + x * cosine - y * sine,
        center.y + x * sine + y * cosine)
    fun contains(x: Float, y: Float, withTouchTolerance: Boolean = false): Boolean {
        if (halfSize.x * 2f < minimumSize || halfSize.y * 2f < minimumSize) return false
        val dx = x - center.x
        val dy = y - center.y
        val localX = dx * cosine + dy * sine
        val localY = -dx * sine + dy * cosine
        val sign = if (isSubtract) 1f else -1f
        val halfX = halfSize.x + if (withTouchTolerance) sign * touchInset.x else 0f
        val halfY = halfSize.y + if (withTouchTolerance) sign * touchInset.y else 0f
        return abs(localX) <= halfX && abs(localY) <= halfY
    }
}

data class PracticeNoiseFrame(val timeSeconds: Double, val regions: List<PracticeNoiseRegion>) {
    /** APK TryGetBlockingBlock (0x1D735D8): both original and inset masks must block. */
    fun blocksAt(x: Float, y: Float): Boolean {
        var originalNormal = false
        var tolerantNormal = false
        var originalSubtract = false
        var tolerantSubtract = false
        for (region in regions) {
            if (region.state != PracticeNoiseState.ACTIVE) continue
            if (region.contains(x, y)) {
                if (region.isSubtract) originalSubtract = !originalSubtract else originalNormal = true
            }
            if (region.contains(x, y, withTouchTolerance = true)) {
                if (region.isSubtract) tolerantSubtract = !tolerantSubtract else tolerantNormal = true
            }
        }
        return (originalNormal xor originalSubtract) && (tolerantNormal xor tolerantSubtract)
    }
}

/**
 * Seek-safe interval index avoids scanning all 14,355 ハテ AT areas for each frame.
 * Keep one runtime per player session. Touch IDs stay blocked until absent from raw touches.
 */
class PracticeNoiseRuntime(private val areas: List<PracticeBlockArea>) {
    private val index = NoiseIntervalIndex(areas)
    private val blockedIds = mutableSetOf<Long>()
    val blockedFingerIds: Set<Long> get() = blockedIds.toSet()
    fun isFingerBlocked(id: Long): Boolean = id in blockedIds

    fun frame(timeSeconds: Double, widthPx: Float, heightPx: Float): PracticeNoiseFrame {
        if (!timeSeconds.isFinite() || widthPx <= 0f || heightPx <= 0f || !widthPx.isFinite() || !heightPx.isFinite()) {
            return PracticeNoiseFrame(timeSeconds, emptyList())
        }
        val current = index.at(timeSeconds)
        return PracticeNoiseFrame(timeSeconds, current.map { geometry(it, areas[it], timeSeconds, widthPx, heightPx) })
    }

    fun updateTouches(frame: PracticeNoiseFrame, rawTouches: Map<Long, PracticeNoisePoint>): Set<Long> {
        blockedIds.retainAll(rawTouches.keys)
        for ((id, point) in rawTouches) {
            if (id !in blockedIds && frame.blocksAt(point.x, point.y)) blockedIds += id
        }
        return blockedIds.toSet()
    }
    fun resetTouches() = blockedIds.clear()

    private fun geometry(id: Int, area: PracticeBlockArea, time: Double, width: Float, height: Float): PracticeNoiseRegion {
        val baseSize = PracticeNoisePoint((area.topRightPercentage.x - area.bottomLeftPercentage.x) * width,
            (area.topRightPercentage.y - area.bottomLeftPercentage.y) * height)
        val originalCenter = PracticeNoisePoint((area.topRightPercentage.x + area.bottomLeftPercentage.x) * .5f * width,
            (area.topRightPercentage.y + area.bottomLeftPercentage.y) * .5f * height)
        var center = originalCenter
        var size = baseSize
        var rotation = 0f
        val scaleIndex = noiseEventIndex(area.scaleEvents, time) { it.time }
        if (scaleIndex >= 0) {
            for (i in 0 until scaleIndex) {
                val previous = area.scaleEvents[i]
                val next = area.scaleEvents[i + 1]
                center = scaleAround(center, previous.anchor.pixels(width, height),
                    safeNoiseDiv(next.scale.x, previous.scale.x), safeNoiseDiv(next.scale.y, previous.scale.y))
            }
            val event = area.scaleEvents[scaleIndex]
            val next = area.scaleEvents.getOrNull(scaleIndex + 1)
            val scale = if (next == null) event.scale else PracticeNoisePoint(
                interpolate(event.scale.x, next.scale.x, progress(time, event.time, next.time, event.easeTypeX)),
                interpolate(event.scale.y, next.scale.y, progress(time, event.time, next.time, event.easeTypeY)))
            if (next != null) center = scaleAround(center, event.anchor.pixels(width, height),
                safeNoiseDiv(scale.x, event.scale.x), safeNoiseDiv(scale.y, event.scale.y))
            size = PracticeNoisePoint(baseSize.x * scale.x, baseSize.y * scale.y)
        }
        val rotateIndex = noiseEventIndex(area.rotateEvents, time) { it.time }
        if (rotateIndex >= 0) {
            for (i in 0 until rotateIndex) {
                val previous = area.rotateEvents[i]
                val next = area.rotateEvents[i + 1]
                center = rotateAround(center, previous.anchor.pixels(width, height), next.rotation - previous.rotation)
            }
            val event = area.rotateEvents[rotateIndex]
            val next = area.rotateEvents.getOrNull(rotateIndex + 1)
            rotation = if (next == null) event.rotation else interpolate(event.rotation, next.rotation,
                progress(time, event.time, next.time, event.easeType))
            if (next != null) center = rotateAround(center, event.anchor.pixels(width, height), rotation - event.rotation)
        }
        val moveIndex = noiseEventIndex(area.moveEvents, time) { it.time }
        if (moveIndex >= 0) {
            val event = area.moveEvents[moveIndex]
            val next = area.moveEvents.getOrNull(moveIndex + 1)
            val move = if (next == null) event.endPosition else PracticeNoisePoint(
                interpolate(event.endPosition.x, next.endPosition.x, progress(time, event.time, next.time, event.easeTypeX)),
                interpolate(event.endPosition.y, next.endPosition.y, progress(time, event.time, next.time, event.easeTypeY)))
            center = PracticeNoisePoint(center.x + move.x * width - originalCenter.x,
                center.y + move.y * height - originalCenter.y)
        }
        val fullX = abs(size.x)
        val fullY = abs(size.y)
        val state = if (time >= area.enableTime && time < area.disableTime) PracticeNoiseState.ACTIVE
            else if (time < area.enableTime && time >= area.enableTime - .5) PracticeNoiseState.READY
            else PracticeNoiseState.DISABLED
        return PracticeNoiseRegion(id, state, area.isSubtract, center,
            PracticeNoisePoint(fullX * .5f, fullY * .5f), rotation,
            PracticeNoisePoint((height * .05f).coerceAtMost(fullX * .25f), (height * .05f).coerceAtMost(fullY * .25f)),
            // Unity's 0.0001 world-unit degenerate cutoff, with a 10-unit orthographic height.
            minimumSize = height * .00001f)
    }
}

private fun PracticeNoisePoint.pixels(width: Float, height: Float) = PracticeNoisePoint(x * width, y * height)
private fun interpolate(start: Float, end: Float, amount: Float) = start + (end - start) * amount
private fun progress(time: Double, start: Double, end: Double, ease: Int) = practiceNoiseEasing(ease,
    if (end <= start) 1f else ((time - start) / (end - start)).toFloat())
private fun safeNoiseDiv(numerator: Float, denominator: Float): Float =
    if (abs(denominator) < maxOf(abs(denominator) * .000001f, Float.MIN_VALUE * 8f)) 1f else numerator / denominator
private fun scaleAround(point: PracticeNoisePoint, anchor: PracticeNoisePoint, x: Float, y: Float) =
    PracticeNoisePoint(anchor.x + (point.x - anchor.x) * x, anchor.y + (point.y - anchor.y) * y)
private fun rotateAround(point: PracticeNoisePoint, anchor: PracticeNoisePoint, degrees: Float): PracticeNoisePoint {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val x = point.x - anchor.x
    val y = point.y - anchor.y
    return PracticeNoisePoint(anchor.x + x * c - y * s, anchor.y + x * s + y * c)
}
private inline fun <T> noiseEventIndex(events: List<T>, time: Double, eventTime: (T) -> Double): Int {
    var low = 0
    var high = events.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (eventTime(events[mid]) <= time) { found = mid; low = mid + 1 } else high = mid - 1
    }
    return found
}

private class NoiseIntervalIndex(private val areas: List<PracticeBlockArea>) {
    private class Node(val split: Double, val starts: List<Int>, val ends: List<Int>, val left: Node?, val right: Node?)
    private val root = build(areas.indices.filter { areas[it].appearTime < areas[it].disappearTime })
    private fun build(ids: List<Int>): Node? {
        if (ids.isEmpty()) return null
        val split = ids.map { (areas[it].appearTime + areas[it].disappearTime) * .5 }.sorted()[ids.size / 2]
        val left = mutableListOf<Int>()
        val right = mutableListOf<Int>()
        val crossing = mutableListOf<Int>()
        for (id in ids) when {
            areas[id].disappearTime <= split -> left += id
            areas[id].appearTime > split -> right += id
            else -> crossing += id
        }
        return Node(split, crossing.sortedBy { areas[it].appearTime }, crossing.sortedByDescending { areas[it].disappearTime },
            build(left), build(right))
    }
    fun at(time: Double): List<Int> = mutableListOf<Int>().also { query(root, time, it) }
    private fun query(node: Node?, time: Double, found: MutableList<Int>) {
        if (node == null) return
        if (time < node.split) {
            for (id in node.starts) { if (areas[id].appearTime > time) break; found += id }
            query(node.left, time, found)
        } else {
            for (id in node.ends) { if (areas[id].disappearTime <= time) break; found += id }
            query(node.right, time, found)
        }
    }
}
