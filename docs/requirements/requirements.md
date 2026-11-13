# Requirements

Working draft for the high-churn period before coding. Requirements move to GitHub issues for traceability once churn settles. `TBD` marks items still under discussion.

## Technology
To be installed: Kotlin, Android Studio, gh, Claude Code JetBrains plugin.
Frameworks and libraries: TBD.
Code license: GPL-3.0. Language data: CC BY-SA, in separate language files (see [datasets.md](datasets.md)).

## Architecture
* The selection engine (spaced repetition) is separate from the gameplay interface, so other interfaces can be added on top of it later.
* Engine and UI refer to a **prompt language** (the audio played) and an **answer language** (the text tapped), not to "Greek" and "English", so a reverse mode can be added later.
* The selection engine sits behind an interface so its rules can be replaced (for example by FSRS-based rules) without changing the gameplay.

## Datasets
Candidate sources and license implications: see [datasets.md](datasets.md). Storage and indexing rules: see `CLAUDE.md`, "Third-party data".

Language data the app needs:
* Greek audio for Greek words.
* English audio for the English translation of Greek words.
* Greek word frequency.
* Sentences (for sentence mode).
* Phrasebooks (each is a named subset of words, phrases or sentences):
	* Letter sounds
	* Getting directions
	* Airport
	* Restaurant
	* Store
	* Souvenirs
	* Cruise
	* Famous locations

Constraints:
* Audio must be recordings of human speakers, not AI-generated. No native speaker is available to record, so only existing recordings are used.
* A word, sentence or phrase is playable only if it has a recording in the prompt language. (A text-only mode may be added later.)
* Where no single-word recording exists, a word may be taken from a slice of a longer recording.
* Translation data comes from an open-source dataset.
* No data source with rehosting or redistribution restrictions.

Phrasebooks:
* Claude drafts each phrasebook from items that have recordings, with Greek checked against Wiktionary.
* The user approves the English for each phrasebook before it is used.

Later (not now): an in-app tool for editing datasets, including recording audio.

## User history
For each word played, the app stores:
* number of repetitions
* overall success rate
* success/fail flags of the last 4 plays (FIFO)
* time last played

For each letter played, the app stores the same four fields.
The app stores the current level number per game mode, kept across app launches.

## Play item selection
Draft rules; exact spaced-repetition rules TBD (being compared with FSRS and other published schemes). Rules will be tuned by playing.

* A word is **unfamiliar** if any of these hold: fewer than 10 repetitions; overall success rate below 75%; recent success rate (last 4 plays) below 100%. One miss therefore makes the word come up repeatedly until it is familiar again (intended).
* A level contains at most 5 distinct unfamiliar words.
* Lower success rate → higher selection probability.
* High repetitions and high success rate → lower selection probability.
* Between words with equal repetitions and success rate, selection probability is higher for:
	* words more frequent in the language
	* words in the currently selected phrasebook
	* shorter words
* The lower a word's repetitions or success rate, the more times it repeats within a level.
* The level number is an input to target selection (see Levels).

## Game play
* The game requests play items from the selection engine.
* A play item appears at the top of the screen showing its text in the answer language.
* Play items move down the screen and disappear when they reach the bottom.
* One visible play item at a time is the **target**. Its audio plays aloud, randomly in either Greek or English.
* Tapping the target is a success: green starburst at the tap location.
* Tapping a word other than the target is a failure for both the target and the tapped word: red starburst at the tap location.
* The target reaching the bottom untapped is a failure: red starburst where it left the screen.
* A non-target word reaching the bottom is ignored (no history update).
* After a success, or a failure from the target reaching the bottom, a new visible play item becomes the target and its audio plays.
* Each level has a pause button that stops play for this session.

## Levels and difficulty
* Level 1 has 1 target word played 10 times. It assumes no prior knowledge: the user learns the first association from context alone.
* Each following level adds target words and reduces repeats per word. Exact formula TBD; it also has to respect the 5-unfamiliar-words limit.
* Difficulty settings control:
	* number of words per level
	* number of distinct play items per level (items repeat to fill the level)
	* number of words on screen at once
	* speed the words move
* Difficulty increases when the previous level's success rate is above 90%.
* Between levels: a message of about 3 seconds listing words whose success rate improved, a starburst, then the next level starts.
* The level number is saved per game mode and restored on the next app launch.

## Modes and settings
* Gameplay interface (selectable in settings): falling words only, for now.
* Content mode (selectable in settings): word, sentence, or phrasebook. How sentences display on screen: TBD.
