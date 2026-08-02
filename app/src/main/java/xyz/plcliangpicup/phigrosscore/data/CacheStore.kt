package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class CacheStore(private val context: Context, private val json: Json) {
    private val snapshotFile = File(context.filesDir, "cache/b30.json")
    private val songCatalogFile = File(context.filesDir, "cache/song-catalog.json")
    val imageFile = File(context.filesDir, "b30/latest.png")

    suspend fun saveSnapshot(snapshot: B30Snapshot) = withContext(Dispatchers.IO) {
        snapshotFile.parentFile?.mkdirs()
        val temp = File(snapshotFile.parentFile, "${snapshotFile.name}.tmp")
        temp.writeText(json.encodeToString(B30Snapshot.serializer(), snapshot))
        if (!temp.renameTo(snapshotFile)) {
            snapshotFile.writeText(temp.readText())
            temp.delete()
        }
    }

    suspend fun readSnapshot(): B30Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            if (!snapshotFile.exists()) null
            else json.decodeFromString(B30Snapshot.serializer(), snapshotFile.readText())
        }.getOrNull()
    }

    suspend fun saveSongCatalog(catalog: RemoteSongCatalog) = withContext(Dispatchers.IO) {
        songCatalogFile.parentFile?.mkdirs()
        val temp = File(songCatalogFile.parentFile, "${songCatalogFile.name}.tmp")
        temp.writeText(json.encodeToString(RemoteSongCatalog.serializer(), catalog))
        if (!temp.renameTo(songCatalogFile)) {
            songCatalogFile.writeText(temp.readText())
            temp.delete()
        }
    }

    suspend fun readSongCatalog(): RemoteSongCatalog? = withContext(Dispatchers.IO) {
        runCatching {
            if (!songCatalogFile.exists()) null
            else json.decodeFromString(RemoteSongCatalog.serializer(), songCatalogFile.readText())
        }.getOrNull()
    }

    suspend fun saveImage(bytes: ByteArray): File = withContext(Dispatchers.IO) {
        imageFile.parentFile?.mkdirs()
        val temp = File(imageFile.parentFile, "latest.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(imageFile)) {
            imageFile.writeBytes(bytes)
            temp.delete()
        }
        imageFile
    }

    suspend fun deleteImage() = withContext(Dispatchers.IO) {
        val temp = File(imageFile.parentFile, "latest.tmp")
        if (temp.exists() && !temp.delete()) {
            throw IllegalStateException("无法删除旧的 B30 临时图片")
        }
        if (imageFile.exists() && !imageFile.delete()) {
            throw IllegalStateException("无法删除旧的 B30 成绩图")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        snapshotFile.delete()
        songCatalogFile.delete()
        imageFile.delete()
    }
}
