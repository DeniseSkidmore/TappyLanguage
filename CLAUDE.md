# Project CLAUDE directives

## Project Overview
Project is an Android application for learning (human) languages.  Initial language will be Greek, for English speaking learners, but will be designed to accept additional language packs and language pair files for additional languages in the future.  Gameplay will use spaced repetition to determine which words are "in play", speak a word aloud, then move words down the screen until the user taps the word that was said.  

## Current project phase
Protyping

## Requirements
Requirements are in the docs/requirements folder.

## Third-party data
- Never use a source whose terms restrict rehosting or redistribution (applies to dataset research too).
- All other data rules (layout, INDEX.csv, caching, polite downloading): `data/CLAUDE.md`.

## Ground rules
Propose each change and wait for my approval before editing; never widen permissions on your own; When finished, list each item as verified, blocked (why), or not attempted.
Bundle requests with numbered actions.
Never edit files with sed or shell, use edit.  (Shell commands require approval, some files are allowed edit.)
If a shell command changes the active directory, return to this repository's root afterwards or execute in a subshell.
Project Python runs in `$TAPPY_PYTHON` (conda env `tappy`); packages are pinned in `environment.yml`; add new dependencies there.

## Conversation file (short term context)
`docs/conversation.md` is a shared, gitignored working file for threaded discussion. Claude may edit it without asking.
- Threads are numbered for cross reference.  If a thread splits use . notation at each split, such as 12.4.2
- Begin proposals with an action list; explanation follows.
- Claude writes answers, proposals and questions in numbered threads, each with an action list ([Claude] / [You] / [Decide]).
- The user answers and approves inline, then says in chat "read conversation" or a simple greeting. Claude then reads it and acts on what was approved.
- Claude deletes threads once completed or dropped, and updates ongoing ones. Lasting outcomes go into requirements, or CLAUDE.md, user may request non-current items go to `docs/open-items.md`.
- Chat stays short: Claude list the action item numbers completed, edited, and added.
- User will denote new action items and approvals with `> ` which should be acted on when read.
- When a thread gets excessivly long, create a seperate file for that thread eg: conversation-37.md so a fresh context can load just the relevant thread.  Notify user in chat when such new files are spawned.

## Open items log (long term context)
`docs/open-items.md` is a working file, gitignored and for Claude to maintain.
- **First reply of every session (mandatory, even if the user asked something else):** if there are no active items in docs/conversation.md, before anything else, summarize the open items in a short numbered list and propose which to pick up, then handle the user's message. A SessionStart hook injects the file's contents into context; if they are not there, Read the file.
- **During work:** add any undecided question, deferred follow-up, or unverified check. Remove an item once it is resolved or explicitly dropped. Keep entries one line each: tag, date, item, and where it came from.
- this file holds only what is still open, not final decisions.