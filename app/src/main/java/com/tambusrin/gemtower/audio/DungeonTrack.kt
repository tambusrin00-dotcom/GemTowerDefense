package com.tambusrin.gemtower.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 던전 BGM "Gem Crypt" — EDM + 피아노, A 하모닉 마이너, 126 BPM, 32마디(약 61초) 무한 반복.
 *
 * 음원 파일 없이 코드로 합성함 (Android 의존성 없음 → 단위 테스트에서 WAV로 미리 들어 볼 수 있음).
 * 끝과 처음이 자연스럽게 이어지도록 모든 소리의 꼬리(리버브·딜레이 포함)는 곡 처음으로 되감아 더함.
 *
 * 구성 (마디)
 *  0~7   인트로: 패드 + 피아노 아르페지오, 필터가 서서히 열림. 4마디부터 킥·하이햇, 7마디 스네어 빌드업
 *  8~15  드롭 1: 4/4 킥, 클랩, 오프비트 베이스, 사이드체인 패드, 신스 리드 멜로디
 *  16~23 브레이크다운: 피아노 멜로디 독주 + 패드, 22~23마디 라이저·스네어 롤
 *  24~31 드롭 2: 드롭 1 + 피아노가 멜로디를 옥타브로 함께 연주
 */
object DungeonTrack {
    const val BPM = 126.0
    const val BARS = 32
    /** 출력 형식 버전. 소리를 바꾸면 올려서 기기에 캐시된 이전 음원을 버리게 함 */
    const val VERSION = 1

    private const val BEAT = 60.0 / BPM
    private const val BAR = BEAT * 4

    /** 마디별 화음: 베이스 음, 패드 음, 아르페지오 음 (MIDI 번호) */
    private class Chord(val bass: Int, val pad: IntArray, val arp: IntArray)

    private val AM = Chord(45, intArrayOf(57, 60, 64), intArrayOf(69, 72, 76, 81))
    private val F = Chord(41, intArrayOf(53, 57, 60), intArrayOf(65, 69, 72, 77))
    private val DM = Chord(38, intArrayOf(53, 57, 62), intArrayOf(62, 65, 69, 74))
    private val E = Chord(40, intArrayOf(52, 56, 59), intArrayOf(64, 68, 71, 76))
    private val PROGRESSION = arrayOf(AM, F, DM, E)

    /** 8마디 멜로디: (시작 박, 길이 박, MIDI). 박은 8마디 구간 처음 기준 */
    private val MELODY = arrayOf(
        // Am
        doubleArrayOf(0.0, 0.5, 69.0), doubleArrayOf(0.5, 0.5, 72.0), doubleArrayOf(1.0, 1.0, 76.0),
        doubleArrayOf(2.0, 0.5, 74.0), doubleArrayOf(2.5, 0.5, 72.0), doubleArrayOf(3.0, 0.5, 71.0), doubleArrayOf(3.5, 0.5, 72.0),
        // F
        doubleArrayOf(4.0, 1.0, 72.0), doubleArrayOf(5.0, 0.5, 69.0), doubleArrayOf(5.5, 0.5, 72.0),
        doubleArrayOf(6.0, 1.5, 77.0), doubleArrayOf(7.5, 0.5, 76.0),
        // Dm
        doubleArrayOf(8.0, 1.0, 74.0), doubleArrayOf(9.0, 0.5, 77.0), doubleArrayOf(9.5, 0.5, 76.0),
        doubleArrayOf(10.0, 0.5, 74.0), doubleArrayOf(10.5, 0.5, 72.0), doubleArrayOf(11.0, 1.0, 69.0),
        // E
        doubleArrayOf(12.0, 0.5, 71.0), doubleArrayOf(12.5, 0.5, 68.0), doubleArrayOf(13.0, 0.5, 71.0),
        doubleArrayOf(13.5, 0.5, 74.0), doubleArrayOf(14.0, 1.0, 76.0), doubleArrayOf(15.0, 1.0, 80.0),
        // Am (높게)
        doubleArrayOf(16.0, 0.5, 76.0), doubleArrayOf(16.5, 0.5, 81.0), doubleArrayOf(17.0, 1.0, 84.0),
        doubleArrayOf(18.0, 0.5, 83.0), doubleArrayOf(18.5, 0.5, 81.0), doubleArrayOf(19.0, 0.5, 80.0), doubleArrayOf(19.5, 0.5, 81.0),
        // F
        doubleArrayOf(20.0, 1.0, 77.0), doubleArrayOf(21.0, 0.5, 76.0), doubleArrayOf(21.5, 0.5, 77.0),
        doubleArrayOf(22.0, 1.5, 81.0), doubleArrayOf(23.5, 0.5, 79.0),
        // Dm
        doubleArrayOf(24.0, 1.0, 77.0), doubleArrayOf(25.0, 0.5, 76.0), doubleArrayOf(25.5, 0.5, 74.0),
        doubleArrayOf(26.0, 0.5, 77.0), doubleArrayOf(26.5, 0.5, 76.0), doubleArrayOf(27.0, 1.0, 74.0),
        // E → A
        doubleArrayOf(28.0, 1.0, 76.0), doubleArrayOf(29.0, 0.5, 71.0), doubleArrayOf(29.5, 0.5, 74.0),
        doubleArrayOf(30.0, 2.0, 76.0),
    )

