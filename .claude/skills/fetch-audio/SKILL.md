---
name: fetch-audio
description: Download the third-party audio files listed in a language's INDEX.csv into data/language/<language>/<source>/, skipping files already present and reporting files the source no longer serves. Use when setting up a new machine to build the app, or after INDEX.csv gains rows.
---

# fetch-audio

Binary language data is gitignored; only `INDEX.csv` is committed (see CLAUDE.md, "Third-party data"). This skill restores the files from the index.

## Steps
1. Ask which language if not given (folder under `data/language/`, e.g. `greek`).
2. Optional preview: `"$TAPPY_PYTHON" -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/<language> --dry-run`
3. Run: `"$TAPPY_PYTHON" -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/<language>`
   Run it in the background if more than ~30 files are missing (120 s pause per file). On HTTP 429 the script stops at once; do not re-run until well after the Retry-After time (wait at least an hour), and tell the user.
4. Report the summary line and every MISSING / FAILED entry to the user.

Python: `TAPPY_PYTHON` is the project's conda env `tappy`, set per machine in `.claude/settings.local.json`. Available packages are listed in `environment.yml` (repo root); add any new dependency there, pinned.

English definitions for translations (pair packs): `"$TAPPY_PYTHON" -I -X utf8 .claude/skills/fetch-audio/lookup_wiktionary.py data/language/<language> [extra words...]`. It looks up every pack item's text plus any extra words not yet in `cache/wiktionary_en.json`, 50 per request, same rules as above.

## Rules
- Do not attempt to download a file already locally cached.
- Be gentle with the providers: one request at a time, never lower `PAUSE_S` or `BACKOFF_S`, never retry a 429 in a loop, never run several copies at once.
- Wikimedia's User-Agent policy asks bots for contact details; a UA without them is throttled sooner. `USER_AGENT` carries a link to this page plus the contact address from the `TAPPY_CONTACT` environment variable, which the user sets themselves (never write the address into the repo). The script refuses to download while it is unset.
- If a file is missing (not just a temporary error) and not already cached locally, edit `INDEX.csv`.  Cautttion the user to consider checking in the changes carefully if there are multiple contributors, and another contributor may have the file.

## For provider server operators
This script belongs to TappyLanguage, an open-source (GPL-3.0) Android app for learning Greek, which credits every recording it uses.
- It downloads only CC-licensed or public-domain Greek pronunciation files listed in the project's `INDEX.csv` files.  Example: [[https://github.com/DeniseSkidmore/TappyLanguage/blob/main/data/language/greek/INDEX.csv]]
- One request at a time, 120 s apart. File URLs and Wiktionary definitions are looked up with batched API queries (50 titles per request) and cached. Files already downloaded and lookups already cached are never requested again.
- It stops at the first HTTP 429 and is not re-run for at least an hour.
- Contact: the address in the User-Agent, or an issue at [[https://github.com/DeniseSkidmore/TappyLanguage/issues]].
- FAILED (not 404/410) usually means rate limiting or network trouble: suggest re-running later rather than retrying in a loop.
- Only Wikimedia Commons file pages are supported for now; other sources need a `download_url` case in the script.
