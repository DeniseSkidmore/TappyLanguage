package com.tappylanguage.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Background = Color(0xFF101820)
private val WordColor = Color(0xFFF5F5F5)
private val Wrong = Color(0xFFE53935)
private val LevelUp = Color(0xFF4CAF50)
private val WordRowHeight = 56.dp
private val TopBarHeight = 56.dp
/** Fixed extra touch area around each word (requirements: slightly larger than the text, not adjusted in play). */
private val TouchMarginH = 16.dp
private val TouchMarginV = 16.dp

/** The one game screen: falling words, tap, feedback. A @Composable is a view written as a function. */
@Composable
fun GameScreen(controller: GameController) {
    // Frame loop: like a CompositionTarget.Rendering handler in WPF.
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { controller.tick(System.currentTimeMillis()) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Background)
            // Taps that no word consumed land here (child clickables handle theirs first).
            .pointerInput(Unit) { detectTapGestures { controller.onEmptyTap() } },
    ) {
        val fallHeight = maxHeight - TopBarHeight - WordRowHeight
        val now = controller.nowMs

        controller.falling.forEach { word ->
            key(word.id) {
                val p = controller.progressOf(word, now)
                Text(
                    text = word.text,
                    color = if (word.wrong) Wrong else WordColor,
                    fontSize = 22.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        // Place the measured word at xFraction of the free width, so it never leaves the screen.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            val freeWidth = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                            val x = (freeWidth * word.xFraction).roundToInt()
                            val y = (TopBarHeight + fallHeight * p).roundToPx()
                            layout(placeable.width, placeable.height) { placeable.place(x, y) }
                        }
                        .clickable { controller.onTap(word) }
                        .padding(horizontal = TouchMarginH, vertical = TouchMarginV),
                )
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            controller.bursts.forEach { b ->
                val age = ((now - b.startMs).toFloat() / GameController.BURST_MS).coerceIn(0f, 1f)
                // Approximate centre: xFraction of the free width, plus about half a word.
                val center = Offset(size.width * (0.1f + 0.8f * b.xFraction), size.height * b.yFraction.coerceAtMost(0.95f))
                val radius = 30f + 90f * age
                for (i in 0 until 12) {
                    val a = i * (Math.PI * 2 / 12)
                    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(b.color.copy(alpha = 1f - age), center + dir * (radius * 0.4f), center + dir * radius, strokeWidth = 6f)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val labelColor = when (controller.levelChange) {
                LevelChange.DOWN -> Wrong
                LevelChange.UP -> LevelUp
                LevelChange.SAME -> WordColor // "black" in the spec; light on this dark background
            }
            val wave = if (controller.wave > 1) "  wave ${controller.wave}" else ""
            Text("Level ${controller.levelLabel}$wave", color = labelColor, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Button(onClick = controller::togglePause) {
                Text(if (controller.phase == GameController.Phase.PAUSED) "Resume" else "Pause")
            }
        }

        when (controller.phase) {
            GameController.Phase.SHOW_ANSWER ->
                Text(controller.message, color = Wrong, fontSize = 44.sp, modifier = Modifier.align(Alignment.Center))
            GameController.Phase.BETWEEN_LEVELS ->
                Text(controller.message, color = WordColor, fontSize = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp))
            GameController.Phase.PAUSED ->
                Box(Modifier.align(Alignment.Center)) { Text("Paused", color = WordColor, fontSize = 32.sp) }
            GameController.Phase.PLAYING -> Unit
        }
    }
}
