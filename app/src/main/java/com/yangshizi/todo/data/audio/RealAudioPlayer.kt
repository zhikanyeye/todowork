package com.yangshizi.todo.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.yangshizi.todo.R

/**
 * 真实音频播放器
 *
 * 播放 res/raw 中的高保真 MP3 背景音资源（共 12 种）：
 * 篝火、海浪、溪流、细雨、风声、鸟语、咖啡、翻书、风铃、钢琴、八音盒、轻音
 *
 * 铃声和滴答声已迁移至 [ToneSynth] 纯代码合成，不再依赖外部音频文件。
 */
object RealAudioPlayer {

    private const val TAG = "RealAudioPlayer"
    private var bgPlayer: MediaPlayer? = null

    /**
     * 真实背景音类型（对应 res/raw 中的 MP3 文件）
     */
    enum class RealSound(val displayName: String, val rawResId: Int) {
        NONE("无背景音", 0),
        BONFIRE("篝火燃烧", R.raw.bonfire_pure),
        OCEAN_WAVES("海浪潮汐", R.raw.ocean_waves),
        FOREST_STREAM("林间溪流", R.raw.forest_stream),
        GENTLE_RAIN("细雨绵绵", R.raw.gentle_rain),
        NATURE_WIND("自然风声", R.raw.nature_wind),
        BIRDS_GARDEN("鸟语花香", R.raw.birds_garden),
        MORNING_COFFEE("清晨咖啡", R.raw.morning_coffee),
        PAGE_TURNING("翻书沙沙", R.raw.page_turning),
        WIND_CHIME("悠然风铃", R.raw.wind_chime),
        PIANO("轻柔钢琴", R.raw.piano),
        MUSIC_BOX("童话八音盒", R.raw.music_box),
        PURE_MUSIC("纯净轻音", R.raw.pure_music);

        companion object {
            fun fromName(name: String): RealSound {
                return try { valueOf(name) } catch (e: Exception) { NONE }
            }
        }
    }

    /**
     * 播放背景音（循环）
     */
    fun startBackground(context: Context, sound: RealSound) {
        if (sound == RealSound.NONE) {
            stopBackground()
            return
        }
        stopBackground()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val afd = context.resources.openRawResourceFd(sound.rawResId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0.6f, 0.6f)
                setOnPreparedListener { it.start() }
                setOnErrorListener { player, _, _ ->
                    Log.e(TAG, "Background MediaPlayer error for ${sound.name}")
                    player.release()
                    if (bgPlayer === player) bgPlayer = null
                    true
                }
            }
            bgPlayer = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background sound: ${sound.name}", e)
        }
    }

    /**
     * 停止背景音
     */
    fun stopBackground() {
        bgPlayer?.let {
            try { it.stop() } catch (e: Exception) {}
            try { it.release() } catch (e: Exception) {}
        }
        bgPlayer = null
    }

    /**
     * 停止所有音频（背景音 + 合成提示音）
     */
    fun stopAll() {
        stopBackground()
        ToneSynth.stopAll()
    }
}
