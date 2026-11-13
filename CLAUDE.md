# Project CLAUDE directives

## Project Overview
Project is an Android application for learning (human) languages.  Initial language will be Greek, but will be designed to accept additional data files for additional languages in the future.  Gameplay will use spaced repetition to determine which words are "in play", speak a word aloud, then move words down the screen until the user taps the word that was said.  There will be a few modes.

## Current project phase
Requirements analysis.

## Requirements
When we get to requirements analysis phase, requirements are in the docs/requirements folder.

## Third-party data
- Language data: `data/language/<language>/<source>/` (e.g. `data/language/greek/lingualibre/`). GUI image data: `data/graphics/<source>/`.
- Each of `data/language/<language>/` and `data/graphics/` keeps an `INDEX.csv`, one row per file: `path,source_url,author,license,retrieved`. Rows for audio slices also carry `start_ms,end_ms`.
- Never use a source whose terms restrict rehosting or redistribution.
- Binary data is not committed during this phase (gitignored); only `INDEX.csv` files are. Hosting for built language packages is decided later.
- Releases bundle all of a language's data into a single language file, with attributions generated from its `INDEX.csv`.

## Ground rules
Propose each change and wait for my approval before editing; never widen permissions on your own; When finished, list each item as verified, blocked (why), or not attempted.
Begin proposals with an action list tagged [Claude] / [You] / [Decide]; explanation follows.
Bundle requests with numbered actions.

## Open items log (Claude's running context)
`docs/open-items.md` is Claude's own working file, gitignored and not for the user to maintain. It is exempt from "propose each change": edit it freely without asking.
- **First reply of every session (mandatory, even if the user asked something else):** before anything else, summarize the open items in a short numbered list and propose which to pick up, then handle the user's message. A SessionStart hook injects the file's contents into context; if they are not there, Read the file.
- **During work:** add any undecided question, deferred follow-up, unverified check, or pending [You] action as it comes up. Remove an item once it is resolved or explicitly dropped. Keep entries one line each: tag, date, item, and where it came from.
- Record final decisions in commit messages, not here; this file holds only what is still open.

## Conversation file (shared)
`docs/conversation.md` is a shared, gitignored working file for threaded discussion. Claude may edit it without asking.
- Claude writes proposals and questions there as numbered threads, each with an action list ([Claude] / [You] / [Decide]).
- The user answers and approves inline, then says in chat to review the conversation file. Claude then reads it and acts on what was approved.
- Claude deletes threads once completed or dropped, and updates ongoing ones. Lasting outcomes go into requirements, CLAUDE.md, or commit messages; undecided leftovers go to `docs/open-items.md`.
- Chat stays short: Claude says what changed in the file and what needs the user's attention.