    private val ARP_PATTERN = intArrayOf(0, 2, 1, 3, 2, 1, 3, 2)

    private fun hz(midi: Double) = 440.0 * 2.0.pow((midi - 69.0) / 12.0)

    /** 이 샘플레이트의 스테레오 16비트 PCM (L, R 교차). 길이 = 곡 한 바퀴 */
    fun render(sampleRate: Int): ShortArray = Renderer(sampleRate).run()

    private class Renderer(val sr: Int) {
        val n = (BAR * BARS * sr).toInt()
        val dryL = FloatArray(n)
        val dryR = FloatArray(n)
        /** 사이드체인(킥에 맞춰 눌림)이 걸리는 버스 */
        val duckL = FloatArray(n)
        val duckR = FloatArray(n)
        val revSend = FloatArray(n)
        val delSend = FloatArray(n)
        val rnd = Random(7)

        fun idx(t: Double) = Math.floorMod((t * sr).toLong(), n.toLong()).toInt()
        fun bar(b: Int) = b * BAR

        fun add(buf: FloatArray, i: Int, v: Float) {
            buf[if (i >= n) i - n else i] += v
        }

        fun run(): ShortArray {
            for (b in 0 until BARS) {
                val ch = PROGRESSION[b % 4]
                val t0 = bar(b)
                val section = b / 8
                padChord(t0, ch, b)
                // 피아노 아르페지오: 인트로·드롭에서 (브레이크다운은 멜로디만)
                if (section != 2) {
                    val vel = when (section) { 0 -> 0.55 + 0.05 * (b % 8); else -> 0.42 }
                    for (i in 0 until 8) piano(t0 + i * BEAT / 2, BEAT * 0.45, ch.arp[ARP_PATTERN[i]].toDouble(), vel, 0.5)
                } else {
                    // 브레이크다운: 왼손 화음을 길게
                    for (m in ch.pad) piano(t0, BAR * 0.95, m - 12.0, 0.35, 0.6)
                }
                drums(b, t0, section)
                if (section == 1 || section == 3) bassBar(t0, ch)
            }
            // 멜로디
            for (note in MELODY) {
                val beat = note[0]; val len = note[1]; val m = note[2]
                // 드롭 1·2: 신스 리드
                for (start in intArrayOf(8, 24)) lead(bar(start) + beat * BEAT, len * BEAT, m)
                // 브레이크다운: 피아노 독주
                piano(bar(16) + beat * BEAT, len * BEAT * 1.1, m, 0.85, 1.0)
                // 드롭 2: 피아노가 옥타브 아래로 함께
                piano(bar(24) + beat * BEAT, len * BEAT, m - 12.0, 0.6, 0.6)
            }
            riser(bar(22), BAR * 2)
            crash(bar(8)); crash(bar(24)); crash(bar(16), 0.5f)
            return mix()
        }

        // ------------------------------------------------------------ 악기

