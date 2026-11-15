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
* Phrases (= sentences): ordered words, with audio. Used in sentence mode and phrasebooks.
* Phrasebooks (each is a named selection of phrases to study):
	* Getting directions
	* Airport
	* Restaurant
	* Store
	* Souvenirs
	* Cruise
	* Famous locations

Constraints:
* Audio must be recordings of human speakers, not AI-generated.
* Greek: no native speaker is available, so only existing recordings are used.
* English: a native English speaker is available, so missing English audio can be recorded by us. Each recording is indexed in INDEX.csv with its speaker as author and an open license (CC BY-SA or CC0, TBD).
* A word, sentence or phrase is playable only if it has a recording in the prompt language. (A text-only mode may be added later.)
* Where no single-word recording exists, a word may be taken from a slice of a longer recording.
* Translation data comes from an open-source dataset.
* No data source with rehosting or redistribution restrictions.

Phrasebooks:
* Claude drafts each phrasebook from items that have recordings, with Greek checked against Wiktionary.
* The user approves the English for each phrasebook before it is used.

Language files (see [data-model.md](data-model.md)): one pack per language (words, phrases, audio, attribution) plus one pair pack per language pair (translations, phrasebooks), so English audio is shared rather than duplicated, and a known language other than English can be added later with its own pair packs.

Later (not now): an in-app tool for editing datasets, including recording audio.

## Items
Detail in [data-model.md](data-model.md), "Items".
* **Translations** (a text in each language) are the learning unit. A word is a one-word translation; a phrase (= sentence) is a longer one. Both keep the same play data.
* **Targets** are the words of the target text, split on spaces. Prompts are never split.
* **Letters** are parts of words and are not tracked individually. The selection engine uses them only to prefer new words that share letters with words already learning or fluent.
* A **phrasebook** is studied in two steps: first the words in its phrases, then each phrase once all its words are learning or fluent. A word with no translation of its own doesn't block its phrase; as a target it gets more fluent distractors.
* Text is the key across files and in saved data; no numeric ids. Duplicate texts (homographs) are picked at random for now.

## Prompt / target combinations
The prompt is the audio played; the target is the text tapped.

| Combination | Prompt audio | Target text | Teaches |
|---|---|---|---|
| learning-learning | Greek | Greek | sound ↔ spelling |
| known-learning | English | Greek | meaning → Greek |
| learning-known | Greek | English | Greek → meaning |

known-known is excluded (teaches nothing).

Progression order:
1. **learning-learning** first: reading Greek teaches pronunciation, so later the learner can say a Greek word from its spelling.
2. **known-learning** next: meaning → Greek.
3. **learning-known** last, as review: reverses skills already learned.

Combinations unlock **per word**, soon after initial success: a word's next combination unlocks once the previous one reaches "learning" (not fluent).
When a word is selected, its combination is chosen with higher probability for the combination with the lowest success for that word.

## User history
Running values only; no long-term play log.
* During a level the app keeps that level's plays; they are folded into the running values while the between-levels screen shows, then discarded.
* Per item (word or phrase) **and per prompt/target combination** (separate values; each combination is a different skill), the app stores:
	* success: weighted average, recent plays weighted more (replaces the last-4 FIFO). A wrong tap counts as a full failure, a timeout as a partial one.
	* tap latency: weighted average, recent plays weighted more; sets the word's fall speed. Wrong taps don't change it; a timeout counts as 1.1 × the time the word was on screen.
	* difficulty: seeded from word length and frequency, then moved by play outcomes; used for word selection, not speed
	* stability
	* time last success: drives forgetting (predicted recall decays from the last proven recall)
	* time last played
* Repetitions are not stored; their effect shows in success, latency and stability.
* A word whose last play failed stays in rotation.
* How strongly recent results outweigh history (the weighting factor): TBD whether it auto-adapts per user (like FSRS-6's trainable forgetting curve) or is a setting.
* Current level number per game mode, kept across app launches.

## Play item selection
Draft; rules will be tuned by playing. Equations, with comparisons to published schemes (FSRS, Leitner, SM-2, half-life regression): see [selection.md](selection.md).

* Each word-combination has a **predicted recall** R (0–1), computed from weighted success and time since last success (forgetting curve). Comparable to FSRS retrievability, but used for word selection and difficulty, not only scheduling.
* A new word starts at R = 0 with the lowest stability, so it is simply the most unproven word; no special case.
* A word unseen for a long time decays toward low R and is treated like an unproven word, but its history gives it higher stability so it recovers faster (relearning). This also handles a user returning after a long break.
* Word states are fixed ranges of R, used only for yes/no rules (pool cutoff, one-unproven rule, combination unlock, phrase eligibility):
	* **unproven**: no success yet, or very low R. Presented only alongside better-known words, so the answer is clear from context.
	* **learning**: between.
	* **fluent**: R at or above the fluent threshold.
* Stability is a single value per skill.
* **Pool**: the level number is the pool size. The pool holds the words with the highest R below the fluent threshold, so fluent words fall back in as R decays.
* At most 1 new word (no success yet) in the pool. A forgotten word is not "new"; its low R ranks it.
* **Letter case**: learning-language targets appear both as written and in ALL CAPS (e.g. Greek signs), tracked as separate skills and mixed from level 1, so both forms are learned together while repeats are high.
* New words are preferred when they are short, frequent, in the selected phrasebook, and share letters with words already learning or fluent.
* **Targets** are drawn at random from the pool (no back-to-back repeat) until their expected tap times add up to about 5 minutes. Small pools therefore repeat words more.
* Distractors have higher R than the target; the weaker the target, the better known its distractors.
* A phrase is eligible only when all its words are learning or fluent.

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
* After failure due to word falling off edge of screen, flash the correct answer in red for 3 seconds before next prompt.
* After failure due to incorrect target selection, flash the distractor red and continue main gain loop waiting for success or failure due to word falling off edge of screen.
* Each level has a pause button that stops play for this session.

## Levels and difficulty
* Level 1 has 1 target word played 10 times. It assumes no prior knowledge: with only the right answer on screen, the association is learned from context alone.
* Once targets have prior success, the screen shows distractors as well as the target.
* Levels 1–4 last about 1 minute; from level 5 on, about 5 minutes. The level number sets the pool size; a higher level means more distinct words and fewer repeats per word.
* Each target's fall speed comes from its own tap latency, so the average tap lands about halfway down the screen; distractors on screen with it fall at the same speed. Speed is independent of the level number.
* At most 4 words on screen at once, to start; may later depend on level and screen size.
* At session start the level is lowered in proportion to how much the pool has been forgotten (see selection.md §9), so a returning player starts at a fitting level.
* The level number goes up when the previous level's success rate is above 90%.
* The pool keeps growing with the level until it holds every word and phrase in the language pack. At that point the level number stops increasing and the game shows "Max" in its place.
* Within a session, the level drops by 1 when a level's success rate is well below the historical average.
* Between levels: a message of about 3 seconds listing words whose success rate improved, a starburst, then the next level starts.
* The level number and running average level success rate is saved per game mode and restored on the next app launch.

## Modes and settings
* Gameplay interface (selectable in settings): falling words only, for now.
* Content mode (selectable in settings): word, sentence, or phrasebook.
* In sentence mode, all the words in the sentence will fall in correct order for the target language. (mixed with false targets)  Sentence success can therefore be partial: score = hits / (words in sentence + wrong taps). Always between 0 and 1, never negative; random tapping drives it toward 0.
