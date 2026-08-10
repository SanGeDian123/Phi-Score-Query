package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.plcliangpicup.phigrosscore.ui.avatarAssetKey
import xyz.plcliangpicup.phigrosscore.ui.challengeModeLabel
import xyz.plcliangpicup.phigrosscore.ui.scoreUpdateText
import xyz.plcliangpicup.phigrosscore.ui.validAvatarName
import kotlin.math.abs

class ModelsTest {
    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `Phi Plugin image style persists and unknown values remain classic`() {
        assertEquals(B30ImageStyle.PHI_PLUGIN, B30ImageStyle.fromPreference("phi-plugin"))
        assertEquals(B30ImageStyle.CLASSIC, B30ImageStyle.fromPreference("unknown"))
    }

    @Test
    fun `announcement requires new bounded nonblank content`() {
        val announcement = AppAnnouncement(
            id = "notice-1",
            title = "标题",
            body = "正文",
        )

        assertTrue(announcement.isDisplayableAfter(null))
        assertTrue(!announcement.isDisplayableAfter("notice-1"))
        assertTrue(announcement.copy(title = " ").isDisplayableAfter(null).not())
        assertTrue(announcement.copy(body = "x".repeat(8_001)).isDisplayableAfter(null).not())
    }

    @Test
    fun `single song image style defaults to new design and preserves legacy selection`() {
        assertEquals(SongScoreImageStyle.DEFAULT, SongScoreImageStyle.fromPreference(null))
        assertEquals(SongScoreImageStyle.DEFAULT, SongScoreImageStyle.fromPreference("unknown"))
        assertEquals(SongScoreImageStyle.LEGACY, SongScoreImageStyle.fromPreference("legacy"))
    }

    @Test
    fun `push acc hint accepts tagged backend object`() {
        val record = decodeRecord("""{"type":"already_phi"}""")

        assertTrue(record.pushAccHint is JsonObject)
        assertEquals(
            "already_phi",
            (record.pushAccHint as JsonObject)["type"]?.let { it as JsonPrimitive }?.content,
        )
    }

