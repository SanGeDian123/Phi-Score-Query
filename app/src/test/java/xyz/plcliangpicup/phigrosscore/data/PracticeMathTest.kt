package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.*

class PracticeMathTest {
    @Test fun rpeEasingIdentifiersAndEndpoints() {
        assertEquals(.5f, practiceEasing(1,.5f), 1e-6f)
        assertEquals(.75f, practiceEasing(4,.5f), 1e-6f)
        assertEquals(.25f, practiceEasing(5,.5f), 1e-6f)
        assertEquals(.875f, practiceEasing(8,.5f), 1e-6f)
        assertEquals(.125f, practiceEasing(9,.5f), 1e-6f)
        for (i in 0..29) {
            assertEquals("easing $i start",0f,practiceEasing(i,0f),1e-5f)
            assertEquals("easing $i end",1f,practiceEasing(i,1f),1e-5f)
        }
    }

    @Test fun speedIsIntegratedInSecondsWithStopsAndReversals() {
        val travel=PracticeTravel(listOf(
            PracticeEvent(0.0,1.0,10f,10f,1),
            PracticeEvent(1.0,2.0,0f,0f,1),
            PracticeEvent(2.0,3.0,-10f,-10f,1)))
        assertEquals(10.0,travel.between(0.0,1.0),1e-6)
        assertEquals(0.0,travel.between(1.0,2.0),1e-6)
        assertEquals(-10.0,travel.between(2.0,3.0),1e-6)
        assertEquals(0.0,travel.between(0.0,3.0),1e-6)
    }

    @Test fun additiveLayersDoNotOverwriteEachOther() {
        val first=listOf(PracticeEvent(0.0,2.0,10f,10f,1,0))
        val second=listOf(PracticeEvent(0.0,2.0,5f,5f,1,1))
        assertEquals(15f,evaluatePracticeLayers(listOf(first,second),1.0,0f),1e-6f)
        assertEquals(30.0,PracticeTravel(first+second).between(0.0,2.0),1e-6)
    }

    @Test fun linearAccelerationAndClampedEasing() {
        val travel=PracticeTravel(listOf(PracticeEvent(0.0,2.0,0f,10f,1)))
        assertEquals(2.5,travel.between(0.0,1.0),1e-5)
        assertEquals(10.0,travel.between(0.0,2.0),1e-5)
        val e=PracticeEvent(0.0,1.0,0f,100f,5,easingLeft=.5f,easingRight=1f)
        assertEquals(100f*(.75f*.75f-.25f)/.75f,evaluatePracticeEvents(listOf(e),.5,0f),1e-4f)
    }

    @Test fun phoneAndTabletUseDifferentAspectWithoutDistortingSprites() {
        val phone=PracticeField(2400f,1080f)
        val tablet=PracticeField(2048f,1536f)
        assertEquals(1920f,phone.width,0f); assertEquals(240f,phone.left,0f)
        assertEquals(2048f,tablet.width,0f); assertEquals(0f,tablet.left,0f)
        assertEquals(16f/9f,phone.width/phone.height,1e-6f)
        assertEquals(4f/3f,tablet.width/tablet.height,1e-6f)
        assertEquals(phone.noteWidth/phone.width,tablet.noteWidth/tablet.width,1e-6f)
    }

    @Test fun verticalJudgementIgnoresNormalDistanceAtEveryLineAngle() {
        for (rotation in listOf(0f,35f,90f,180f,270f)) {
            val a=Math.toRadians(rotation.toDouble())
            for (normal in listOf(-1200f,0f,1500f)) {
                val x=500f+100*cos(a).toFloat()-normal*sin(a).toFloat()
                val y=400f+100*sin(a).toFloat()+normal*cos(a).toFloat()
                assertEquals(0f,practiceTangentDistance(x,y,500f,400f,rotation,100f),.001f)
                assertEquals(100f,practiceTangentDistance(x,y,500f,400f,rotation,0f),.001f)
            }
        }
    }

    @Test fun scoreAndRksBoundaryCases() {
        val ap=calculatePlayScoreAndAccuracy(2077,2077,0,0,0,2077)
        assertEquals(1_000_000,ap.score);assertEquals(100.0,ap.accuracy,0.0)
        val good=calculatePlayScoreAndAccuracy(1,0,1,0,0,1)
        assertEquals(685000,good.score);assertEquals(65.0,good.accuracy,0.0)
        assertEquals(0.0,calculateChartRks(17.9,69.99),0.0)
        assertEquals(17.9,calculateChartRks(17.9,100.0),1e-9)
    }

    @Test fun actualExoplanetaryArchiveHasCompleteGeometryAndVariableHolds() {
        val path=System.getenv("PSQ_PRACTICE_PEZ")
        assumeTrue("Set PSQ_PRACTICE_PEZ to run the real chart regression",path!=null)
        val json=ZipFile(path!!).use { z -> z.getInputStream(z.getEntry("chart.json")).bufferedReader().readText() }
        val chart=PracticeChartParser.parse(json,File("music.wav"),File("illustration.png"))
        assertEquals(66,chart.lines.size)
        assertEquals(2077,chart.notes.count { !it.fake })
        assertEquals(146,chart.notes.count { !it.above })
        val holds=chart.notes.filter { it.type==PracticeNoteType.HOLD }
        assertTrue(holds.isNotEmpty())
        assertTrue(holds.map { round((it.endSeconds-it.startSeconds)*1000) }.distinct().size>5)
        holds.forEach { n ->
            val line=chart.lines[n.lineIndex]
            val a=practiceTravelPixels(line,n.startSeconds-.3,n.startSeconds,1080f)
            val b=practiceTravelPixels(line,n.startSeconds-.3,n.endSeconds,1080f)
            assertTrue(a.isFinite() && b.isFinite())
        }
        chart.notes.forEach { n ->
            assertEquals(0f,practiceTravelPixels(chart.lines[n.lineIndex],n.startSeconds,n.startSeconds,1080f),0f)
        }
        assertTrue(chart.lines.any { it.speed.any { e -> e.start<0f } })
        assertTrue(chart.lines.any { it.alpha.any { event -> event.start == 0f || event.end == 0f } })
    }
}
