---
description: Grade this session's efficiency (developer time + tokens)
argument-hint: "[transcript path; blank = current session]"
allowed-tools: Bash(bash .claude/hooks/py .claude/hooks/session_digest.py *)
---
Session digest:

!`bash .claude/hooks/py .claude/hooks/session_digest.py $ARGUMENTS`

Pass the digest above, verbatim and without commentary, to the session-grader
subagent. Show me its reply unchanged. Then ask whether to append the grade, with
today's date and the session id, to .claude/metrics/retros.md.
