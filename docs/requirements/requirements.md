# Requirements

Working draft for the high-churn period before coding. Requirements move to GitHub issues for traceability once churn settles. `TBD` marks items still under discussion.

## Technology
To be installed: Kotlin, gh, Claude Code JetBrains plugin. Installed: Android Studio.
Frameworks and libraries: TBD.
Code license: GPL-3.0 (`LICENSE`). Language data: CC BY-SA, in separate language files (see [datasets.md](datasets.md)).
Final language data file format: TBD. For now, audio slices are stored as timestamps into the source recording.

## Architecture
* The selection engine (spaced repetition) is separate from the gameplay interface, so other interfaces can be added on top of it later.
* Engine and UI refer to a **learning language** (Greek) and a **known language** (English), not to "Greek" and "English" directly.
* Spaced repetition is our own algorithm, designed for learning from scratch inside a game. FSRS and other published schemes are references, not dependencies.
* The selection engine sits behind an interface so its rules can be replaced or tuned without changing the gameplay.

## Design principles
* The game must stay fun. No rule may trap a word in a permanently penalized state (cf. SM-2 "ease hell"); recent results must always be able to pull a word back.
* Probability, not buckets: lowering a word's selection probability lowers how often it is drawn (a continuous version of Leitner boxes).
* All words start difficult and get easier. Learning starts with shorter words.

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

## Prompt / target combinations
The prompt is the audio played; the target is the text tapped.

| Combination | Prompt audio | Target text | Teaches |
|---|---|---|---|
| learning-learning | Greek | Greek | sound ↔ spelling |
| known-learning | English | Greek | meaning → Greek |
| learning-known | Greek | English | Greek → meaning |

known-known is excluded (teaches nothing). Which combinations are in the first release: TBD.

## User history
Running values only; no long-term play log.
* During a level the app keeps that level's plays; they are folded into the running values while the between-levels screen shows, then discarded.
* Per word **and per prompt/target combination** (TBD: confirm separate values per combination), the app stores:
	* repetitions
	* success: weighted average, recent plays weighted more (replaces the last-4 FIFO)
	* tap latency: weighted average, recent plays weighted more; calibrates difficulty
	* time last played
	* time last success (TBD)
* Letters: the same fields.
* Current level number per game mode, kept across app launches.

## Play item selection
Draft; exact rules TBD. Rules will be tuned by playing. Each rule will be compared against published schemes (FSRS, Leitner, SM-2, half-life regression) when drafted.

* Word states (draft, replaces the single "unfamiliar" threshold):
	* **new / unproven**: no recent success. Presented only alongside words the user has already succeeded at, so the answer is clear from context.
	* **learning**: some recent success, but below the fluency threshold.
	* **fluent**: at or above the fluency threshold.
* A level contains at most 5 distinct non-fluent words (exact split between new and learning TBD).
* Lower weighted success → higher selection probability.
* High repetitions and high weighted success → lower selection probability.
* Initial difficulty depends on word length (shorter = easier). Tap latency then calibrates it.
* Between otherwise equal words, selection probability is higher for:
	* words more frequent in the language
	* words in the currently selected phrasebook
	* shorter words
* The lower a word's repetitions or success, the more times it repeats within a level.
* The level number is an input to target selection (see Levels).

## Game play
* The game requests play items from the selection engine.
* A play item appears at the top of the screen showing its target text.
* Play items move down the screen and disappear when they reach the bottom.
* One visible play item at a time is the **target**. Its prompt audio plays aloud, using one of the enabled prompt/target combinations.
* Tapping the target is a success: green starburst at the tap location.
* Tapping a word other than the target is a failure for both the target and the tapped word: red starburst at the tap location.
* The target reaching the bottom untapped is a failure: red starburst where it left the screen.
* A non-target word reaching the bottom is ignored (no history update).
* After a success, or a failure from the target reaching the bottom, a new visible play item becomes the target and its audio plays.
* Each level has a pause button that stops play for this session.

## Levels and difficulty
* Level 1 has 1 target word played 10 times. It assumes no prior knowledge: with only the right answer on screen, the association is learned from context alone.
* A new word is added each time one breaks "unproven" threshold. Once every target in a level has prior success, the screen shows more options than correct targets.
* Each following level adds target words and reduces repeats per word. Exact formula TBD; it also has to respect the non-fluent cap.
* Difficulty settings control:
	* number of words per level
	* number of distinct play items per level (items repeat to fill the level on easier difficulty)
	* number of words on screen at once (more words is more difficult)
	* speed the words move (faster is more difficult)
* Difficulty increases when the previous level's success rate is above 90%.
* We may need some way to globally reduce difficulty when user has been away from the game too long and needs a reduced word set to build back up from.  This can be determined by a significantly lower level success rate than historical average.
* Between levels: a message of about 3 seconds listing words whose success rate improved, a starburst, then the next level starts.
* The level number and running average level success rate is saved per game mode and restored on the next app launch.

## Modes and settings
* Gameplay interface (selectable in settings): falling words only, for now.
* Content mode (selectable in settings): word, sentence, or phrasebook.
* In sentence mode, all the words in the sentence will fall in correct order for the target language. (mixed with false targets)  Phrase success can therefore be partial, scored as number of successful word hits/total words in sentence.  TBD should we penalize wrong hits further in this formula?  We don't want to make a formula that can have a negative result if user is clicking randomly.
