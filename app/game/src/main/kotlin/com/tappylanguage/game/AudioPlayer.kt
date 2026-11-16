package com.tappylanguage.game

import android.content.res.AssetManager
import android.media.MediaPlayer
import android.util.Log

/** Plays one prompt clip at a time from assets. */
class AudioPlayer(private val assets: AssetManager) {
    private var player: MediaPlayer? = null

    fun play(file: String?) {
        if (file == null) return
        release()
        try {
            player = MediaPlayer().apply {
                assets.openFd(file).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Cannot play $file", e) // missing download: game continues silently
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
