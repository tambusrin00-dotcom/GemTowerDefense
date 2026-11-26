package com.tambusrin.gemtower.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class DungeonTrackTest {
    @Test
    fun rendersSeamlessLoopAndWritesPreview() {
        val sr = 32000
        val t0 = System.nanoTime()
        val pcm = DungeonTrack.render(sr)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("BGM 합성 ${ms}ms, ${pcm.size / 2 / sr.toDouble()}초")

        val seconds = 60.0 / DungeonTrack.BPM * 4 * DungeonTrack.BARS
        assertEquals((seconds * sr).toInt() * 2, pcm.size)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak=$peak", peak in 20000..32767)

        // 반복 지점: 끝과 처음 사이에 큰 튐(클릭)이 없어야 함
        val jumpL = abs(pcm[0] - pcm[pcm.size - 2])
        val jumpR = abs(pcm[1] - pcm[pcm.size - 1])
        assertTrue("loop jump $jumpL/$jumpR", jumpL < 4000 && jumpR < 4000)

        // 미리 듣기용 WAV: app/build/bgm-preview.wav
        val out = File("build/bgm-preview.wav")
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) data.putShort(s)
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(sr).putInt(sr * 4).putShort(4).putShort(16)
        h.put("data".toByteArray()).putInt(pcm.size * 2)
        out.outputStream().use { it.write(h.array()); it.write(data.array()) }
    }
}
