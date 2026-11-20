package com.shengyu.music

import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioListener
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        player.addAudioListener(object : AudioListener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizer?.release()
                equalizer = if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        Equalizer(0, audioSessionId).apply { enabled = true }
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }
        })
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_EQ) {
            applyEqualizerPreset(intent.getIntExtra(EXTRA_EQ_PRESET, 0))
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun applyEqualizerPreset(preset: Int) {
        val eq = equalizer ?: return
        try {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val profiles = arrayOf(
                intArrayOf(0, 0, 0, 0, 0),             // Flat
                intArrayOf(650, 420, 120, 0, 0),       // Bass
                intArrayOf(-250, 0, 420, 320, 120),    // Vocal
                intArrayOf(0, 0, 120, 420, 650),       // Treble
                intArrayOf(420, 180, -120, 180, 420),  // Pop
                intArrayOf(520, 250, -100, 260, 520)   // Rock
            )
            val values = profiles[preset.coerceIn(0, profiles.lastIndex)]
            for (i in 0 until bands) {
                val profileIndex = if (bands <= 1) 2 else (i * 4 / (bands - 1)).coerceIn(0, 4)
                val level = values[profileIndex].coerceIn(min, max)
                eq.setBandLevel(i.toShort(), level.toShort())
            }
        } catch (_: Exception) {
            // Some devices do not expose a hardware/software equalizer.
        }
    }

    override fun onDestroy() {
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SET_EQ = "com.shengyu.music.action.SET_EQ"
        const val EXTRA_EQ_PRESET = "preset"
    }
}
