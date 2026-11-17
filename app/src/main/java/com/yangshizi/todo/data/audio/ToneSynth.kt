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

/**
 * 合成提示音生成器
 *
 * 不依赖任何外部音频文件，使用 AudioTrack 实时合成以下提示音：
 * - 专注倒计时滴滴声（最后10秒每秒短促蜂鸣）
 * - 计时完成叮咚声（双音阶衰减包络）
 * - 专注滴答声（每秒一次的机械节拍）
 */
object ToneSynth {

    private const val TAG = "ToneSynth"
    private const val SAMPLE_RATE = 44100

    private var countdownTrack: AudioTrack? = null
    private var completionTrack: AudioTrack? = null
    private var tickingTrack: AudioTrack? = null
    private var tickingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * 播放一次短促的倒计时蜂鸣（约150ms，880Hz方波带衰减）
     */
    fun playCountdownBeep() {
        playShortTone(880.0, 0.15, 0.6f, AudioAttributes.USAGE_ALARM)
    }

    /**
     * 播放计时完成的"叮咚"声
     *
     * 先播放高音(1318Hz)再播放低音(988Hz)，各持续约400ms，
     * 用指数衰减包络模拟自然钟声余韵。
     */
    fun playCompletionSound() {
        stopCompletion()
        try {
            val durationSec = 1.6
            val totalSamples = (SAMPLE_RATE * durationSec).toInt()
            val buffer = ShortArray(totalSamples)

            // 第一段：高音叮 (1318Hz ≈ E6)，0.0~0.5s
            val hiFreq = 1318.0
            val hiEnd = (SAMPLE_RATE * 0.5).toInt()
            for (i in 0 until hiEnd) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = Math.exp(-t * 3.5)
                val wave = Math.sin(2.0 * Math.PI * hiFreq * t)
                val secondHarmonic = Math.sin(2.0 * Math.PI * hiFreq * 2 * t) * 0.3
                val sample = (wave + secondHarmonic) * envelope * 0.5
                buffer[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

            // 第二段：低音咚 (988Hz ≈ B5)，0.5~1.6s
            val loFreq = 988.0
            for (i in hiEnd until totalSamples) {
                val t = (i - hiEnd).toDouble() / SAMPLE_RATE
                val envelope = Math.exp(-t * 2.0)
                val wave = Math.sin(2.0 * Math.PI * loFreq * t)
                val secondHarmonic = Math.sin(2.0 * Math.PI * loFreq * 2 * t) * 0.25
                val sample = (wave + secondHarmonic) * envelope * 0.45
                buffer[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

            playBuffer(buffer, AudioAttributes.USAGE_ALARM)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play completion sound", e)
        }
    }

    /**
     * 开始持续滴答声（专注时每秒一次的节拍）
     *
     * 合成方案：每秒产生一个短促的"嗒"声（2000Hz方波，40ms衰减），
     * 模拟机械秒表的清脆节拍感。
     */
    fun startTicking() {
        stopTicking()
        tickingJob = scope.launch {
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
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

                tickingTrack = track
                track.play()

                val tickFreq = 2000.0
                val tickDurationSamples = (SAMPLE_RATE * 0.04).toInt() // 40ms
                val gapDurationSamples = SAMPLE_RATE - tickDurationSamples // ~960ms silence
                val tickBuffer = ShortArray(tickDurationSamples)
                val gapBuffer = ShortArray(gapDurationSamples)

                // 生成"嗒"声：方波 + 指数衰减
                for (i in 0 until tickDurationSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = Math.exp(-t * 80.0)
                    val wave = Math.signum(Math.sin(2.0 * Math.PI * tickFreq * t))
                    val sample = wave * envelope * 0.25
                    tickBuffer[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }

                track.setVolume(0.5f)
                while (isActive) {
                    track.write(tickBuffer, 0, tickBuffer.size)
                    track.write(gapBuffer, 0, gapBuffer.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ticking synthesis failed", e)
            } finally {
                kotlin.runCatching {
                    tickingTrack?.stop()
                    tickingTrack?.release()
                }
                tickingTrack = null
            }
        }
    }

    /**
     * 停止滴答声
     */
    fun stopTicking() {
        tickingJob?.cancel()
        tickingJob = null
        kotlin.runCatching {
            tickingTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                release()
            }
        }
        tickingTrack = null
    }

    /**
     * 停止完成提示音
     */
    fun stopCompletion() {
        kotlin.runCatching {
            completionTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                release()
            }
        }
        completionTrack = null
    }

    /**
     * 停止倒计时蜂鸣
     */
    fun stopCountdown() {
        kotlin.runCatching {
            countdownTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                release()
            }
        }
        countdownTrack = null
    }

    /**
     * 停止所有合成提示音
     */
    fun stopAll() {
        stopTicking()
        stopCompletion()
        stopCountdown()
    }

    // ---- 内部工具方法 ----

    /**
     * 播放一个短促的单音
     */
    private fun playShortTone(freq: Double, durationSec: Double, volume: Float, usage: Int) {
        try {
            val totalSamples = (SAMPLE_RATE * durationSec).toInt()
            val buffer = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = Math.exp(-t * 8.0)
                val wave = Math.sin(2.0 * Math.PI * freq * t)
                val sample = wave * envelope * volume
                buffer[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

            playBuffer(buffer, usage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play short tone", e)
        }
    }

    /**
     * 将PCM缓冲写入AudioTrack播放
     */
    private fun playBuffer(buffer: ShortArray, usage: Int) {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize * 2, buffer.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            // 注册到对应引用以便后续可停止
            when (usage) {
                AudioAttributes.USAGE_ALARM -> {
                    completionTrack?.release()
                    completionTrack = track
                }
                else -> {
                    countdownTrack?.release()
                    countdownTrack = track
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play buffer", e)
        }
    }
}
