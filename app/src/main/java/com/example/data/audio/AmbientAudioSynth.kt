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
        RAIN("雨落屋檐 (白噪音雨)"),
        OCEAN("潮起潮落 (海浪舒缓)"),
        SPACE_DRONE("太空宇宙 (专注脑波)")
    }

    private var currentType = SoundType.NONE

    fun start(type: SoundType) {
        if (currentType == type) return
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
                var phaseTheta = 0.0
                var dronePhase1 = 0.0
                var dronePhase2 = 0.0
                var dronePhase3 = 0.0

                while (isActive) {
                    for (i in buffer.indices) {
                        val sample: Float = when (type) {
                            SoundType.WHITE_NOISE -> {
                                val noise = random.nextFloat() * 2f - 1f
                                val filtered = lastOut + 0.35f * (noise - lastOut)
                                lastOut = filtered
                                filtered * 0.12f
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
