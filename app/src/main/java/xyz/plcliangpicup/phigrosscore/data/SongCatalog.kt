package xyz.plcliangpicup.phigrosscore.data

import android.content.Context

class SongCatalog(context: Context) {
    private data class CatalogSnapshot(
        val songs: Map<String, SongInfo>,
        val version: String?,
    )

    private val details = context.assets.open("song_details.csv")
        .bufferedReader(Charsets.UTF_8)
        .useLines { lines ->
            lines.drop(1).mapNotNull(::parseSongDetailCsvLine).associateBy(SongDetail::id)
        }

    private val bundledSongs: Map<String, SongInfo> = context.assets.open("song_info.csv")
        .bufferedReader(Charsets.UTF_8)
        .useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                parseSongInfoCsvLine(line)?.let { song ->
                    val detail = details[song.id]
                    song.copy(
                        chapter = detail?.chapter.orEmpty(),
                        charts = listOf("EZ", "HD", "IN", "AT").mapNotNull { difficulty ->
                            val chartConstant = song.chartConstants[difficulty]
                            val charter = detail?.charters?.get(difficulty).orEmpty()
                            val noteCount = detail?.noteCounts?.get(difficulty)
                            if (chartConstant == null && charter.isBlank() && noteCount == null) null
                            else SongChartInfo(
                                difficulty = difficulty,
                                chartConstant = chartConstant,
                                noteCount = noteCount,
                                charter = charter,
                            )
                        },
                    )
                }
            }.associateBy { it.id }
        }

    @Volatile
    private var snapshot = CatalogSnapshot(bundledSongs, null)

    @Volatile
    private var hiddenSongIds: Set<String> = emptySet()

    val version: String?
        get() = snapshot.version

    operator fun get(id: String): SongInfo? = if (isVisible(id)) snapshot.songs[id] else null

    fun setHiddenSongIds(songIds: Collection<String>) {
        hiddenSongIds = songIds.asSequence()
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .toSet()
    }

    fun isVisible(songId: String): Boolean = songId.lowercase() !in hiddenSongIds

    fun constantTableEntries(): List<ConstantTableEntry> =
        buildConstantTableEntries(snapshot.songs.values.filter { isVisible(it.id) })

    fun applyRemoteCatalog(catalog: RemoteSongCatalog) {
        if (catalog.version.isBlank() || catalog.items.isEmpty()) return
        val songs = catalog.items.associate { remote ->
            remote.id to resolveRemote(remote)
        }.filterKeys(::isVisible)
        snapshot = CatalogSnapshot(songs, catalog.version)
    }

    fun resetToBundled() {
        snapshot = CatalogSnapshot(bundledSongs, null)
    }

    fun resolveRemote(remote: RemoteSongInfo): SongInfo {
        val bundled = bundledSongs[remote.id]
        return mergeRemoteSong(remote, bundled)
    }

    fun search(query: String, limit: Int = 30): List<SongInfo> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return snapshot.songs.values.asSequence()
            .filter { isVisible(it.id) }
            .mapNotNull { song ->
                val id = song.id.lowercase()
                val name = song.name.lowercase()
                val composer = song.composer.lowercase()
                val rank = when {
                    id == needle -> 0
                    name == needle -> 1
                    name.startsWith(needle) -> 2
                    id.startsWith(needle) -> 3
                    name.contains(needle) -> 4
                    composer.contains(needle) -> 5
                    id.contains(needle) -> 6
                    else -> return@mapNotNull null
                }
                rank to song
            }
            .sortedWith(compareBy<Pair<Int, SongInfo>> { it.first }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
            .toList()
    }

}

internal fun RemoteChartConstants.toDifficultyMap(): Map<String, Double> = buildMap {
    ez?.let { put("EZ", it) }
    hd?.let { put("HD", it) }
    inLevel?.let { put("IN", it) }
    at?.let { put("AT", it) }
}

