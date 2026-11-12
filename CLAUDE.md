# Project CLAUDE directives

## Project Overview
Project is an Android application for learning (human) languages.  Initial language will be Greek, but will be designed to accept additional data files for additional languages in the future.  Gameplay will use spaced repetition to determine which words are "in play", speak a word aloud, then move words down the screen until the user taps the word that was said.  There will be a few modes.

## Current project phase
Establishing tools & processes.

## Requirements
When we get to requirements analysis phase, requirements are in the docs/requirements folder.

## Ground rules
Propose each change and wait for my approval before editing; never widen permissions on your own; When finished, list each item as verified, blocked (why), or not attempted.
Begin proposals with an action list tagged [Claude] / [You] / [Decide]; explanation follows.
Bundle requests with numbered actions.

## Open items log (Claude's running context)
`.claude/open-items.md` is Claude's own working file, gitignored and not for the user to maintain. It is exempt from "propose each change": edit it freely without asking.
- **Session start:** read it, summarize the open items in a short numbered list, and propose which to pick up (or ask if the user already has a task).
- **During work:** add any undecided question, deferred follow-up, unverified check, or pending [You] action as it comes up. Remove an item once it is resolved or explicitly dropped. Keep entries one line each: tag, date, item, and where it came from.
- Record final decisions in commit messages, not here; this file holds only what is still open.