        /** 배음 합성 피아노. 높은 배음일수록 빨리 사라지고, 약간의 비조화성으로 현 느낌을 냄 */
        fun piano(t: Double, dur: Double, midi: Double, vel: Double, send: Double) {
            val f = hz(midi)
            val partials = if (f > 1200) 4 else 7
            val baseTau = 1.4 * (262.0 / f).pow(0.45)
            val len = min(dur + 0.35, 2.4)
            val count = (len * sr).toInt()
            val start = idx(t)
            val pan = ((midi - 66.0) / 30.0).coerceIn(-0.6, 0.6)
            val gl = (0.5 * (1 - pan)).toFloat()
            val gr = (0.5 * (1 + pan)).toFloat()
            val c = DoubleArray(partials); val s = DoubleArray(partials)
            val cw = DoubleArray(partials); val sw = DoubleArray(partials)
            val amp = DoubleArray(partials); val dec = DoubleArray(partials)
            for (k in 0 until partials) {
                val kk = k + 1.0
                val fk = f * kk * sqrt(1 + 0.0004 * kk * kk)
                if (fk > sr * 0.45) { amp[k] = 0.0; continue }
                val w = 2 * PI * fk / sr
                cw[k] = cos(w); sw[k] = sin(w)
                c[k] = 1.0; s[k] = 0.0
                // 세게 칠수록 높은 배음이 밝아짐
                amp[k] = vel * (1.0 / kk.pow(1.25)) * vel.pow(k * 0.25)
                dec[k] = exp(-1.0 / (sr * baseTau / (1 + 0.7 * k)))
            }
            val relStart = (dur * sr).toInt()
            val relLen = (0.12 * sr).toInt()
            val atk = (0.003 * sr).toInt().coerceAtLeast(1)
            var hammer = 0.0
            for (i in 0 until count) {
                var v = 0.0
                for (k in 0 until partials) {
                    if (amp[k] == 0.0) continue
                    v += s[k] * amp[k]
                    val nc = c[k] * cw[k] - s[k] * sw[k]
                    s[k] = s[k] * cw[k] + c[k] * sw[k]
                    c[k] = nc
                    amp[k] *= dec[k]
                }
                // 해머 소리: 아주 짧은 저역 노이즈
                if (i < sr / 100) {
                    hammer += (rnd.nextDouble() * 2 - 1 - hammer) * 0.3
                    v += hammer * vel * 0.25 * (1 - i / (sr / 100.0))
                }
                var e = if (i < atk) i / atk.toDouble() else 1.0
                if (i > relStart) e *= max(0.0, 1.0 - (i - relStart) / relLen.toDouble())
                if (e == 0.0 && i > relStart) break
                val out = (v * e * 0.32).toFloat()
                val j = start + i
                add(dryL, j, out * gl); add(dryR, j, out * gr)
                add(revSend, j, out * send.toFloat() * 0.5f)
            }
        }

        /** 디튠된 톱니파 패드 (사이드체인 버스) */
        fun padChord(t: Double, ch: Chord, b: Int) {
            val start = idx(t)
            val count = (BAR * 1.15 * sr).toInt()
            val hold = (BAR * sr).toInt()
            // 인트로 앞 4마디는 필터가 닫혔다가 열림, 브레이크다운은 어둡게
            val cutoff0 = when {
                b < 4 -> 250.0 + 300.0 * b
                b < 8 -> 1300.0
                b in 16..23 -> 900.0
                else -> 1800.0
            }
            val cutoff1 = if (b < 4) 250.0 + 300.0 * (b + 1) else cutoff0
            val level = if (b / 8 == 2) 0.10 else 0.08
            for ((vi, m) in ch.pad.withIndex()) {
                for (det in doubleArrayOf(-0.09, 0.09)) {
                    val f = hz(m + det)
                    val dt = f / sr
                    var ph = rnd.nextDouble()
                    val svf = Svf()
                    val panL = if (det < 0) 0.75f else 0.25f
                    for (i in 0 until count) {
                        if (i % 32 == 0) svf.set(cutoff0 + (cutoff1 - cutoff0) * min(1.0, i / hold.toDouble()), 0.8, sr)
                        val saw = 2 * ph - 1 - polyBlep(ph, dt)
                        ph += dt; if (ph >= 1) ph -= 1
                        val lp = svf.lp(saw)
                        val atk = min(1.0, i / (0.25 * sr))
                        val rel = if (i > hold) max(0.0, 1 - (i - hold) / (0.15 * BAR * sr)) else 1.0
                        val out = (lp * atk * rel * level).toFloat()
                        val j = start + i
                        add(duckL, j, out * panL); add(duckR, j, out * (1 - panL))
                        if (vi == 0) add(revSend, j, out * 0.6f)
                    }
                }
            }
        }

