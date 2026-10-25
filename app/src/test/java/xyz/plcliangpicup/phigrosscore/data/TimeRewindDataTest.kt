package xyz.plcliangpicup.phigrosscore.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeRewindDataTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun oldConstantsAreAnOverlayAndRemoteUpdatesKeepTheirNormalValues() {
        val catalog = SongCatalog(context)
        assertEquals(1037, catalog.rewindConstants.values.values.sumOf { it.size })
        assertEquals(16.4, catalog.rewindConstants.values["DesultorySignals.technoplanet"]!!["IN"]!!, 0.0)
        assertEquals(16.3, catalog.rewindConstants.values["ExoplanetaryMirage.かめりあ"]!!["IN"]!!, 0.0)
        val id = "Glaciaxion.SunsetRay"
        val before = catalog[id]!!.chartConstants["IN"]!!
        catalog.timeRewindMode = true
        assertEquals(12.5, catalog[id]!!.chartConstants["IN"]!!, 0.0)
        val remote = RemoteSongInfo(id = id, name = "Glaciaxion", chartConstants = RemoteChartConstants(inLevel = 13.1))
        catalog.applyRemoteCatalog(RemoteSongCatalog("test", listOf(remote)))
        assertEquals(12.5, catalog[id]!!.chartConstants["IN"]!!, 0.0)
        assertEquals(12.5, catalog.constantTableEntries().first { it.chart.difficulty == "IN" }.chart.chartConstant!!, 0.0)
        catalog.timeRewindMode = false
        assertEquals(13.1, catalog[id]!!.chartConstants["IN"]!!, 0.0)
        assertEquals(before, SongCatalog(context)[id]!!.chartConstants["IN"]!!, 0.0)
        assertFalse(SongCatalog(context).timeRewindMode)
    }

    @Test fun legacySnapshotRecalculatesEveryScoreAndUsesOnePhiPlusNineteenBest() {
        val catalog = SongCatalog(context)
        val sourceRecords = catalog.constantTableEntries().take(40).mapIndexed { i, entry ->
            ScoreSnapshotEntry(entry.song.id, entry.song.name, entry.chart.difficulty,
                if (i % 5 == 0) 1_000_000 else 990_000, if (i % 5 == 0) 100.0 else 99.0,
                99.0, true, 99.0, 99.9, "target_acc")
        }
        val source = B30Snapshot(99.0, emptyList(), emptyMap(), cachedAtEpochMs = 0, scoreRecords = sourceRecords)
        val old = catalog.rewindConstants.snapshot(source) { catalog[it] }
        old.scoreRecords.forEach { record ->
            val constant = catalog.rewindConstants.values[record.songId]!![record.difficulty]!!
            assertEquals(constant, record.chartConstant!!, 0.0)
            assertEquals(calculateChartRankingScore(record.accuracy, constant), record.rks, 1e-10)
            assertNull(record.pushAcc)
        }
        assertEquals(1, old.items.count { it.section == "AP" })
        assertEquals(19, old.items.count { it.section == "BEST" })
        assertEquals(old.items.sumOf { it.rks } / 20.0, old.totalRks, 1e-10)
        assertEquals(99.0, source.totalRks, 0.0)
        assertTrue(source.scoreRecords.all { it.chartConstant == 99.0 })
        val glaciaxion = ScoreSnapshotEntry("Glaciaxion.SunsetRay", "Glaciaxion", "IN", 1_000_000, 100.0, 99.0, true, 99.0)
        assertEquals(1.25, calculateTimeRewindRks(listOf(catalog.rewindConstants.record(glaciaxion)!!)), 1e-10)
    }

    @Test fun rewindImagesNeverOverwriteNormalImageFiles() = runBlocking {
        val cache = CacheStore(context, Json { ignoreUnknownKeys = true })
        val normal = cache.saveImage(byteArrayOf(1, 2), RankingImageKind.B30)
        val rewind = cache.saveImage(byteArrayOf(3, 4), RankingImageKind.B30, timeRewind = true)
        assertNotEquals(normal, rewind)
        assertEquals("b19-p1-v2.png", rewind.name)
        assertArrayEquals(byteArrayOf(1, 2), normal.readBytes())
        assertArrayEquals(byteArrayOf(3, 4), rewind.readBytes())
    }

    @Test fun p30ApiRejectsRewindBeforeNetworkAccess() = runBlocking {
        val api = PhiApiClient("http://127.0.0.1:9", Json { ignoreUnknownKeys = true })
        val error = runCatching {
            api.renderP30("unused", 1440, B30ImageStyle.CLASSIC, false, timeRewind = true)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("时间回溯模式不支持 P30", error!!.message)
    }

    @Test fun phiTiesUseChartIdentityInsteadOfTitlesOrInputOrder() {
        fun ap(id: String, name: String, difficulty: String, constant: Double, score: Int = 1_000_000) =
            ScoreSnapshotEntry(id, name, difficulty, score, 100.0, constant, true, constant)
        val records = listOf(ap("Z.song", "Alpha", "IN", 16.0),
            ap("A.song", "Zulu", "AT", 16.0), ap("A.song", "Zulu", "IN", 16.0),
            ap("0.lower", "First", "AT", 15.9), ap("0.not-ap", "First", "AT", 17.0, 999_999))
        repeat(40) { seed ->
            val phi = selectTimeRewindPhi(records.shuffled(kotlin.random.Random(seed)))!!
            assertEquals("A.song", phi.songId)
            assertEquals("IN", phi.difficulty)
        }
        assertNull(selectTimeRewindPhi(records.filter { it.score != 1_000_000 }))
    }

    @Test fun snapshotP1UsesTheSameIdentityRuleAfterApplyingOldConstants() {
        val catalog = SongCatalog(context)
        val group = catalog.rewindConstants.values.flatMap { (id, charts) -> charts.map { (difficulty, constant) ->
            Triple(id, difficulty, constant)
        } }.groupBy { it.third }.filterValues { charts -> charts.map { it.first }.distinct().size > 1 }.maxBy { it.key }.value
        val expectedId = group.minOf { it.first }
        val expectedDifficulty = listOf("EZ", "HD", "IN", "AT").first { d -> group.any { it.first == expectedId && it.second == d } }
        val records = group.map { (id, difficulty, _) -> ScoreSnapshotEntry(id,
            if (id == expectedId) "Zulu" else "Alpha", difficulty, 1_000_000, 100.0, 999.0, true, 999.0) }
        for (input in listOf(records, records.reversed())) {
            val snapshot = catalog.rewindConstants.snapshot(B30Snapshot(0.0, emptyList(), emptyMap(),
                cachedAtEpochMs = 0, scoreRecords = input)) { catalog[it] }
            val phi = snapshot.items.single { it.section == "AP" }
            assertEquals(expectedId, phi.songId)
            assertEquals(expectedDifficulty, phi.difficulty)
        }
    }
}
