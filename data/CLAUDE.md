# Data directives
Loaded when working with files under `data/`. Root rule still applies: never use a source whose terms restrict rehosting or redistribution.

## Layout
- Language data: `data/language/<language>/<source>/` (e.g. `data/language/greek/commons/`). GUI image data: `data/graphics/<source>/`.
- Each of `data/language/<language>/` and `data/graphics/` keeps an `INDEX.csv`, one row per file: `path,source_url,author,license,retrieved`. Rows for audio slices also carry `start_ms,end_ms` (a slice is a modification: the credits mark it as an excerpt).
- Pack sources (our own text data, CC BY-SA): `data/language/<language>/packs/*.json`: the language pack (e.g. `el-prototype.json`) and pair packs (e.g. `el-en.json`, translations adapted from English Wiktionary). The app's `buildPack` task reads them and drops items whose audio file is missing, and their translations.
- Lookup cache: `data/language/<language>/cache/` (Commons file list and per-file author/license, Wiktionary definitions). Check it before any request; never request what is already cached.

## Git
- Binary data is not committed during this phase (gitignored). Committed: `INDEX.csv` files, pack sources, and this file. Hosting for built language packages is decided later.
- Releases ship a language pack per language plus a pair pack per language pair, with attributions generated from each language's `INDEX.csv`.

## Choosing content
- Licenses accepted: CC BY-SA, CC BY, CC0, public domain. Record the license and author per file; Commons licenses vary per file.
- Leave out profanity.
- Word recordings: one word per file. Phrases and multi-form files are not single words. Conjugation tables are included: split per form by forced alignment into slice rows (`start_ms,end_ms`), one word item per form. A pronoun read aloud stays in the Greek text and goes into the English ("εσύ έχεις" / "you have").
- VoxPopuli: only as extra recordings, never a word's only source.

## Polite downloading (Wikimedia and other non-profit providers)
- One request at a time, at least 120 s apart; batch API queries (up to 50 titles per request).
- Save results after every request so an interrupted run loses nothing.
- Lookups are cached in `data/language/greek/cache/` (gitignored, durable): `wiktionary_en.json` (definitions), `commons_meta.json` (author and licence per file) and `commons_titles.json` (the Commons file list). Cached entries are never requested again, and the cache is saved after every request.
- Stop at the first HTTP 429; don't retry in a loop. Wait at least an hour before re-running, and tell the user.
- User-Agent: tool name, a link to `.claude/skills/fetch-audio/SKILL.md` on GitHub, and the contact address from the `TAPPY_CONTACT` environment variable, which the user sets. Never write the address into the repo. Don't send requests while it is unset.
- Audio downloads go through the fetch-audio skill.

## Context Preservation
- Save scripts written to download data so if a request needs to be re-run due to rate limiting, we can finish later.  Set all scripts up to skip already cached data.
