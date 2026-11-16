---
name: fetch-audio
description: Download the third-party audio files listed in a language's INDEX.csv into data/language/<language>/<source>/, skipping files already present and reporting files the source no longer serves. Use when setting up a new machine to build the app, or after INDEX.csv gains rows.
---

# fetch-audio

Binary language data is gitignored; only `INDEX.csv` is committed (see CLAUDE.md, "Third-party data"). This skill restores the files from the index.

## Steps
1. Ask which language if not given (folder under `data/language/`, e.g. `greek`).
2. Optional preview: `python -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/<language> --dry-run`
3. Run: `python -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/<language>`
   Run it in the background if more than ~30 files are missing (3 s pause per file, long backoff on HTTP 429).
4. Report the summary line and every MISSING / FAILED entry to the user.

## Rules
- Be gentle with the providers: one request at a time, never lower `PAUSE_S` or `BACKOFF_S`, never run several copies at once.
- Do not edit `INDEX.csv` because a file is missing. A missing file is left out of the build (the app drops items whose audio asset is absent); tell the user so they can decide whether to replace the source or remove the row.
- FAILED (not 404/410) usually means rate limiting or network trouble: suggest re-running later rather than retrying in a loop.
- Only Wikimedia Commons file pages are supported for now; other sources need a `download_url` case in the script.
