# Selection equations (draft)

How the selection engine scores skills and builds a level. All constants are starting guesses, to be tuned by playing. Each equation notes how it compares with published schemes (references in `docs/open-items.md`).

Notation: one **skill** = one item (word or phrase) × language pair × combination (see [data-model.md](data-model.md)). `t` = time now. Times in days.

## 1. Running values (updated between levels)
For each play of a skill, with outcome x = 1 (success) or 0 (failure):

| Value                              | Update                    | Start |
| ---------------------------------- | ------------------------- | ----- |
| success average s                  | s ← (1 − α)·s + α·x       | 0     |
| latency average ℓ (successes only) | ℓ ← (1 − α)·ℓ + α·latency | ℓ_max |
| repetitions n                      | n ← n + 1                 | 0     |
| last played                        | t                         | none  |
| last success                       | t if x = 1                | none  |

α = recency weight (0.2 to start). Open question: fixed setting or auto-adapted per user.
- Compares with: half-life regression uses counts of correct/wrong; this weighted average is the same idea with recent plays counting more. Replaces the last-4 FIFO.

> Recentcy weight might be relative to overall recentcy weight?  If the player has not played in some time all the words would be non-recent.  If they've been playing for 24 hours straight then there's a high average recency.  TBD
> Please use variable names instead of single letters in formulas.  I'm a programmer not a mathematician.
> Switch the order of the file so that we start with the level play log, and derive values in order before getting to the running values saved.  Then take the data from running values and compute word selection.  Define each variable before it's introduced in a formula.
> Repetitions isn't used in any downstream calculations?  Anything we could derive from this is implied by the success/latency and last played?  (More repetitions over time should increase success, and decrease latency, and if it doesn't then we don't want to correct for having had lots of reps.)  If you agree, remove repetitions from the sored data.

## 2. Difficulty
Ease from latency, 0 (slow) to 1 (fast):
  e = clamp((ℓ_max − ℓ) / (ℓ_max − ℓ_min), 0, 1)

> We have been talking about difficulty as one variable that  controls multiple settings, but if we're measuring latency then speed can be an independent factor.  Level speed should be tuned to the latency of the expected words.  Latency is a recentcy weighted average, so should predict the user's ability to gain a success before the word reaches the bottom of the screen.  It rolls both the user's personal reaction time and their word fluency together.  We'd need standard deviation as well to accurately predict 90% success rate, but we might do well enough with the average being 50% of screen travel?  Users with a low standard deviation will hit most of the targets in the middle, I think that's fine.  A failure due to tapping the wrong word should not affect latency.  A failure due to the word falling off screen should count towards latency as 1.1 times opportunity time (time between prompt and word falling off the edge), thereby driving up latency and slowing down the word on next attempt.  
> With this formula, initial value for latency not start at mathematical max, or the first level would be nearly frozen in time.  Start at 30 seconds.

Difficulty, 0 (easy) to 1 (hard):
  D = clamp(D_len · (1 − e·k_e), 0, 1),  D_len = clamp(length / L_ref, D_min, 1)

> please use longer variable names so this is human readable.  if D_len is difficulty due to length, do you mean word_length?  Where word_length is some sort of score of letters/max_letters_per_word?  I think we need separate formulas for initial word difficulty (based on word length and language frequency), and running difficulty (recency biased modification based on success/failure)
> Language Frequency doesn't actually affect difficulty for true naive learners, but a) is does affect difficulty for those who have tried other methods, and b) it allows us to easily incorporate language frequency into initial word selection.

Start: e = 0, so D = D_len (longer words start harder). Constants: L_ref = 8 letters, D_min = 0.2, k_e = 0.6, ℓ_min = 0.6 s, ℓ_max = item fall time.
- Compares with: FSRS sets initial difficulty from the first rating, then moves it with each rating. We set it from word length, then let tap speed calibrate it. FSRS has no latency input; we use latency in place of its Hard/Good/Easy ratings.

## 3. Stability (days)
On success:  S ← S · (1 + g · (1 − D) · (1 − R))
On failure:  S ← max(S_min, S · m_fail)
Start: S = S_0.

Constants: S_0 = 0.1 day, S_min = 0.05 day, g = 3, m_fail = 0.5.
- Growth is largest when recall was hard (low R) and the item is easy (low D). During massed practice R is near 1, so ten correct taps in a row add little stability. This handles same-day repeats without special rules.
> this may conflict with my earlier breaking difficulty into two components of latency and success rate.  We do need to have separate information concerning word selection and game play.  Propose ideas.
- A failure halves stability; it never resets to zero, so a previously known word relearns faster (no "ease hell").
- > Penalize a failure by wrong tap more than by timeout.  Many timeouts per game should just slow the game down, not alter word selection as much.
- Compares with: a simplified FSRS stability update (FSRS also multiplies by a factor that grows with (1 − R) and shrinks with D; on a lapse FSRS uses a separate post-lapse formula). FSRS-7 splits S into fast and slow; we keep one value.

## 4. Predicted recall R (0 to 1)
  R = s · (1 + F · Δt / S)^C,  Δt = t − last success

