package xyz.plcliangpicup.phigrosscore.ui

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.SystemClock
import xyz.plcliangpicup.phigrosscore.data.PracticeHitGate
import xyz.plcliangpicup.phigrosscore.data.PracticeHitVoices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import xyz.plcliangpicup.phigrosscore.data.PracticeNoteType
import java.io.File

/** Owns decoded samples, bounded rejection recovery and playback-scoped requests. */
internal class PracticeHitAudio private constructor(
    private val pool: SoundPool,
    private val samples: Map<PracticeNoteType, Int>,
) {
    private val thread = android.os.HandlerThread("PracticeHits", android.os.Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val handler = Handler(thread.looper)
    private val gate = PracticeHitGate()
    private val voices = PracticeHitVoices(64, { pool.play(it, .8f, .8f, 1, 0, 1f) }, { pool.stop(it) })
    @Volatile private var released = false
    @Volatile var scheduledAutoPlay = false
        set(value) { field = value; gate.setAutomatic(value) }

    fun setEnabled(value: Boolean) { gate.setRunning(value) }
    fun playScheduled(type: PracticeNoteType) = enqueue(type, true)
    fun play(type: PracticeNoteType) = enqueue(type, false)

    private fun enqueue(type: PracticeNoteType, automatic: Boolean) {
        val ticket = gate.request(automatic, SystemClock.uptimeMillis()) ?: return
        val sample = samples[type] ?: return
        // SoundPool.play is a short native enqueue. Submit the first attempt on
        // the input/audio caller; only rejection recovery needs another queue.
        attempt(sample, type, ticket, 0)
    }

    @Synchronized private fun attempt(sample: Int, type: PracticeNoteType,
        ticket: PracticeHitGate.Ticket, retries: Int) {
        if (released || !gate.valid(ticket) || (retries > 0 && !gate.canRetry(ticket, SystemClock.uptimeMillis()))) return
        // Equal priority allows SoundPool to replace the oldest voice in dense
        // passages. Old AUTOPLAY voices must not starve new manual hits.
        if (voices.play(sample)) return
        if (retries < 2 && gate.canRetry(ticket, SystemClock.uptimeMillis())) {
            handler.postDelayed({ attempt(sample, type, ticket, retries + 1) }, 2L)
        } else {
            android.util.Log.w("PracticePlayer", "Hit audio stream rejected: $type")
        }
    }

    @Synchronized fun release() {
        if (released) return
        released = true
        gate.release()
        handler.removeCallbacksAndMessages(null)
        pool.release()
        thread.quitSafely()
    }

    companion object {
        suspend fun load(click: File, drag: File, flick: File): PracticeHitAudio = withContext(Dispatchers.Main.immediate) {
            val pool = SoundPool.Builder().setMaxStreams(64).setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            ).build()
            try {
                val ready = CompletableDeferred<Unit>()
                var remaining = 3
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (status != 0) ready.completeExceptionally(IllegalStateException("打击音效加载失败：$status"))
                    else if (--remaining == 0) ready.complete(Unit)
                }
                val tapId = pool.load(click.absolutePath, 1)
                val dragId = pool.load(drag.absolutePath, 1)
                val flickId = pool.load(flick.absolutePath, 1)
                check(tapId != 0 && dragId != 0 && flickId != 0) { "无法加载打击音效" }
                withTimeout(10_000) { ready.await() }
                pool.setOnLoadCompleteListener(null)
                // Create the mixer streams while the loading screen is visible.
                // The first audible note should not pay SoundPool's stream startup cost.
                for (sample in listOf(tapId, dragId, flickId)) {
                    pool.play(sample, 0f, 0f, 0, 0, 1f)
                }
                PracticeHitAudio(pool, mapOf(PracticeNoteType.TAP to tapId, PracticeNoteType.HOLD to tapId,
                    PracticeNoteType.DRAG to dragId, PracticeNoteType.FLICK to flickId))
            } catch (failure: Throwable) {
                pool.release()
                throw failure
            }
        }
    }
}
