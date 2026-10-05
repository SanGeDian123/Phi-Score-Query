package xyz.plcliangpicup.phigrosscore.ui

import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import xyz.plcliangpicup.phigrosscore.data.PracticePcmClock
import xyz.plcliangpicup.phigrosscore.data.PracticePlaybackClock
import xyz.plcliangpicup.phigrosscore.data.PracticeAutoSoundSchedule
import xyz.plcliangpicup.phigrosscore.data.practicePlayingLeadFrames
import xyz.plcliangpicup.phigrosscore.data.practicePresentedFrame

/** Decode once during visible loading; playback/seek never invoke a decoder. */
internal class PracticeMusicPlayer private constructor(
    private val thread: HandlerThread,
    private val pcm: ByteArray,
    private val sampleRate: Int,
    private val channels: Int,
) {
    private val handler = Handler(thread.looper)
    private val dispatcher = handler.asCoroutineDispatcher("PracticeMusic")
    private lateinit var track: AudioTrack
    private val frameBytes = channels * 2
    private val frameCount = pcm.size / frameBytes
    private val clock = PracticePcmClock(sampleRate, frameCount)
    private val playbackClock = PracticePlaybackClock()
    private val timestamp = AudioTimestamp()
    private var timestampEpochNanos = System.nanoTime()
    private var writeFrame = 0
    private var originFrame = 0
    private var headBase = 0L
    private var queuedFrames = 0L
    private var outputSuspended = false
    private var outputWarm = false
    private var primingHead = 0L
    private var speed = 1f
    private val noiseFilter = PracticeNoiseLowPass(sampleRate, channels)
    // Retain partially written filtered PCM. Advancing the filter twice on an
    // AudioTrack nonblocking retry would corrupt both sound and timing.
    private val noiseOutput = ByteArray(maxOf(frameBytes, sampleRate / 100 * frameBytes))
    private var noiseOutputOffset = 0
    private var noiseOutputBytes = 0
    @Volatile private var noiseBlockedRequested = false
    private var autoSchedule: PracticeAutoSoundSchedule? = null
    @Volatile private var autoSoundOffsetSeconds = 0.0
    private var autoHit: ((Int) -> Unit)? = null
    @Volatile var onPlaybackState: ((Boolean) -> Unit)? = null
    private val silence = ByteArray(sampleRate * frameBytes / 10)
    data class Position(val milliseconds: Int, val uptimeMs: Long, val advancing: Boolean = false)
    @Volatile var position = Position(0, SystemClock.uptimeMillis())
        private set
    @Volatile var isPlaying = false
        private set
    val duration = clock.milliseconds(frameCount)
    @Volatile private var failure: Throwable? = null
    @Volatile private var released = false
    fun readPosition(): Position { failure?.let { throw it }; return position }
    val currentPosition: Int get() = readPosition().milliseconds
    fun setAutoSoundOffset(seconds: Double) { autoSoundOffsetSeconds = seconds }
    fun setNoiseBlocked(blocked: Boolean) {
        if (released || noiseBlockedRequested == blocked) return
        noiseBlockedRequested = blocked
        handler.post {
            if (!released) {
                if (isPlaying) noiseFilter.setBlocked(blocked)
                else resetNoiseOwned()
            }
        }
    }
    private fun resetNoiseOwned() {
        noiseBlockedRequested = false
        noiseFilter.reset()
        noiseOutputOffset = 0
        noiseOutputBytes = 0
    }
    suspend fun configureAutoSound(schedule: PracticeAutoSoundSchedule?, hit: ((Int) -> Unit)?) = withContext(dispatcher) {
        schedule?.seek(position.milliseconds / 1000.0, autoSoundOffsetSeconds)
        autoSchedule = schedule; autoHit = hit
    }
    private fun sample() {
        val head = track.playbackHeadPosition.toLong() and 0xffffffffL
        val frame = clock.consumedFrame(originFrame, head, headBase)
        val now = SystemClock.uptimeMillis()
        val presented = if (isPlaying && track.getTimestamp(timestamp)) practicePresentedFrame(
            timestamp.framePosition, timestamp.nanoTime, System.nanoTime(), timestampEpochNanos,
            headBase, queuedFrames, sampleRate, speed) else null
        val smooth = playbackClock.sample(frame.toDouble() / sampleRate, now, speed, isPlaying && head > headBase)
        val seconds = if (presented != null) (originFrame + presented - headBase) / sampleRate else smooth.seconds
        position = Position((seconds * 1000).toInt().coerceIn(0, duration), now, presented != null || smooth.advancing)
        if (isPlaying && head >= headBase) autoHit?.let {
            autoSchedule?.dispatch(position.milliseconds / 1000.0, autoSoundOffsetSeconds, it)
        }
        // A smoothed fallback head can predict a few ms ahead. Only confirmed
        // consumed/presented frames may stop the track at the end of the song.
        val finished = if (presented != null) originFrame + presented - headBase >= frameCount else frame >= frameCount
        if (isPlaying && finished) {
            resetNoiseOwned()
            isPlaying = false; track.setVolume(0f); onPlaybackState?.invoke(false)
        }
    }
    private fun fill() {
        val music = isPlaying && writeFrame < frameCount
        val head = track.playbackHeadPosition.toLong() and 0xffffffffL
        if (head > primingHead) outputWarm = true
        // Keep a wall-clock reserve at every rate. No real-time time-stretch DSP
        // runs on the device's audio mixer; source PCM is resampled directly.
        val targetLead = if (!outputWarm) track.bufferSizeInFrames.toLong()
            else if (isPlaying) practicePlayingLeadFrames(sampleRate, speed)
            else (sampleRate * speed * .02f).toLong()
        val available = (targetLead - (queuedFrames - head).coerceAtLeast(0)).coerceAtLeast(0).toInt() * frameBytes
        val bytes = minOf(available, if (music) pcm.size - writeFrame * frameBytes else silence.size, silence.size)
        if (bytes <= 0) return
        val written = if (music) {
            if (noiseOutputBytes > 0 || !noiseFilter.isBypassed) {
                if (noiseOutputBytes == 0) {
                    noiseOutputOffset = 0
                    noiseOutputBytes = minOf(bytes, noiseOutput.size)
                    noiseFilter.process(pcm, writeFrame * frameBytes, noiseOutputBytes, noiseOutput)
                }
                val result = track.write(noiseOutput, noiseOutputOffset, minOf(bytes, noiseOutputBytes), AudioTrack.WRITE_NON_BLOCKING)
                if (result > 0) {
                    noiseOutputOffset += result
                    noiseOutputBytes -= result
                }
                result
            } else {
                val result = track.write(pcm, writeFrame * frameBytes, bytes, AudioTrack.WRITE_NON_BLOCKING)
                if (result > 0) noiseFilter.observePcm(pcm, writeFrame * frameBytes, result)
                result
            }
        } else track.write(silence, 0, bytes, AudioTrack.WRITE_NON_BLOCKING)
        check(written >= 0) { "音频输出失败：$written" }
        queuedFrames += written / frameBytes
        if (music) writeFrame += written / frameBytes
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (released || outputSuspended) return
            runCatching { fill(); if (isPlaying) sample() }.onFailure { failure = it; isPlaying = false }
            if (!released && failure == null) handler.postDelayed(this, if (isPlaying) 2 else 8)
        }
    }
    private fun primeSilence(frame: Int) {
        onPlaybackState?.invoke(false)
        handler.removeCallbacks(ticker)
        track.setVolume(0f)
        track.pause(); track.flush()
        resetNoiseOwned()
        isPlaying = false
        outputSuspended = false
        originFrame = frame
        writeFrame = frame
        headBase = track.playbackHeadPosition.toLong() and 0xffffffffL
        queuedFrames = headBase
        primingHead = headBase
        outputWarm = false
        playbackClock.reset()
        timestampEpochNanos = System.nanoTime()
        position = Position(clock.milliseconds(frame), SystemClock.uptimeMillis())
        fill()
        // Keep output warm during preparation/countdown; only zero PCM is sent.
        track.play()
        handler.post(ticker)
    }
    private fun pauseOwned() {
        if (isPlaying) sample()
        primeSilence(clock.frameAt(position.milliseconds.toLong()))
    }
    fun pause() {
        onPlaybackState?.invoke(false)
        if (!released) handler.post {
            if (!released) runCatching { pauseOwned() }.onFailure { failure = it }
        }
    }
    fun suspendOutput() {
        onPlaybackState?.invoke(false)
        if (!released) handler.post {
            if (!released) runCatching {
                if (isPlaying) sample()
                isPlaying = false
                outputSuspended = true
                resetNoiseOwned()
                track.setVolume(0f); track.pause()
                handler.removeCallbacks(ticker)
            }.onFailure { failure = it }
        }
    }
    suspend fun setSpeed(speed: Float, keepPlaying: Boolean, soundOffsetSeconds: Double? = null) = withContext(dispatcher) {
        check(!released)
        require(speed.isFinite() && speed in .5f..2f)
        if (isPlaying) sample()
        // Like Phira's playback_rate path, vary sampling rate (pitch follows
        // tempo) instead of Android's device-dependent pitch-preserving DSP.
        check(track.setPlaybackRate((sampleRate * speed).toInt()) == AudioTrack.SUCCESS) { "设备不支持此播放速率" }
        this@PracticeMusicPlayer.speed = speed
        noiseFilter.setPlaybackSpeed(speed)
        soundOffsetSeconds?.let { autoSoundOffsetSeconds = it }
        playbackClock.reset()
        timestampEpochNanos = System.nanoTime()
        position = position.copy(uptimeMs = SystemClock.uptimeMillis(), advancing = false)
        if (!keepPlaying) pauseOwned()
    }
    suspend fun seekTo(positionMs: Long) = withContext(dispatcher) {
        check(!released)
        primeSilence(clock.frameAt(positionMs))
    }
    suspend fun prepareStart(positionMs: Long) = withContext(dispatcher) {
        check(!released)
        // Already decoded and queued. A paused run can resume its existing buffer.
        if (outputSuspended || kotlin.math.abs(position.milliseconds.toLong() - positionMs) > 2) seekTo(positionMs)
        fill()
        withTimeout(5_000) {
            while (!outputWarm) { delay(8); failure?.let { throw it } }
        }
    }
    suspend fun start() = withContext(dispatcher) {
        check(!released)
        failure?.let { throw it }
        // Already-running silence keeps the hardware output open. The audible
        // timeline begins after those queued silent frames, never before them.
        headBase = queuedFrames
        timestampEpochNanos = System.nanoTime()
        onPlaybackState?.invoke(true)
        isPlaying = true
        position = position.copy(uptimeMs = SystemClock.uptimeMillis(), advancing = false)
        track.setVolume(1f)
        fill()
    }
    fun release() {
        if (released) return
        released = true; isPlaying = false
        onPlaybackState?.invoke(false)
        handler.post {
            handler.removeCallbacksAndMessages(null)
            resetNoiseOwned()
            if (::track.isInitialized) track.release()
            thread.quitSafely()
        }
    }
    companion object {
        suspend fun load(file: File): PracticeMusicPlayer {
            val audio = withContext(Dispatchers.IO) { decode(file) }
            val owner = PracticeMusicPlayer(HandlerThread("PracticeMusic", android.os.Process.THREAD_PRIORITY_AUDIO).apply { start() }, audio.bytes, audio.rate, audio.channels)
            try {
                withContext(owner.dispatcher) {
                    val mask = if (audio.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                    val minimum = AudioTrack.getMinBufferSize(audio.rate, mask, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum > 0) { "设备不支持此音频格式" }
                    owner.track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(audio.rate).setChannelMask(mask).build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .setBufferSizeInBytes(maxOf(minimum * 2, audio.rate * audio.channels * 2 * 3 / 10))
                        .build()
                    check(owner.track.state == AudioTrack.STATE_INITIALIZED) { "无法准备音频输出" }
                    owner.primeSilence(0)
                }
                return owner
            } catch (error: Throwable) { owner.release(); throw error }
        }

        private data class Decoded(val bytes: ByteArray, val rate: Int, val channels: Int)
        private suspend fun decode(file: File): Decoded {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            val temporary = File.createTempFile("practice-decoded-", ".pcm", file.parentFile)
            try {
                extractor.setDataSource(file.absolutePath)
                val audioIndex = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("谱面没有可播放的音频")
                extractor.selectTrack(audioIndex)
                val format = extractor.getTrackFormat(audioIndex)
                var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, encoding)
                val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec = decoder
                decoder.configure(format, null, null, 0); decoder.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var lastProgress = SystemClock.elapsedRealtime()
                FileOutputStream(temporary).use { output ->
                    while (!outputDone) {
                        currentCoroutineContext().ensureActive()
                        check(SystemClock.elapsedRealtime() - lastProgress < 15_000) { "音频解码超时" }
                        if (!inputDone) {
                            val index = decoder.dequeueInputBuffer(10_000)
                            if (index >= 0) {
                                val input = decoder.getInputBuffer(index)!!
                                val size = extractor.readSampleData(input, 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                                lastProgress = SystemClock.elapsedRealtime()
                            }
                        }
                        when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val actual = decoder.outputFormat
                                rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                encoding = if (actual.containsKey(MediaFormat.KEY_PCM_ENCODING)) actual.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                                check(channels in 1..2) { "不支持的音频声道数：$channels" }
                            }
                            else -> if (index >= 0) {
                                val buffer = decoder.getOutputBuffer(index)!!
                                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                    when (encoding) {
                                        AudioFormat.ENCODING_PCM_16BIT -> {
                                            val bytes = ByteArray(info.size); buffer.get(bytes); output.write(bytes)
                                        }
                                        AudioFormat.ENCODING_PCM_FLOAT -> {
                                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                                            val bytes = ByteBuffer.allocate(info.size / 2).order(ByteOrder.LITTLE_ENDIAN)
                                            while (buffer.remaining() >= 4) bytes.putShort((buffer.float.coerceIn(-1f, 1f) * 32767).toInt().toShort())
                                            output.write(bytes.array())
                                        }
                                        else -> error("不支持的音频编码：$encoding")
                                    }
                                    check(temporary.length() <= 128L * 1024 * 1024) { "解码后的音频过大" }
                                }
                                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                decoder.releaseOutputBuffer(index, false)
                                lastProgress = SystemClock.elapsedRealtime()
                            }
                        }
                    }
                }
                check(channels in 1..2 && temporary.length() > 0) { "音频解码结果为空" }
                return Decoded(temporary.readBytes(), rate, channels)
            } finally {
                runCatching { codec?.stop() }; codec?.release(); extractor.release(); temporary.delete()
            }
        }
    }
}