F = 19/81, C = −0.5 (the FSRS-4.5 forgetting curve: recall reaches 0.9 when Δt = S). A skill with no success yet has R = 0.
- The extra factor s folds in the weighted success, so a word with shaky recent results reads as less known even right after a success.
- > Yes good, given our high repeat opportunities count, a success can breed more success and fun game play, but enough success will move the word to the fluent category and reduce frequency of selection to keep it from being boring and repetitive.
- > Is this the same thing as our fluency percentage that we use to categorize unproven-learning-fluent?  I think it's good for fluency category to decay over time and to bring back previously fluent words for review just as in normal spaced repetition.  The thing I'm getting at with the fluency categories is that we want to mostly drill words with some but not a lot of recall, and only introduce words with no or very low recall among high recall words so it can be chosen by process of elimination until it enters a moderate recall level.
 > If predicted recall falls over time between plays, and we use hard thresholds, then all the words will be lumped into the same category.  Further thoughts needed on word selection phase derived from predicted recall.
 > We expect previously known words to increase success rate faster than true learning words even after an extended gap.  Should we account for this or just let it organically happen that the success rate will climb faster and bump the word back into the fluent category faster?
- Compares with: FSRS retrievability (same curve shape); half-life regression uses p = 2^(−Δt/h). Leitner boxes are a coarse step version of this.

## 5. Skill states and the dynamic boundary
  fluent:    R ≥ b
  learning:  a ≤ R < b
  unproven:  R < a

b = 0.85 (fixed). a is dynamic, so the learning pool stays small:
  a = max(a_min, R of the N_pool-th highest non-fluent skill)

Constants: a_min = 0.1, N_pool = 15. Skills just below the cut wait as unproven until the pool has room.
- Compares with: Anki caps new cards per day; this caps the number of skills in progress at once instead.

Is the pool the number of words to be selected or the size of the learning category?  We have levels not days, we might pick a smaller number of words for a level than we do for the day.  The decay rate means that R will decrease slightly even between levels, which will refigure our pools.  In an absolute best case scenario, words would move from learning to fluent during a level, and we'd have new learning words in the next level.  In the worst case most of the words remain in learning, and some descend to unproven.  Trying to balance maintaining interest by not over-using the same words, and not being too difficult by not having enough of the same words.

## 6. Selection weight
For each eligible skill:
  W = need · bonus · freshness

- need = 1 − R
- bonus = 1 + λ_f·freq + λ_p·inPhrasebook + λ_s·shortness + λ_o·overlap (tie-breakers; λ around 0.1)
	- freq = 1 − rank / rank_max
	- shortness = 1 − D_len
	- overlap = share of the word's letters that appear in learning or fluent words
- freshness = 1 − μ · exp(−(t − last played) / τ), with μ = 0.3, τ = 10 minutes. A light penalty for a skill just played; never removes a failed skill from rotation.

> An advertised desirable frequency for leaning is "15 minutes a day".  An advertised desirable duration for games is a few hours a day.  Unclear what this means for an ideal τ.  If a level is around 5 minutes, and people play 3-40 levels per session, how would that compare to the more established repetition patterns?  Draw me some 2D graphs for the functions with decay factors so I can more easily understand it.

Compares with: these are your original tie-breakers made continuous. Leitner, SM-2 and FSRS pick items by due date only; this weighting is our own.

> We definitely want continuous functions and sliding boundaries rather than due dates to accommodate different play intervals and durations.  The player decides how much they study and how long, we can only control what they study in the next level.

## 7. Building a level
Plays per level P comes from difficulty (P = 10 at the start).
1. Candidate unproven skill: at most 1 per level, and only if every other target is at least learning. New words are picked by W (favoring frequent, short, high letter overlap, in the selected phrasebook).
2. Fill target slots with learning skills by W, up to 5 non-fluent; then fluent skills.
	> Up to 10 learning, only one Unproven, the rest fluent for targets.  Distractors on screen at the same time should have higher recall than the target word, and may include more fluent words.
3. Plays per target: P_i = round(P · W_i / ΣW).
	Maybe we just want to decrease chances of a re-pick over time and not set specific plays per target per level.  Increase the amount of words to pick targets from over time.  So at level 1 we're only considering the top word and therefore it gets picked for every target.  But by level 10 there's 10 words to pick from so we end up re-picking some but not all are picked.
4. Distractors: words with higher R than the target, only once every target has prior success.  When there are very few fluent words, use duplicate targets and also count as successes in place of distractors.
5. Combination for each play of a word: P(c) ∝ (1 − R_c) + ε over unlocked combinations (ε = 0.05). The next combination unlocks when the previous one reaches learning.
6. Phrases: eligible only when every word in the phrase is learning or fluent (in the phrase's combination).

Check, level 1: one new skill (R = 0, need = 1) gets all 10 plays, with no distractors.

> We need to consider choosing targets and false targets for each level.  The lower the recall probability of a target the higher the recall probability of the false targets on screen should be.  As mentioned level 1 has no candidates for higher recall probability, so we would just make all the targets true targets with no false targets.

## 8. Level result and difficulty
- Level success rate = successes / plays.
- levelSuccessAvg ← (1 − α_L) · levelSuccessAvg + α_L · rate, α_L = 0.3.
- Difficulty + 1 if rate > 0.9.
- Difficulty − 1 if rate < levelSuccessAvg − 0.2 (e.g. after a long break).
- Sentence mode: phrase score = hits / (words + wrong taps).
