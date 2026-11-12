---
description: Read-only audit of this project's Claude Code setup (settings, hooks, commands, agents)
allowed-tools: Read, Glob, Grep, Bash(git status*), Bash(git diff *), Bash(git log *), Bash(bash .claude/hooks/py .claude/hooks/session_digest.py *)
---
Read-only audit. Do not edit any file.

1. Read CLAUDE.md, CLAUDE.local.md, .gitignore, and everything under .claude/
   (skip metrics/ contents beyond a line count).
2. Check that local/secret files are gitignored (`git check-ignore -v`) and
   that `git status` is clean or explain what is not.
3. Test hooks and commands with no-op inputs only:
   - session_digest.py with no args, a bad path, and `--hook` fed a sample
     transcript_path, logging to a temp file outside the repo (never to
     .claude/metrics/).
4. Review permission rules for gaps: deny rules that Bash/PowerShell can bypass,
   force-push or history-rewrite variants, credential paths, and anything
   allowed more broadly than needed.

Report:
- A table of findings: item | severity (High/Med/Low/Info) | proposed change.
- Then each check listed as verified, blocked (why), or not attempted.
Keep the whole report under 40 lines. $ARGUMENTS