        /** 오프비트 베이스: 톱니 + 사인 서브 */
        fun bassBar(t: Double, ch: Chord) {
            val f = hz(ch.bass.toDouble())
            for (i in 0 until 4) {
                val st = idx(t + i * BEAT + BEAT / 2)
                val count = (BEAT * 0.48 * sr).toInt()
                val svf = Svf()
                var ph = 0.0
                var subPh = 0.0
                val dt = f / sr
                for (k in 0 until count) {
                    if (k % 32 == 0) svf.set(250.0 + 1100.0 * exp(-k / (0.05 * sr)), 1.6, sr)
                    val saw = 2 * ph - 1 - polyBlep(ph, dt)
                    ph += dt; if (ph >= 1) ph -= 1
                    val sub = sin(2 * PI * subPh); subPh += dt / 2; if (subPh >= 1) subPh -= 1
                    val env = min(1.0, k / (0.004 * sr)) * min(1.0, (count - k) / (0.02 * sr))
                    val out = ((svf.lp(saw) * 0.5 + sub * 0.6) * env * 0.32).toFloat()
                    add(duckL, st + k, out); add(duckR, st + k, out)
                }
            }
        }

        /** 신스 리드: 디튠 톱니 2개, 플럭 필터 엔벨로프, 핑퐁 딜레이 */
        fun lead(t: Double, dur: Double, midi: Double) {
            val st = idx(t)
            val count = ((dur + 0.08) * sr).toInt()
            val hold = (dur * sr).toInt()
            val svf = Svf()
            val f = hz(midi)
            val dts = doubleArrayOf(f * 0.996 / sr, f * 1.004 / sr, f * 2.0 / sr)
            val phs = DoubleArray(3) { rnd.nextDouble() }
            for (k in 0 until count) {
                if (k % 32 == 0) svf.set(900.0 + 3200.0 * exp(-k / (0.12 * sr)), 1.2, sr)
                var v = 0.0
                for (o in 0 until 3) {
                    val saw = 2 * phs[o] - 1 - polyBlep(phs[o], dts[o])
                    phs[o] += dts[o]; if (phs[o] >= 1) phs[o] -= 1
                    v += saw * (if (o == 2) 0.25 else 0.5)
                }
                val atk = min(1.0, k / (0.005 * sr))
                val rel = if (k > hold) max(0.0, 1 - (k - hold) / (0.08 * sr)) else 1.0
                val env = atk * rel * (0.65 + 0.35 * exp(-k / (0.15 * sr)))
                val out = (svf.lp(v) * env * 0.11).toFloat()
                add(duckL, st + k, out * 0.55f); add(duckR, st + k, out * 0.45f)
                add(delSend, st + k, out * 0.7f)
                add(revSend, st + k, out * 0.3f)
            }
        }

        fun drums(b: Int, t0: Double, section: Int) {
            val full = section == 1 || section == 3
            val introBeat = section == 0 && b >= 4
            val breakBeat = section == 2 && b >= 20
            for (beat in 0 until 4) {
                val bt = t0 + beat * BEAT
                if (full || introBeat) kick(bt, if (introBeat) 0.75f else 1f)
                if (full && (beat == 1 || beat == 3)) clap(bt)
                if (full || introBeat || breakBeat) hat(bt + BEAT / 2, open = true, level = if (full) 0.9f else 0.6f)
                if (full) for (s in intArrayOf(1, 3)) hat(bt + s * BEAT / 4, open = false, level = 0.5f)
            }
            // 빌드업: 7마디(인트로 끝) 8분 스네어, 22~23마디 16분으로 빨라지는 스네어 롤
            if (b == 7) for (i in 0 until 8) snare(t0 + i * BEAT / 2, 0.35f + 0.08f * i)
            if (b == 22) for (i in 0 until 8) snare(t0 + i * BEAT / 2, 0.3f + 0.03f * i)
            if (b == 23) for (i in 0 until 16) snare(t0 + i * BEAT / 4, 0.5f + 0.035f * i)
        }

