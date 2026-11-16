package com.tappylanguage.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioPlayer
    private lateinit var controller: GameController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = PackLoader.load(assets, "packs/el-prototype.json")
        audio = AudioPlayer(assets)
        controller = GameController(pack, ProgressStore(File(filesDir, "progress-el.json")), audio)
        setContent { MaterialTheme { GameScreen(controller) } }
    }

    override fun onPause() {
        super.onPause()
        controller.pause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}