internal fun mergeRemoteSong(remote: RemoteSongInfo, bundled: SongInfo?): SongInfo {
    val constants = buildMap {
        putAll(remote.chartConstants.toDifficultyMap())
        bundled?.chartConstants?.forEach { (difficulty, constant) ->
            if (difficulty !in this) put(difficulty, constant)
        }
    }
    return SongInfo(
        id = remote.id,
        name = remote.name.ifBlank { bundled?.name.orEmpty() },
        composer = remote.composer.ifBlank { bundled?.composer.orEmpty() },
        illustrator = remote.illustrator.ifBlank { bundled?.illustrator.orEmpty() },
        chartConstants = constants,
        chapter = bundled?.chapter.orEmpty(),
        charts = listOf("EZ", "HD", "IN", "AT").mapNotNull { difficulty ->
            val bundledChart = bundled?.charts?.firstOrNull {
                it.difficulty.equals(difficulty, ignoreCase = true)
            }
            val chartConstant = constants[difficulty]
            if (chartConstant == null && bundledChart == null) null
            else SongChartInfo(
                difficulty = difficulty,
                chartConstant = chartConstant ?: bundledChart?.chartConstant,
                noteCount = bundledChart?.noteCount,
                charter = bundledChart?.charter.orEmpty(),
            )
        },
    )
}

internal fun buildConstantTableEntries(songs: Collection<SongInfo>): List<ConstantTableEntry> =
    songs.asSequence()
        .flatMap { song ->
            song.charts.asSequence().mapNotNull { chart ->
                val constant = chart.chartConstant ?: return@mapNotNull null
                if (!constant.isFinite() || constant <= 0.0) return@mapNotNull null
                ConstantTableEntry(song = song, chart = chart)
            }
        }
        .sortedWith(
            compareByDescending<ConstantTableEntry> { it.chart.chartConstant }
                .thenBy { it.song.name.lowercase() }
                .thenBy { difficultyOrderForCatalog(it.chart.difficulty) },
        )
        .toList()

internal fun constantTableLevels(entries: List<ConstantTableEntry>): List<Int> =
    ((18 downTo 1).toList() + entries.mapNotNull { entry ->
        entry.chart.chartConstant?.takeIf { it.isFinite() && it > 0.0 }?.toInt()
    }).distinct().sortedDescending()

private fun difficultyOrderForCatalog(difficulty: String): Int = when (difficulty.uppercase()) {
    "AT" -> 0
    "IN" -> 1
    "HD" -> 2
    "EZ" -> 3
    else -> 4
}

private data class SongDetail(
    val id: String,
    val chapter: String,
    val charters: Map<String, String>,
    val noteCounts: Map<String, Int>,
)

private fun parseSongDetailCsvLine(line: String): SongDetail? {
    val columns = parseCsvLine(line)
    if (columns.size < 10) return null
    val difficulties = listOf("EZ", "HD", "IN", "AT")
    return SongDetail(
        id = columns[0],
        chapter = columns[1],
        charters = difficulties.mapIndexedNotNull { index, difficulty ->
            columns.getOrNull(index + 2)?.takeIf(String::isNotBlank)?.let { difficulty to it }
        }.toMap(),
        noteCounts = difficulties.mapIndexedNotNull { index, difficulty ->
            columns.getOrNull(index + 6)?.toIntOrNull()?.let { difficulty to it }
        }.toMap(),
    )
}

internal fun parseSongInfoCsvLine(line: String): SongInfo? {
    val columns = parseCsvLine(line)
    if (columns.size < 4) return null
    val difficulties = listOf("EZ", "HD", "IN", "AT")
    val constants = difficulties.mapIndexedNotNull { index, difficulty ->
        columns.getOrNull(index + 4)?.trim()?.toDoubleOrNull()?.let { difficulty to it }
    }.toMap()
    return SongInfo(
        id = columns[0],
        name = columns[1],
        composer = columns[2],
        illustrator = columns[3],
        chartConstants = constants,
    )
}

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                result += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    result += current.toString()
    return result
}