        fun kick(t: Double, level: Float) {
            val st = idx(t)
            val count = (0.38 * sr).toInt()
            var ph = 0.0
            for (k in 0 until count) {
                val time = k / sr.toDouble()
                val f = 46 + 120 * exp(-time * 28)
                ph += f / sr
                val v = sin(2 * PI * ph) * exp(-time * 6.5) + (if (k < sr / 500) 0.4 else 0.0)
                val out = (tanh(v * 1.4) * 0.55 * level).toFloat()
                add(dryL, st + k, out); add(dryR, st + k, out)
            }
        }

        fun noiseHit(t: Double, len: Double, decay: Double, fc: Double, q: Double, hp: Boolean, level: Float, rev: Float, panL: Float = 0.5f) {
            val st = idx(t)
            val count = (len * sr).toInt()
            val svf = Svf().apply { set(fc, q, sr) }
            for (k in 0 until count) {
                val x = rnd.nextDouble() * 2 - 1
                val y = if (hp) svf.hp(x) else svf.bp(x)
                val out = (y * exp(-k / (decay * sr)) * level).toFloat()
                add(dryL, st + k, out * panL * 2); add(dryR, st + k, out * (1 - panL) * 2)
                if (rev > 0) add(revSend, st + k, out * rev)
            }
        }

        fun clap(t: Double) {
            for (i in 0 until 3) noiseHit(t + i * 0.009, 0.02, 0.006, 1300.0, 1.2, false, 0.35f, 0f)
            noiseHit(t + 0.027, 0.25, 0.07, 1200.0, 1.0, false, 0.38f, 0.5f)
        }

        fun hat(t: Double, open: Boolean, level: Float) =
            noiseHit(t, if (open) 0.25 else 0.05, if (open) 0.06 else 0.012, 8000.0, 0.7, true, 0.12f * level, 0.1f, if (open) 0.42f else 0.58f)

        fun snare(t: Double, level: Float) {
            noiseHit(t, 0.15, 0.045, 2500.0, 0.8, false, 0.3f * level, 0.3f)
            val st = idx(t)
            for (k in 0 until (0.08 * sr).toInt()) {
                val out = (sin(2 * PI * 190 * k / sr) * exp(-k / (0.025 * sr)) * 0.18 * level).toFloat()
                add(dryL, st + k, out); add(dryR, st + k, out)
            }
        }

        fun crash(t: Double, level: Float = 1f) =
            noiseHit(t, 2.0, 0.55, 5500.0, 0.6, true, 0.16f * level, 0.4f)

        /** 노이즈 라이저: 대역 필터가 위로 쓸려 올라가며 커짐 */
        fun riser(t: Double, len: Double) {
            val st = idx(t)
            val count = (len * sr).toInt()
            val svf = Svf()
            for (k in 0 until count) {
                val p = k / count.toDouble()
                if (k % 32 == 0) svf.set(300.0 * 20.0.pow(p), 2.0, sr)
                val out = (svf.bp(rnd.nextDouble() * 2 - 1) * p * p * 0.35).toFloat()
                add(dryL, st + k, out * (0.5f + 0.4f * sin(p * 20).toFloat())); add(dryR, st + k, out * (0.5f - 0.4f * sin(p * 20).toFloat()))
                add(revSend, st + k, out * 0.5f)
            }
        }

        // ------------------------------------------------------------ 믹스

        /** 킥에 맞춘 사이드체인 게인 (드롭과 인트로 후반에만) */
        fun duckGain(i: Int): Float {
            val t = i / sr.toDouble()
            val b = (t / BAR).toInt()
            val section = b / 8
            val depth = when {
                section == 1 || section == 3 -> 0.75
                section == 0 && b >= 4 -> 0.45
                else -> 0.0
            }
            if (depth == 0.0) return 1f
            val p = (t % BEAT) / BEAT
            val x = min(1.0, p / 0.5)
            return (1 - depth * (1 - x * x * (3 - 2 * x))).toFloat()
        }

