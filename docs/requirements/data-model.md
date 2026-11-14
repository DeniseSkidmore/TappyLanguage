# Data model (draft)

Derived from [requirements.md](requirements.md). Conceptual, not an implementation: names and types will change. Selection math: [selection.md](selection.md).

| Diagram | Contents | Lifetime |
|---|---|---|
| [diagrams/language-pack.mmd](diagrams/language-pack.mmd) | Language packs and pair packs | Read-only, shipped |
| [diagrams/user-data.mmd](diagrams/user-data.mmd) | Skill rows, progress, settings | Saved on device |
| [diagrams/level-session.mmd](diagrams/level-session.mmd) | One level's plays and result | Memory only; folded into user data between levels, then discarded |

Diagrams reference objects defined in another diagram by foreign key (e.g. `Skill.itemId` → `Word.id`) instead of drawing them twice.

## Language files (proposal; see conversation thread 14)
- **One language pack per language** (Greek, English, …): words, phrases, audio, attribution. No translations. The English pack is shared by every pair, so English audio is never duplicated.
- **One pair pack per language pair** (el-en): word and phrase translations plus phrasebooks. Small, text only.
- Adding a new learning language adds its own pack, a pair pack, and only the English words and phrases that pair needs.

## Items
- **Words** are the targets (what the user taps).
- **Phrases** (= sentences) contain ordered words. A word or a phrase can be the prompt.
- **Letters** are not tracked as items. They are derived from word text and used only by the selection engine (introduce new words that share letters with words already learning or fluent).
- **Phrasebook** = a named selection of phrases. Studying one means first learning the words in its phrases, then the phrases once all their words are learning or fluent.

## Notes
- **Skill** is the core of spaced repetition: one row per item (word or phrase), language pair and combination, with running values only. `predictedRecall()` and `state()` are computed, not stored.
- **stability** stays a single value.
- **Skill state boundaries are dynamic**: the unproven/learning boundary moves to keep the learning pool small (see [selection.md](selection.md)).
- **ModeProgress** is per language pair and content mode. If gameplay interfaces multiply, the key gains the interface.