    @Test
    fun `push acc hint remains compatible with legacy string and null`() {
        val legacy = decodeRecord(""""phi_only"""")
        val absent = decodeRecord("null")

        assertEquals("phi_only", (legacy.pushAccHint as JsonPrimitive).content)
        assertNull(absent.pushAccHint)
    }

    @Test
    fun `single chart ranking score matches backend formula`() {
        assertEquals(0.0, calculateChartRankingScore(69.999, 15.0), 0.0)
        assertEquals(15.0, calculateChartRankingScore(100.0, 15.0), 1e-12)
        val expected = ((98.5 - 55.0) / 45.0) * ((98.5 - 55.0) / 45.0) * 12.0
        assertTrue(abs(calculateChartRankingScore(98.5, 12.0) - expected) < 1e-12)
    }

    @Test
    fun `save comparison reports changed and newly played charts only`() {
        val previous = listOf(
            score("song-a", "IN", 980_000, 98.0),
            score("song-b", "HD", 950_000, 95.0),
        )
        val current = listOf(
            score("song-a", "IN", 990_000, 99.0),
            score("song-b", "HD", 950_000, 95.0),
            score("song-c", "AT", 970_000, 97.0),
        )

        val updated = detectUpdatedScores(previous, current)

        assertEquals(setOf("song-a", "song-c"), updated.map { it.songId }.toSet())
    }

    @Test
    fun `empty legacy baseline does not report the whole save as updated`() {
        val updated = detectUpdatedScores(emptyList(), listOf(score("song-a", "IN", 990_000, 99.0)))

        assertTrue(updated.isEmpty())
    }

    @Test
    fun `accuracy-only update is not presented as a score update`() {
        val previous = listOf(score("song-a", "IN", 995_000, 98.25))
        val current = listOf(score("song-a", "IN", 995_000, 99.10))

        val update = detectUpdatedScores(previous, current).single()

        assertTrue(update.accuracyChanged)
        assertTrue(!update.scoreChanged)
        assertEquals(995_000, update.previousScore)
        assertEquals(98.25, update.previousAccuracy ?: 0.0, 0.0)
        val text = scoreUpdateText(update)
        assertEquals("ACC 99.1000%", text)
        assertTrue(!text.contains("分数"))
    }

    @Test
    fun `score update text contains exactly the changed values`() {
        val previous = listOf(score("song-a", "IN", 980_000, 98.0))
        val scoreOnly = detectUpdatedScores(previous, listOf(score("song-a", "IN", 990_000, 98.0))).single()
        val scoreAndAcc = detectUpdatedScores(previous, listOf(score("song-a", "IN", 990_000, 99.0))).single()

        val scoreOnlyText = scoreUpdateText(scoreOnly)
        assertEquals("分数 990,000", scoreOnlyText)
        assertTrue(!scoreOnlyText.contains("ACC"))
        assertEquals("分数 990,000 · ACC 99.0000%", scoreUpdateText(scoreAndAcc))
    }

    @Test
    fun `best charts honor user limit and ranking score order`() {
        val records = listOf(
            score("song-a", "IN", 990_000, 13.2),
            score("song-b", "AT", 980_000, 15.4),
            score("song-c", "HD", 1_000_000, 14.1),
        )

        val selected = selectBestCharts(records, 2)

        assertEquals(listOf("song-b", "song-c"), selected.map { it.songId })
    }

    @Test
    fun `perfect charts contain only all perfect records`() {
        val records = listOf(
            score("song-a", "IN", 1_000_000, 13.2),
            score("song-b", "AT", 999_999, 15.4),
            score("song-c", "HD", 1_000_000, 14.1),
        )

        val selected = selectPerfectCharts(records)

        assertEquals(listOf("song-c", "song-a"), selected.map { it.songId })
        assertTrue(selected.all { it.score == 1_000_000 })
    }

    @Test
    fun `p30 rks repeats the top three perfect records before adding b27`() {
        val records = listOf(
            score("song-a", "IN", 1_000_000, 15.0),
            score("song-b", "AT", 999_999, 18.0),
            score("song-c", "HD", 1_000_000, 12.0),
            score("song-d", "IN", 1_000_000, 9.0),
            score("song-e", "EZ", 1_000_000, 6.0),
        )

        assertEquals(2.6, calculateP30Rks(records), 1e-12)
    }

    @Test
    fun `app update comparison uses version code instead of version name`() {
        val update = AppUpdateManifest(
            versionCode = 13,
            versionName = "Pre-0.9.7",
            apkUrl = "https://api.plc-liangpi-cup.xyz/app-update/app.apk",
            sha256 = "0".repeat(64),
        )

        assertTrue(update.isNewerThan(12))
        assertTrue(!update.copy(versionName = "Pre-99.0.0", versionCode = 12).isNewerThan(12))
        assertTrue(!update.copy(versionName = "Pre-99.0.0", versionCode = 11).isNewerThan(12))
    }

    @Test
    fun `backend alias search page accepts song metadata and extra chart constants`() {
        val page = json.decodeFromString<SongSearchPage>(
            """
            {
              "items": [{
                "id": "Anomaly.D_AAN",
                "name": "Anomaly",
                "composer": "D_AAN",
                "illustrator": "dummy",
                "chartConstants": {"ez": 4.0, "hd": 9.0, "in": 14.0}
              }],
              "total": 1,
              "limit": 20,
              "offset": 0,
              "hasMore": false
            }
            """.trimIndent(),
        )

        assertEquals(1, page.total)
        assertEquals("Anomaly.D_AAN", page.items.single().id)
    }

    @Test
    fun `save response decodes nickname summary avatar and challenge rank`() {
        val response = json.decodeFromString<SaveAndRksResponse>(
            """
            {
              "save": {
                "game_record": {},
                "summaryParsed": {
                  "challenge_mode_rank": 503,
                  "ranking_score": 15.4321,
                  "avatar": "Cipher : /2&//<|0"
                }
              },
              "rks": {"totalRks": 15.4321, "b30Charts": []},
              "gradeCounts": {},
              "playerNickname": "Test Player"
            }
            """.trimIndent(),
        )

        assertEquals("Test Player", response.playerNickname)
        assertEquals("Cipher : /2&//<|0", response.save.summaryParsed?.avatar)
        assertEquals(503, response.save.summaryParsed?.challengeModeRank)
    }

    @Test
    fun `leaderboard response decodes player presentation fields`() {
        val response = json.decodeFromString<LeaderboardResponse>(
            """
            {
              "items": [{
                "rank": 1,
                "alias": null,
                "nickname": "Alice",
                "user": "ab12****",
                "avatar": "Glaciaxion",
                "challengeModeRank": 502,
                "score": 16.1234,
                "updatedAt": "2026-07-19T00:00:00Z"
              }],
              "total": 1
            }
            """.trimIndent(),
        )

        val entry = response.items.single()
        assertEquals("Alice", entry.nickname)
        assertEquals("Glaciaxion", entry.avatar)
        assertEquals(502, entry.challengeModeRank)
        assertEquals(16.1234, entry.score, 0.0)
    }

    @Test
    fun `avatar asset key is stable and safe for every official name`() {
        val key = avatarAssetKey("Cipher : /2&//<|0")

        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertEquals(key, avatarAssetKey("Cipher : /2&//<|0"))
    }

    @Test
    fun `challenge mode labels use compact single character colors`() {
        assertEquals("绿12", challengeModeLabel(112))
        assertEquals("黄21", challengeModeLabel(421))
        assertEquals("红49", challengeModeLabel(349))
        assertEquals("彩51", challengeModeLabel(551))
    }

    @Test
    fun `leaderboard avatar rejects broken values before requesting an image`() {
        assertEquals("Glaciaxion", " Glaciaxion ".validAvatarName())
        assertNull("...".validAvatarName())
        assertNull("\u000e".validAvatarName())
        assertNull("  ".validAvatarName())
    }

    @Test
    fun `local song catalog parses chart constants and ignores blanks`() {
        val song = parseSongInfoCsvLine(
            "Glaciaxion.SunsetRay,Glaciaxion,SunsetRay,艾若拉,1.0,6.5,12.6,",
        )!!

        assertEquals(1.0, song.chartConstants["EZ"]!!, 0.0)
        assertEquals(6.5, song.chartConstants["HD"]!!, 0.0)
        assertEquals(12.6, song.chartConstants["IN"]!!, 0.0)
        assertNull(song.chartConstants["AT"])
    }

    @Test
    fun `local song catalog keeps quoted commas while parsing constants`() {
        val song = parseSongInfoCsvLine(
            "test.song,\"Song, Full Name\",Composer,Illustrator,2.0,8.0,,15.5",
        )!!

        assertEquals("Song, Full Name", song.name)
        assertEquals(15.5, song.chartConstants["AT"]!!, 0.0)
    }

    @Test
    fun `constant table keeps every chart and sorts by exact constant descending`() {
        val rrharil = SongInfo(
            id = "Rrharil.TeamGrimoire",
            name = "Rrhar'il",
            composer = "Team Grimoire",
            illustrator = "Illustrator",
            charts = listOf(
                SongChartInfo("IN", 16.1),
                SongChartInfo("AT", 17.6),
            ),
        )
        val distortedFate = SongInfo(
            id = "DistortedFate.Sakuzyo",
            name = "Distorted Fate",
            composer = "Sakuzyo",
            illustrator = "Illustrator",
            charts = listOf(SongChartInfo("AT", 17.4)),
        )

        val entries = buildConstantTableEntries(listOf(distortedFate, rrharil))

        assertEquals(listOf(17.6, 17.4, 16.1), entries.map { it.chart.chartConstant })
        assertEquals(listOf("AT", "AT", "IN"), entries.map { it.chart.difficulty })
        assertEquals("Rrhar'il", entries.first().song.name)
    }

    @Test
    fun `remote catalog overrides metadata while keeping bundled chart details`() {
        val bundled = SongInfo(
            id = "new.song",
            name = "Old Name",
            composer = "Old Composer",
            illustrator = "Old Illustrator",
            chapter = "Legacy Chapter",
            charts = listOf(SongChartInfo("IN", 13.0, noteCount = 900, charter = "Charter")),
        )
        val remote = RemoteSongInfo(
            id = "new.song",
            name = "Server Name",
            composer = "Server Composer",
            illustrator = "Server Illustrator",
            chartConstants = RemoteChartConstants(inLevel = 13.4, at = 15.2),
        )

        val merged = mergeRemoteSong(remote, bundled)

        assertEquals("Server Name", merged.name)
        assertEquals("Legacy Chapter", merged.chapter)
        assertEquals(13.4, merged.chartConstants["IN"]!!, 0.0)
        assertEquals(900, merged.charts.first { it.difficulty == "IN" }.noteCount)
        assertEquals("Charter", merged.charts.first { it.difficulty == "IN" }.charter)
        assertEquals(15.2, merged.charts.first { it.difficulty == "AT" }.chartConstant!!, 0.0)
    }

    @Test
    fun `best thirty selection includes three overflow records after first twenty seven`() {
        val records = (1..35).map { index ->
            score("song-$index", "IN", 1_000_000 - index, 100.0 - index / 100.0)
        }

        val selected = selectBestCharts(records, 30)

        assertEquals(30, selected.size)
        assertEquals(27, selected.take(27).size)
        assertEquals(3, selected.drop(27).size)
    }

    private fun decodeRecord(pushAccHint: String): DifficultyRecord = json.decodeFromString(
        """
        {
          "difficulty":"IN",
          "score":1000000,
          "accuracy":100.0,
          "is_full_combo":true,
          "push_acc":100.0,
          "push_acc_hint":$pushAccHint
        }
        """.trimIndent(),
    )

    private fun score(songId: String, difficulty: String, score: Int, accuracy: Double) =
        ScoreSnapshotEntry(
            songId = songId,
            songName = songId,
            difficulty = difficulty,
            score = score,
            accuracy = accuracy,
            rks = accuracy,
            isFullCombo = false,
        )
}