        fun mix(): ShortArray {
            val (revL, revR) = reverb(revSend)
            val (delL, delR) = pingPong(delSend, BEAT * 0.75, 0.38f)
            val out = ShortArray(n * 2)
            var peak = 1e-9f
            val mixL = FloatArray(n)
            val mixR = FloatArray(n)
            for (i in 0 until n) {
                val g = duckGain(i)
                mixL[i] = dryL[i] + duckL[i] * g + revL[i] * 0.9f + delL[i] * g
                mixR[i] = dryR[i] + duckR[i] * g + revR[i] * 0.9f + delR[i] * g
                peak = max(peak, max(abs(mixL[i]), abs(mixR[i])))
            }
            // 약간 눌러서(소프트 클립) 최고점을 -1.5 dBFS로
            val pre = 1.25f / peak
            val norm = (0.84 / tanh(1.25)).toFloat()
            for (i in 0 until n) {
                out[2 * i] = (tanh((mixL[i] * pre).toDouble()) * norm * 32767).toInt().coerceIn(-32767, 32767).toShort()
                out[2 * i + 1] = (tanh((mixR[i] * pre).toDouble()) * norm * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
            return out
        }

        /** 간단한 Schroeder 리버브. 반복 곡이라 두 바퀴 돌려서 꼬리가 처음으로 이어지게 함 */
        fun reverb(input: FloatArray): Pair<FloatArray, FloatArray> {
            val scale = sr / 44100.0
            fun chan(spread: Int): FloatArray {
                val combs = intArrayOf(1116, 1188, 1277, 1356).map { FloatArray(((it + spread) * scale).toInt()) }
                val combIdx = IntArray(4)
                val combLp = FloatArray(4)
                val aps = intArrayOf(556, 441).map { FloatArray(((it + spread) * scale).toInt()) }
                val apIdx = IntArray(2)
                val outBuf = FloatArray(n)
                for (pass in 0 until 2) for (i in 0 until n) {
                    val x = input[i] * 0.12f
                    var y = 0f
                    for (c in 0 until 4) {
                        val buf = combs[c]
                        val o = buf[combIdx[c]]
                        combLp[c] = o * 0.6f + combLp[c] * 0.4f
                        buf[combIdx[c]] = x + combLp[c] * 0.84f
                        combIdx[c] = (combIdx[c] + 1) % buf.size
                        y += o
                    }
                    for (a in 0 until 2) {
                        val buf = aps[a]
                        val o = buf[apIdx[a]]
                        buf[apIdx[a]] = y + o * 0.5f
                        y = o - y * 0.5f
                        apIdx[a] = (apIdx[a] + 1) % buf.size
                    }
                    if (pass == 1) outBuf[i] = y
                }
                return outBuf
            }
            return chan(0) to chan(23)
        }

        fun pingPong(input: FloatArray, delaySec: Double, fb: Float): Pair<FloatArray, FloatArray> {
            val d = (delaySec * sr).toInt()
            val bl = FloatArray(d); val br = FloatArray(d)
            val ol = FloatArray(n); val or = FloatArray(n)
            var p = 0
            for (pass in 0 until 2) for (i in 0 until n) {
                val l = bl[p]; val r = br[p]
                bl[p] = input[i] + r * fb
                br[p] = l
                p = (p + 1) % d
                if (pass == 1) { ol[i] = l * 0.6f; or[i] = r * 0.6f }
            }
            return ol to or
        }
    }

    /** 톱니파 앨리어싱을 줄이는 PolyBLEP 보정 */
    private fun polyBlep(ph: Double, dt: Double): Double = when {
        ph < dt -> { val x = ph / dt; x + x - x * x - 1 }
        ph > 1 - dt -> { val x = (ph - 1) / dt; x * x + x + x + 1 }
        else -> 0.0
    }

    /** TPT 상태 변수 필터 (LP/BP/HP) */
    private class Svf {
        private var g = 0.0; private var k = 1.0; private var a1 = 0.0; private var a2 = 0.0; private var a3 = 0.0
        private var ic1 = 0.0; private var ic2 = 0.0
        private var lp = 0.0; private var bp = 0.0; private var hp = 0.0

        fun set(fc: Double, q: Double, sr: Int) {
            g = tan(PI * min(fc, sr * 0.45) / sr)
            k = 1 / q
            a1 = 1 / (1 + g * (g + k)); a2 = g * a1; a3 = g * a2
        }

        private fun tick(x: Double) {
            val v3 = x - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2 * v1 - ic1; ic2 = 2 * v2 - ic2
            lp = v2; bp = v1; hp = x - k * v1 - v2
        }

        fun lp(x: Double): Double { tick(x); return lp }
        fun bp(x: Double): Double { tick(x); return bp }
        fun hp(x: Double): Double { tick(x); return hp }
    }
}
