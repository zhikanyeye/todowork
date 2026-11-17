package com.yangshizi.todo.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

/**
 * 合成背景音生成器
 *
 * 使用 AudioTrack 实时合成 3 种无法用真实录音替代的背景音：
 * - 白噪音（全频段均匀遮蔽）
 * - 棕噪音（低频厚重遮蔽）
 * - 原创和弦铺底（可听的旋律性专注氛围）
 *
 * 其余自然音效（雨声、海浪、溪流、篝火等）已使用 res/raw 中的
 * 高保真 MP3 录音，通过 [RealAudioPlayer] 播放，不再在此合成。
 */
object AmbientAudioSynth {
    private const val TAG = "AmbientAudioSynth"
    private const val SAMPLE_RATE = 44100
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    enum class SoundType(val displayName: String) {
        NONE("无背景音"),
        WHITE_NOISE("深度全神 (白噪音)"),
        BROWN_NOISE("低频安定 (棕噪音)"),
        FOCUS_PAD("原创和弦铺底 (可听纯音)"),
        CUSTOM("自定义音乐 (导入外部音频)")
    }

    private var currentType = SoundType.NONE

    fun start(type: SoundType) {
        if (currentType == type && audioTrack != null && synthJob?.isActive == true) return
        stop()
        if (type == SoundType.NONE || type == SoundType.CUSTOM) return
        currentType = type

        synthJob = scope.launch {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val random = Random()
                val buffer = ShortArray(1024)

                var lastOut = 0f
                var brownOut = 0f
                var phaseTheta = 0.0
                var padPhase1 = 0.0
                var padPhase2 = 0.0
                var padPhase3 = 0.0
                var padPhase4 = 0.0
                var padSampleCursor = 0L

                track.setVolume(0.88f)
                while (isActive) {
                    for (i in buffer.indices) {
                        val sample: Float = when (type) {
                            SoundType.WHITE_NOISE -> {
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.35f * (noise - lastOut)
                                lastOut = filtered
                                filtered * 0.12f
                            }
                            SoundType.BROWN_NOISE -> {
                                val white = random.nextFloat() * 2f - 1f
                                brownOut = (brownOut + white * 0.018f).coerceIn(-1f, 1f)
                                brownOut * 0.32f
                            }
                            SoundType.FOCUS_PAD -> {
                                phaseTheta += (2.0 * Math.PI * 0.035) / SAMPLE_RATE
                                val breath = (0.62f + 0.38f * ((Math.sin(phaseTheta) + 1.0) / 2.0).toFloat())
                                val seconds = padSampleCursor.toDouble() / SAMPLE_RATE
                                val chordStep = ((seconds / 8.0).toInt() % 4)
                                val root = doubleArrayOf(130.81, 98.00, 110.00, 87.31)[chordStep]
                                val third = doubleArrayOf(164.81, 123.47, 130.81, 110.00)[chordStep]
                                val fifth = doubleArrayOf(196.00, 146.83, 164.81, 130.81)[chordStep]
                                val high = doubleArrayOf(261.63, 196.00, 220.00, 174.61)[chordStep]

                                padPhase1 += (2.0 * Math.PI * root) / SAMPLE_RATE
                                padPhase2 += (2.0 * Math.PI * third) / SAMPLE_RATE
                                padPhase3 += (2.0 * Math.PI * fifth) / SAMPLE_RATE
                                padPhase4 += (2.0 * Math.PI * high) / SAMPLE_RATE

                                val padChord = (
                                    Math.sin(padPhase1).toFloat() * 0.34f +
                                        Math.sin(padPhase2).toFloat() * 0.24f +
                                        Math.sin(padPhase3).toFloat() * 0.22f +
                                        Math.sin(padPhase4).toFloat() * 0.12f
                                    ) * breath

                                val arpeggio = doubleArrayOf(root, fifth, high, fifth, third, fifth, high, fifth)
                                val noteIndex = ((seconds * 2.0).toInt() % arpeggio.size).coerceIn(0, arpeggio.lastIndex)
                                val notePhase = (seconds * 2.0) % 1.0
                                val noteEnvelope = Math.exp(-notePhase * 4.2).toFloat()
                                val melody = Math.sin(2.0 * Math.PI * arpeggio[noteIndex] * seconds).toFloat() * noteEnvelope

                                val air = random.nextFloat() * 2f - 1f
                                val filteredAir = lastOut + 0.04f * (air - lastOut)
                                lastOut = filteredAir
                                padSampleCursor++
                                (padChord * 0.22f + melody * 0.12f + filteredAir * 0.018f).coerceIn(-0.28f, 0.28f)
                            }
                            else -> 0f
                        }

                        val pcmSample = (sample * 32767).toInt().coerceIn(-32768, 32767)
                        buffer[i] = pcmSample.toShort()
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio synthesis failed", e)
            } finally {
                kotlin.runCatching {
                    audioTrack?.stop()
                    audioTrack?.release()
                }
                audioTrack = null
                if (currentType == type) {
                    currentType = SoundType.NONE
                }
            }
        }
    }

    fun stop() {
        currentType = SoundType.NONE
        synthJob?.cancel()
        synthJob = null
        kotlin.runCatching {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        }
        audioTrack = null
    }
}
