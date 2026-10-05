package xyz.plcliangpicup.phigrosscore.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PracticeNoiseTest {
    private fun point(x: Float, y: Float) = PracticeNoisePoint(x, y)
    private fun area(left: Float = .1f, bottom: Float = .1f, right: Float = .9f, top: Float = .9f,
        subtract: Boolean = false, start: Double = 0.0, end: Double = 10.0) = PracticeBlockArea(
        point(right, top), point(left, bottom), start, start + 1.0, end - 1.0, end, subtract)

    @Test fun parsesOfficialNoiseWithoutBeatConversionOrCoordinateClamping() {
        val json = """{
          "blockAreaList":[{"topRightPercentage":{"x":1.2,"y":1},"bottomLeftPercentage":{"x":-0.2,"y":0},
            "appearTime":60.3681,"enableTime":63.312885,"disableTime":66.25767,"disappearTime":67,
            "isSubtract":true,
            "moveEvents":[{"endPosition":{"x":0.9,"y":0.5},"time":64,"easeTypeX":2,"easeTypeY":13}],
            "rotateEvents":[{"anchor":{"x":0.4,"y":0.3},"time":65,"easeType":14,"rotation":-90}],
            "scaleEvents":[{"anchor":{"x":0.6,"y":0.7},"time":63,"easeTypeX":5,"easeTypeY":1,"scale":{"x":3.8,"y":0}}]}],
          "judgeLineList":[],"META":{"name":"noise","offset":125},"BPMList":[{"bpm":163,"startTime":[0,0,1]}]
        }"""
        val chart = PracticeChartParser.parse(json, File("music"), File("image"))
        val block = chart.blockAreas.single()
        assertEquals(60.3681, block.appearTime, 0.0)
        assertEquals(63.312885, block.enableTime, 0.0)
        assertEquals(-.2f, block.bottomLeftPercentage.x, 0f)
        assertEquals(1.2f, block.topRightPercentage.x, 0f)
        assertTrue(block.isSubtract)
        assertEquals(13, block.moveEvents.single().easeTypeY)
        assertEquals(64.0, block.moveEvents.single().time, 0.0)
        assertEquals(-90f, block.rotateEvents.single().rotation, 0f)
        assertEquals(14, block.rotateEvents.single().easeType)
        assertEquals(point(3.8f, 0f), block.scaleEvents.single().scale)
        assertEquals(.125, chart.offsetSeconds, 0.0)
    }

    @Test fun missingNoiseIsBackwardCompatibleAndMalformedNoiseIsRejected() {
        assertTrue(PracticeChartParser.parse("""{"judgeLineList":[],"META":{}}""", File("m"), File("i")).blockAreas.isEmpty())
        assertThrows(Exception::class.java) {
            PracticeChartParser.parse("""{"judgeLineList":[],"blockAreaList":"wrong type"}""", File("m"), File("i"))
        }
    }

    @Test fun usesOfficialSampledEasingAndConstantCurves() {
        assertEquals(.00005f, practiceNoiseEasing(1, .005f), .00000001f)
        assertEquals(.75f, practiceNoiseEasing(2, .5f), .000001f)
        assertEquals(.5f, practiceNoiseEasing(3, .5f), .000001f)
        assertEquals(.03125f, practiceNoiseEasing(10, .5f), .000001f)
        assertEquals(0f, practiceNoiseEasing(13, 1f), 0f)
        assertEquals(1f, practiceNoiseEasing(14, 0f), 0f)
        assertEquals(0f, practiceNoiseEasing(0, -1f), 0f)
        assertEquals(1f, practiceNoiseEasing(0, 2f), 0f)
    }

    @Test fun scalesThenRotatesThenAddsMovementWithAbsoluteScreenAnchors() {
        val block = area(.1f, .1f, .3f, .3f).copy(
            scaleEvents = listOf(PracticeBlockScaleEvent(point(.1f, .1f), 0.0, scale = point(1f, 1f)),
                PracticeBlockScaleEvent(point(.1f, .1f), 1.0, scale = point(3f, 3f))),
            rotateEvents = listOf(PracticeBlockRotateEvent(point(.5f, .5f), 0.0, rotation = 0f),
                PracticeBlockRotateEvent(point(.5f, .5f), 1.0, rotation = 180f)),
            moveEvents = listOf(PracticeBlockMoveEvent(point(.2f, .2f), 0.0),
                PracticeBlockMoveEvent(point(.4f, .2f), 1.0)))
        val region = PracticeNoiseRuntime(listOf(block)).frame(.5, 1000f, 1000f).regions.single()
        assertEquals(800f, region.center.x, .0001f)
        assertEquals(300f, region.center.y, .0001f)
        assertEquals(200f, region.halfSize.x, .0001f)
        assertEquals(90f, region.rotationDegrees, .0001f)
        assertEquals(4, region.corners.size)
        assertTrue(region.contains(800f, 300f))
        assertFalse(region.contains(800f, 600f))
    }

    @Test fun completedScaleSegmentsRetainTheirOwnAnchorsAndZeroScalesStayFinite() {
        val block = area(.1f, .1f, .3f, .3f).copy(scaleEvents = listOf(
            PracticeBlockScaleEvent(point(.1f, .1f), 0.0, scale = point(1f, 1f)),
            PracticeBlockScaleEvent(point(.8f, .8f), 1.0, scale = point(2f, 2f)),
            PracticeBlockScaleEvent(point(.8f, .8f), 2.0, scale = point(4f, 4f))))
        val region = PracticeNoiseRuntime(listOf(block)).frame(1.5, 1000f, 1000f).regions.single()
        assertEquals(50f, region.center.x, .0002f)
        assertEquals(300f, region.halfSize.x, .0002f)
        val zero = block.copy(scaleEvents = listOf(
            PracticeBlockScaleEvent(point(.1f, .1f), 0.0, scale = point(0f, 0f)),
            PracticeBlockScaleEvent(point(.1f, .1f), 1.0, scale = point(1f, 1f))))
        val safe = PracticeNoiseRuntime(listOf(zero)).frame(.5, 1000f, 1000f).regions.single()
        assertTrue(safe.center.x.isFinite())
        assertEquals(200f, safe.center.x, .0001f)
    }

    @Test fun unionNormalAndParitySubtractUseXorIncludingSubtractOnlyRegions() {
        val normal = area()
        val subtract = area(.3f, .3f, .7f, .7f, subtract = true)
        fun frame(vararg blocks: PracticeBlockArea) = PracticeNoiseRuntime(blocks.toList()).frame(3.0, 1000f, 1000f)
        assertTrue(frame(normal, normal).blocksAt(500f, 500f))
        assertFalse(frame(normal, subtract).blocksAt(500f, 500f))
        assertTrue(frame(normal, subtract, subtract).blocksAt(500f, 500f))
        assertTrue(frame(subtract).blocksAt(500f, 500f))
        assertFalse(frame(subtract, subtract).blocksAt(500f, 500f))
    }

    @Test fun inputRequiresOriginalAndInsetMasksAndCapsInsetForThinRegions() {
        val normal = PracticeNoiseRuntime(listOf(area())).frame(3.0, 1000f, 1000f)
        assertFalse(normal.blocksAt(125f, 500f))
        assertTrue(normal.blocksAt(151f, 500f))
        val subtract = PracticeNoiseRuntime(listOf(area(.3f, .3f, .7f, .7f, subtract = true))).frame(3.0, 1000f, 1000f)
        assertFalse(subtract.blocksAt(275f, 500f)) // expanded mask alone must not block
        assertTrue(subtract.blocksAt(310f, 500f))
        val thin = PracticeNoiseRuntime(listOf(area(.45f, .1f, .55f, .9f))).frame(3.0, 1000f, 1000f)
        assertEquals(25f, thin.regions.single().touchInset.x, .0001f)
        assertTrue(thin.blocksAt(500f, 500f))
        assertFalse(thin.blocksAt(460f, 500f))
    }

    @Test fun blockedFingerRemainsBlockedAfterMovingOutOrAreaDisablingUntilLift() {
        val runtime = PracticeNoiseRuntime(listOf(area()))
        val frame = runtime.frame(3.0, 1000f, 1000f)
        assertEquals(setOf(7L), runtime.updateTouches(frame, mapOf(7L to point(500f, 500f), 8L to point(10f, 10f))))
        assertEquals(setOf(7L), runtime.updateTouches(frame, mapOf(7L to point(10f, 10f), 8L to point(10f, 10f))))
        assertEquals(setOf(7L), runtime.updateTouches(runtime.frame(10.0, 1000f, 1000f), mapOf(7L to point(10f, 10f))))
        assertTrue(runtime.isFingerBlocked(7L))
        assertTrue(runtime.updateTouches(frame, emptyMap()).isEmpty())
        assertTrue(runtime.updateTouches(frame, mapOf(7L to point(10f, 10f))).isEmpty())
        runtime.updateTouches(frame, mapOf(7L to point(500f, 500f)))
        runtime.resetTouches()
        assertFalse(runtime.isFingerBlocked(7L))
    }

    @Test fun visibilityAndActivityAreHalfOpenAndInvalidVisibilityIsOffscreen() {
        val runtime = PracticeNoiseRuntime(listOf(area(start = 2.0, end = 6.0)))
        assertTrue(runtime.frame(1.999, 1000f, 1000f).regions.isEmpty())
        assertEquals(PracticeNoiseState.DISABLED, runtime.frame(2.0, 1000f, 1000f).regions.single().state)
        assertEquals(PracticeNoiseState.READY, runtime.frame(2.5, 1000f, 1000f).regions.single().state)
        assertEquals(PracticeNoiseState.ACTIVE, runtime.frame(3.0, 1000f, 1000f).regions.single().state)
        assertEquals(PracticeNoiseState.DISABLED, runtime.frame(5.0, 1000f, 1000f).regions.single().state)
        assertTrue(runtime.frame(6.0, 1000f, 1000f).regions.isEmpty())
        val invalid = area().copy(appearTime = 10.0, disappearTime = 0.0)
        assertTrue(PracticeNoiseRuntime(listOf(invalid)).frame(3.0, 1000f, 1000f).regions.isEmpty())
    }

    @Test fun manyAreasSupportRandomSeeksAndOverlappingIntervals() {
        val blocks = (0 until 14355).map { i -> area(start = i * .01, end = i * .01 + 2.5) }
        val runtime = PracticeNoiseRuntime(blocks)
        for (time in listOf(120.123, 3.25, 100.0, 0.0, 300.0, 50.995, 2.5)) {
            val expected = blocks.indices.filter { blocks[it].appearTime <= time && time < blocks[it].disappearTime }.toSet()
            assertEquals(expected, runtime.frame(time, 1920f, 1080f).regions.map { it.id }.toSet())
        }
    }
}
