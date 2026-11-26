package com.tambusrin.gemtower

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tambusrin.gemtower.audio.DungeonTrack
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 배경 음악. 곡은 코드로 합성하고(처음 한 번, 백그라운드), 결과를 백업 제외 내부 저장소에 캐시해 다음 실행부터 바로 재생.
 * 켜기/끄기 설정은 기기에 저장됨.
 */
class BgmPlayer(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cacheFile = File(context.noBackupFilesDir, "bgm_v${DungeonTrack.VERSION}_$SAMPLE_RATE.pcm")
    private val lock = Object()

    var enabled = prefs.getBoolean(KEY_ENABLED, true)
        set(v) {
            field = v
            prefs.edit().putBoolean(KEY_ENABLED, v).apply()
            update()
        }

    @Volatile private var pcm: ShortArray? = null
    @Volatile private var resumed = false
    private var thread: Thread? = null
    /** 재생 스레드 세대. 끄면 올라가서 이전 스레드가 스스로 끝남 */
    @Volatile private var generation = 0

    fun onResume() {
        resumed = true
        update()
    }

    fun onPause() {
        resumed = false
        update()
    }

    private fun update() {
        if (resumed && enabled) start() else stop()
    }

    private fun start() {
        synchronized(lock) {
            if (thread != null) return
            val gen = ++generation
            thread = Thread({ playLoop(gen) }, "bgm").apply { start() }
        }
    }

    private fun stop() {
        val t: Thread
        synchronized(lock) {
            t = thread ?: return
            generation++
            thread = null
        }
        t.interrupt()
    }

    @Synchronized
    private fun loadPcm(): ShortArray {
        pcm?.let { return it }
        val cached = runCatching {
            if (cacheFile.length() != EXPECTED_SHORTS * 2L) return@runCatching null
            val bytes = cacheFile.readBytes()
            ShortArray(bytes.size / 2).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
        }.getOrNull()
        val data = cached ?: DungeonTrack.render(SAMPLE_RATE).also { saveCache(it) }
        pcm = data
        return data
    }

    private fun saveCache(data: ShortArray) {
        runCatching {
            // 이전 버전 캐시는 지움
            cacheFile.parentFile?.listFiles { f -> f.name.startsWith("bgm_") && f.name != cacheFile.name }?.forEach { it.delete() }
            val buf = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            buf.asShortBuffer().put(data)
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeBytes(buf.array())
            tmp.renameTo(cacheFile)
        }
    }

    private fun playLoop(gen: Int) {
        val data = loadPcm()
        if (gen != generation) return
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_RATE)) // 약 0.25초
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return
        }
        try {
            track.setVolume(VOLUME)
            track.play()
            val chunk = 4096
            while (gen == generation) {
                val pos = position
                val len = minOf(chunk, data.size - pos)
                val written = track.write(data, pos, len)
                if (written < 0) break
                position = (pos + written) % data.size
            }
            track.pause()
            track.flush()
        } finally {
            track.release()
        }
    }

    /** 다시 켤 때 이어서 재생하도록 위치를 기억 (짝수 = L/R 경계 유지) */
    @Volatile private var position = 0

    private companion object {
        const val SAMPLE_RATE = 32000
        const val KEY_ENABLED = "bgm"
        const val VOLUME = 0.55f
        val EXPECTED_SHORTS = (60.0 / DungeonTrack.BPM * 4 * DungeonTrack.BARS * SAMPLE_RATE).toInt() * 2
    }
}
