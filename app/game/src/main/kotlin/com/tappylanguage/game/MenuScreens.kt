package com.tappylanguage.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tappylanguage.engine.Params

private val Background = Color(0xFF101820)
private val TextColor = Color(0xFFF5F5F5)
private val Muted = Color(0xFFB0BEC5)
private val NotStarted = Color(0xFF9E9E9E)
private val LearningLow = Color(0xFF9E9E9E)
private val LearningHigh = Color(0xFFFFEB3B)
private val Fluent = Color(0xFF4CAF50)

enum class Screen { MENU, GAME, HOW_TO_PLAY, ABOUT_PACK, ABOUT_APP }

@Composable
private fun Page(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = TextColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        content()
        if (onBack != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

private val UrlPattern = Regex("""https?://[^\s,]+""")
private val LinkColor = Color(0xFF64B5F6)

/** Body text; any http(s) URL in it becomes a tappable link that opens in the browser. */
@Composable
private fun Body(text: String, color: Color = TextColor) {
    val linked = buildAnnotatedString {
        var last = 0
        UrlPattern.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            // LinkAnnotation.Url: Text opens it via the platform URI handler (C#: Process.Start(url)).
            withLink(LinkAnnotation.Url(m.value, TextLinkStyles(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline)))) {
                append(m.value)
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
    Text(linked, color = color, fontSize = 16.sp)
}

@Composable
fun MenuScreen(
    languageName: String,
    onPlay: () -> Unit,
    onReset: () -> Unit,
    onOpen: (Screen) -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Background).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TappyLanguage", color = TextColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(languageName, color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        val wide = Modifier.fillMaxWidth()
        Button(onClick = onPlay, modifier = wide) { Text("Play", fontSize = 20.sp) }
        Button(onClick = { onOpen(Screen.HOW_TO_PLAY) }, modifier = wide) { Text("How to Play") }
        Button(onClick = { onOpen(Screen.ABOUT_PACK) }, modifier = wide) { Text("About Language Pack") }
        Button(onClick = { onOpen(Screen.ABOUT_APP) }, modifier = wide) { Text("About App") }
        Button(onClick = { confirmReset = true }, modifier = wide) { Text("Reset player data") }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset player data") },
            text = { Text("Delete all progress for $languageName? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun HowToPlayScreen(onBack: () -> Unit) = Page("How to Play", onBack) {
    Body("Listen to the word, then tap it before it falls off the screen.")
    Body("In the beginning you'll have very few choices to guess, but it will get harder once you learn some words.")
    Body("Level up to get more and more words, but don't worry, if you leave for a while and forget we'll start at an easier level.")
    Body("Score above 90% to go up a level.")
    Body("Quit any time, your progress is saved after every level.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutPackScreen(
    languageName: String,
    words: List<WordProgress>,
    credits: List<AudioCredit>,
    onBack: () -> Unit,
) = Page("About Language Pack", onBack) {
    Body(languageName, Muted)

    Text("Progress", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val fluent = words.count { it.state == WordState.FLUENT }
    val learning = words.count { it.state == WordState.LEARNING }
    val notStarted = words.count { it.state == WordState.NOT_STARTED }
    Body("$fluent fluent, $learning learning, $notStarted not started", Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        words.forEach { WordChip(it) }
    }

    Text("License and attributions", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Body("Audio recordings:", Muted)
    credits.forEach { c ->
        Body("${c.text}: ${c.author}, ${c.license}\n${c.sourceUrl}")
    }
    if (credits.isEmpty()) Body("(no attribution data in this build)", Muted)
    Body("Word frequency: hermitdave/FrequencyWords (el_50k), CC BY-SA 4.0, https://github.com/hermitdave/FrequencyWords", Muted)
}

@Composable
private fun WordChip(word: WordProgress) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier.padding(0.dp)
    val modifier = when (word.state) {
        WordState.NOT_STARTED -> base.border(1.dp, NotStarted, shape)
        WordState.LEARNING -> base.background(
            lerp(LearningLow, LearningHigh, (word.recall / Params.FLUENT_THRESHOLD).toFloat().coerceIn(0f, 1f)), shape)
        WordState.FLUENT -> base.background(Fluent, shape)
    }
    val textColor = if (word.state == WordState.NOT_STARTED) NotStarted else Color(0xFF101820)
    Text(word.text, color = textColor, fontSize = 16.sp, modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
fun AboutAppScreen(versionName: String, onBack: () -> Unit) = Page("About App", onBack) {
    Body("TappyLanguage $versionName")
    Body("Copyright (C) 2026 TappyLanguage contributors: https://github.com/DeniseSkidmore/TappyLanguage/graphs/contributors")
    Body("This program is free software: you can redistribute it and/or modify it under the terms of the " +
        "GNU General Public License as published by the Free Software Foundation, either version 3 of the " +
        "License, or (at your option) any later version.")
    Body("This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without " +
        "even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU " +
        "General Public License for more details: https://www.gnu.org/licenses/gpl-3.0.html")
    Body("Source code: https://github.com/DeniseSkidmore/TappyLanguage", Muted)
    Body("Language data is licensed separately; see About Language Pack.", Muted)
}
