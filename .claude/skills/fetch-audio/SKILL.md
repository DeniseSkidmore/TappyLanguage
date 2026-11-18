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
   Run it in the background if more than ~30 files are missing (5 s pause per file). On HTTP 429 the script stops at once; do not re-run until well after the Retry-After time (wait at least an hour), and tell the user.
4. Report the summary line and every MISSING / FAILED entry to the user.

## Rules
- Be gentle with the providers: one request at a time, never lower `PAUSE_S` or `BACKOFF_S`, never retry a 429 in a loop, never run several copies at once.
- Wikimedia's User-Agent policy asks bots for contact details; a UA without them is throttled sooner. `USER_AGENT` carries a link to this page plus the contact address from the `TAPPY_CONTACT` environment variable, which the user sets themselves (never write the address into the repo). The script refuses to download while it is unset.

## For Wikimedia operators
This script belongs to TappyLanguage, an open-source (GPL-3.0) Android app for learning Greek, which credits every recording it uses.
- It downloads only CC-licensed or public-domain Greek pronunciation files listed in the project's `INDEX.csv`: about 100 files in total.
- One request at a time, 5 s apart. Files already downloaded are never requested again.
- It stops at the first HTTP 429 and is not re-run for at least an hour.
- Contact: the address in the User-Agent, or an issue at https://github.com/DeniseSkidmore/TappyLanguage/issues.
- Do not edit `INDEX.csv` because a file is missing. A missing file is left out of the build (the app drops items whose audio asset is absent); tell the user so they can decide whether to replace the source or remove the row.
- FAILED (not 404/410) usually means rate limiting or network trouble: suggest re-running later rather than retrying in a loop.
- Only Wikimedia Commons file pages are supported for now; other sources need a `download_url` case in the script.
