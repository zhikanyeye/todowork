package com.example.data.audio

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

object AmbientAudioSynth {
    private const val TAG = "AmbientAudioSynth"
    private const val SAMPLE_RATE = 44100
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    enum class SoundType(val displayName: String) {
        NONE("无背景音"),
        WHITE_NOISE("深度全神 (白噪音)"),
        PINK_NOISE("柔和遮蔽 (粉噪音)"),
        BROWN_NOISE("低频安定 (棕噪音)"),
        RAIN("雨落屋檐 (白噪音雨)"),
        OCEAN("潮起潮落 (海浪舒缓)"),
        STREAM("林间溪流 (清澈水声)"),
        FAN("恒定风扇 (机械白噪)"),
        FIREPLACE("炉火噼啪 (温暖木柴)"),
        CAFE("咖啡馆底噪 (远处人声)"),
        SPACE_DRONE("太空宇宙 (专注脑波)"),
        FOCUS_PAD("原创和弦铺底 (可听纯音)"),
        CUSTOM("自定义音乐 (导入外部音频)")
    }

    private var currentType = SoundType.NONE

    fun start(type: SoundType) {
        if (currentType == type && audioTrack != null && synthJob?.isActive == true) return
        stop()
        if (type == SoundType.NONE) return
        currentType = type

        synthJob = scope.launch {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                // Use builder to create modern audio track
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
                
                // Keep track multipliers
                var lastOut = 0f
                var brownOut = 0f
                var pinkB0 = 0f
                var pinkB1 = 0f
                var pinkB2 = 0f
                var pinkB3 = 0f
                var pinkB4 = 0f
                var pinkB5 = 0f
                var pinkB6 = 0f
                var phaseTheta = 0.0
                var waterPhase = 0.0
                var fanPhase = 0.0
                var cafePhase1 = 0.0
                var cafePhase2 = 0.0
                var crackle = 0f
                var dronePhase1 = 0.0
                var dronePhase2 = 0.0
                var dronePhase3 = 0.0
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
                            SoundType.PINK_NOISE -> {
                                val white = random.nextFloat() * 2f - 1f
                                pinkB0 = 0.99886f * pinkB0 + white * 0.0555179f
                                pinkB1 = 0.99332f * pinkB1 + white * 0.0750759f
                                pinkB2 = 0.96900f * pinkB2 + white * 0.1538520f
                                pinkB3 = 0.86650f * pinkB3 + white * 0.3104856f
                                pinkB4 = 0.55000f * pinkB4 + white * 0.5329522f
                                pinkB5 = -0.7616f * pinkB5 - white * 0.0168980f
                                val pink = pinkB0 + pinkB1 + pinkB2 + pinkB3 + pinkB4 + pinkB5 + pinkB6 + white * 0.5362f
                                pinkB6 = white * 0.115926f
                                (pink * 0.035f).coerceIn(-0.22f, 0.22f)
                            }
                            SoundType.BROWN_NOISE -> {
                                val white = random.nextFloat() * 2f - 1f
                                brownOut = (brownOut + white * 0.018f).coerceIn(-1f, 1f)
                                brownOut * 0.32f
                            }
                            SoundType.RAIN -> {
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.09f * (noise - lastOut)
                                lastOut = filtered
                                
                                var droplet = 0f
                                if (random.nextFloat() < 0.001f) {
                                    droplet = (random.nextFloat() * 2f - 1f) * 0.45f
                                }
                                (filtered * 0.7f + droplet * 0.3f) * 0.22f
                            }
                            SoundType.OCEAN -> {
                                phaseTheta += (2.0 * java.lang.Math.PI * 0.12) / SAMPLE_RATE
                                val modulator = (Math.sin(phaseTheta) + 1.0) / 2.0
                                
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.07f * (noise - lastOut)
                                lastOut = filtered
                                
                                filtered * modulator.toFloat() * 0.28f
                            }
                            SoundType.STREAM -> {
                                waterPhase += (2.0 * java.lang.Math.PI * 0.42) / SAMPLE_RATE
                                val ripple = ((Math.sin(waterPhase) + 1.0) / 2.0).toFloat()
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.18f * (noise - lastOut)
                                lastOut = filtered

                                var sparkle = 0f
                                if (random.nextFloat() < 0.004f) {
                                    sparkle = (random.nextFloat() * 2f - 1f) * 0.34f
                                }
                                (filtered * (0.12f + ripple * 0.12f) + sparkle * 0.18f)
                            }
                            SoundType.FAN -> {
                                fanPhase += (2.0 * java.lang.Math.PI * 58.0) / SAMPLE_RATE
                                phaseTheta += (2.0 * java.lang.Math.PI * 0.9) / SAMPLE_RATE
                                val hum = Math.sin(fanPhase).toFloat() * 0.08f
                                val bladePulse = Math.sin(phaseTheta).toFloat() * 0.025f
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.22f * (noise - lastOut)
                                lastOut = filtered
                                hum + bladePulse + filtered * 0.08f
                            }
                            SoundType.FIREPLACE -> {
                                val emberNoise = random.nextFloat() * 2f - 1f
                                val bed = lastOut + 0.045f * (emberNoise - lastOut)
                                lastOut = bed
                                if (random.nextFloat() < 0.0022f) {
                                    crackle = random.nextFloat() * 0.85f
                                }
                                crackle *= 0.82f
                                bed * 0.16f + crackle * (random.nextFloat() * 2f - 1f) * 0.45f
                            }
                            SoundType.CAFE -> {
                                cafePhase1 += (2.0 * java.lang.Math.PI * 185.0) / SAMPLE_RATE
                                cafePhase2 += (2.0 * java.lang.Math.PI * 246.0) / SAMPLE_RATE
                                phaseTheta += (2.0 * java.lang.Math.PI * 0.23) / SAMPLE_RATE
                                val roomMod = (0.55f + 0.45f * ((Math.sin(phaseTheta) + 1.0) / 2.0).toFloat())
                                val noise = random.nextFloat() * 2f - 1f
                                val murmur = lastOut + 0.035f * (noise - lastOut)
                                lastOut = murmur
                                val distantVoiceBand = (
                                    Math.sin(cafePhase1).toFloat() * 0.025f +
                                        Math.sin(cafePhase2).toFloat() * 0.018f
                                    ) * roomMod
                                murmur * 0.11f + distantVoiceBand
                            }
                            SoundType.SPACE_DRONE -> {
                                phaseTheta += (2.0 * java.lang.Math.PI * 0.05) / SAMPLE_RATE
                                val modulator = (Math.sin(phaseTheta) + 1.0) / 2.0
                                
                                dronePhase1 += (2.0 * java.lang.Math.PI * 110.0) / SAMPLE_RATE
                                dronePhase2 += (2.0 * java.lang.Math.PI * 164.81) / SAMPLE_RATE
                                dronePhase3 += (2.0 * java.lang.Math.PI * 220.0) / SAMPLE_RATE
                                
                                val wave1 = Math.sin(dronePhase1).toFloat()
                                val wave2 = Math.sin(dronePhase2).toFloat()
                                val wave3 = Math.sin(dronePhase3).toFloat()
                                
                                val synthChord = (wave1 * 0.4f + wave2 * 0.3f + wave3 * 0.3f)
                                
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.06f * (noise - lastOut)
                                lastOut = filtered
                                
                                (synthChord * 0.55f + filtered * 0.45f * modulator.toFloat()) * 0.13f
                            }
                            SoundType.FOCUS_PAD -> {
                                phaseTheta += (2.0 * java.lang.Math.PI * 0.035) / SAMPLE_RATE
                                val breath = (0.62f + 0.38f * ((Math.sin(phaseTheta) + 1.0) / 2.0).toFloat())
                                val seconds = padSampleCursor.toDouble() / SAMPLE_RATE
                                val chordStep = ((seconds / 8.0).toInt() % 4)
                                val root = doubleArrayOf(130.81, 98.00, 110.00, 87.31)[chordStep]
                                val third = doubleArrayOf(164.81, 123.47, 130.81, 110.00)[chordStep]
                                val fifth = doubleArrayOf(196.00, 146.83, 164.81, 130.81)[chordStep]
                                val high = doubleArrayOf(261.63, 196.00, 220.00, 174.61)[chordStep]

                                padPhase1 += (2.0 * java.lang.Math.PI * root) / SAMPLE_RATE
                                padPhase2 += (2.0 * java.lang.Math.PI * third) / SAMPLE_RATE
                                padPhase3 += (2.0 * java.lang.Math.PI * fifth) / SAMPLE_RATE
                                padPhase4 += (2.0 * java.lang.Math.PI * high) / SAMPLE_RATE

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
                                val melody = Math.sin(2.0 * java.lang.Math.PI * arpeggio[noteIndex] * seconds).toFloat() * noteEnvelope

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
                Log.e(TAG, "Audio tracked synthesis failed", e)
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
