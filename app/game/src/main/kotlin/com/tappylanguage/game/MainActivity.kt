package com.tappylanguage.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

private const val PACK = "packs/el-prototype.json"
private const val CREDITS = "packs/el-prototype-credits.json"
private const val LANGUAGE_NAME = "Greek"

/**
 * Hosts the screens. `screen` picks which composable shows (C#: a ContentControl bound to a view-model
 * property); no navigation library.
 */
class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioPlayer
    private lateinit var progressStore: ProgressStore
    private lateinit var playLog: PlayLog
    private val pack by lazy { PackLoader.load(assets, PACK) }

    private var screen by mutableStateOf(Screen.MENU)
    /** Created on the first Play; kept (paused) while the menu shows; dropped on reset. */
    private var controller by mutableStateOf<GameController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = AudioPlayer(assets)
        audio.preload(pack.items.mapNotNull { it.audioFile })
        progressStore = ProgressStore(File(filesDir, "progress-el.json"))
        playLog = PlayLog(File(filesDir, "playlogs"))
        setContent {
            MaterialTheme {
                BackHandler(enabled = screen != Screen.MENU) { showMenu() }
                when (screen) {
                    Screen.MENU -> MenuScreen(LANGUAGE_NAME, onPlay = ::play, onReset = ::reset, onOpen = { screen = it })
                    Screen.GAME -> controller?.let { GameScreen(it, onMenu = ::showMenu) }
                    Screen.HOW_TO_PLAY -> HowToPlayScreen(onBack = ::showMenu)
                    Screen.ABOUT_PACK -> AboutPackScreen(
                        LANGUAGE_NAME,
                        PackInfo.progress(pack, progressStore, System.currentTimeMillis()),
                        PackInfo.credits(assets, CREDITS),
                        onBack = ::showMenu,
                    )
                    Screen.ABOUT_APP -> AboutAppScreen(versionName(), onBack = ::showMenu)
                }
            }
        }
    }

    /** Each Play is a session start: a new game applies the session-start level; a paused one resumes. */
    private fun play() {
        val c = controller ?: GameController(pack, progressStore, playLog, audio).also { controller = it }
        c.resume()
        screen = Screen.GAME
    }

    private fun showMenu() {
        controller?.pause()
        screen = Screen.MENU
    }

    private fun reset() {
        controller = null // drops the unfinished level too
        progressStore.delete()
        playLog.clear()
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    override fun onPause() {
        super.onPause()
        controller?.pause()
        if (screen == Screen.GAME) screen = Screen.MENU // come back to the menu, game paused
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}
