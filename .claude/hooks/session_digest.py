#!/usr/bin/env python3
"""Summarize a Claude Code session transcript (JSONL) into a compact digest.

One script, two uses:
  * SessionEnd hook:  session_digest.py --hook --log .claude/metrics/sessions.jsonl
                      (reads the hook's JSON on stdin; appends one metrics line)
  * /retro command:   session_digest.py [--latest | TRANSCRIPT_PATH]
                      (prints a markdown digest small enough to grade cheaply)

The transcript format is internal to Claude Code and may change; every field
is read defensively so a format change degrades the digest instead of failing.
"""
import argparse, collections, datetime, glob, json, os, re, sys

PROMPT_CHARS = 300
GAP_CAP_EDITED_MIN = 120  # idle gap counted as active up to this, if files were edited around it
GAP_CAP_PLAIN_MIN = 30    # otherwise
EDIT_TOOLS = ("Edit", "Write", "MultiEdit", "NotebookEdit")
PASTED = re.compile(r"<pasted_content[^>]*>(.*?)</pasted_content[^>]*>", re.S)


def latest_transcript():
    files = glob.glob(os.path.expanduser("~/.claude/projects/*/*.jsonl"))
    return max(files, key=os.path.getmtime) if files else None


def text_of(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(b.get("text", "") for b in content
                        if isinstance(b, dict) and b.get("type") == "text")
    return ""


def ts(s):
    return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))


def active_minutes(events):
    """Sum of gaps between transcript lines, each capped.

    A gap is capped at GAP_CAP_EDITED_MIN when files were edited around it:
    Claude edited files earlier in the current turn (user likely reviewing them),
    or the user edited a file Claude had read (edited_text_file attachment
    within a minute after the gap). Otherwise GAP_CAP_PLAIN_MIN.
    """
    try:
        ev = sorted((ts(t), ce, ue, up) for t, ce, ue, up in events)
    except ValueError:
        return None
    if len(ev) < 2:
        return None
    user_edit_times = [t for t, _, ue, _ in ev if ue]
    total, claude_edited_this_turn = 0.0, False
    for (t0, ce, _, up), (t1, *_rest) in zip(ev, ev[1:]):
        if up:
            claude_edited_this_turn = False
        claude_edited_this_turn = claude_edited_this_turn or ce
        gap = (t1 - t0).total_seconds() / 60
        user_edited = any(0 <= (u - t1).total_seconds() <= 60 for u in user_edit_times)
        cap = GAP_CAP_EDITED_MIN if (claude_edited_this_turn or user_edited) else GAP_CAP_PLAIN_MIN
        total += min(gap, cap)
    return round(total, 1)


def parse(path):
    usage_by_msg = {}
    tools = collections.Counter()
    errors = collections.Counter()
    tool_names_by_id = {}
    files_edited, prompts, models, stamps = set(), [], set(), []
    events = []  # (timestamp, claude_edit, user_edit, user_prompt) per transcript line
    session_id = None

    with open(path, encoding="utf-8") as fh:
        for line in fh:
            try:
                d = json.loads(line)
            except ValueError:
                continue
            session_id = session_id or d.get("sessionId")
            if d.get("timestamp"):
                stamps.append(d["timestamp"])
            msg = d.get("message") if isinstance(d.get("message"), dict) else {}
            claude_edit = user_edit = user_prompt = False
            att = d.get("attachment")
            if isinstance(att, dict) and att.get("type") == "edited_text_file":
                user_edit = True  # a file Claude had read was changed outside Claude

            if d.get("type") == "assistant":
                if msg.get("model"):
                    models.add(msg["model"])
                if msg.get("usage"):  # one API message can span several lines
                    usage_by_msg[msg.get("id") or d.get("uuid")] = msg["usage"]
                for b in msg.get("content") or []:
                    if isinstance(b, dict) and b.get("type") == "tool_use":
                        name = b.get("name", "?")
                        tools[name] += 1
                        tool_names_by_id[b.get("id")] = name
                        fp = (b.get("input") or {}).get("file_path")
                        if fp and name in EDIT_TOOLS:
                            files_edited.add(fp)
                            claude_edit = True

            elif d.get("type") == "user" and not d.get("isMeta") and not d.get("isSidechain"):
                content = msg.get("content")
                if isinstance(content, list):
                    for b in content:
                        if isinstance(b, dict) and b.get("type") == "tool_result" and b.get("is_error"):
                            errors[tool_names_by_id.get(b.get("tool_use_id"), "?")] += 1
                if "toolUseResult" not in d:
                    # unwrap pasted text (the user's own prompt), then skip injected blocks
                    t = PASTED.sub(r"\1", text_of(content)).strip()
                    if t and not t.startswith("<"):
                        prompts.append(t)
                        user_prompt = True
            if d.get("timestamp"):
                events.append((d["timestamp"], claude_edit, user_edit, user_prompt))

    tok = collections.Counter()
    for u in usage_by_msg.values():
        for k in ("input_tokens", "output_tokens",
                  "cache_creation_input_tokens", "cache_read_input_tokens"):
            tok[k] += u.get(k) or 0

    minutes = None
    if len(stamps) >= 2:
        minutes = round((ts(max(stamps)) - ts(min(stamps))).total_seconds() / 60, 1)

    return {
        "session_id": session_id, "transcript": path,
        "ended": max(stamps) if stamps else None, "wall_minutes": minutes,
        "active_minutes": active_minutes(events),
        "api_turns": len(usage_by_msg), "user_prompts": len(prompts),
        "tokens": dict(tok), "models": sorted(models),
        "tool_calls": dict(tools), "tool_errors": dict(errors),
        "files_edited": sorted(files_edited), "_prompts": prompts,
    }


def markdown(m):
    t = m["tokens"]
    out = [f"# Session digest {m['session_id']}",
           f"- Wall time: {m['wall_minutes']} min; active: {m['active_minutes']} min "
           f"(gaps capped {GAP_CAP_EDITED_MIN} min after edits, else {GAP_CAP_PLAIN_MIN}); user prompts: {m['user_prompts']}; "
           f"API turns: {m['api_turns']}; models: {', '.join(m['models'])}",
           f"- Tokens: in {t.get('input_tokens', 0):,} | out {t.get('output_tokens', 0):,} | "
           f"cache write {t.get('cache_creation_input_tokens', 0):,} | "
           f"cache read {t.get('cache_read_input_tokens', 0):,}",
           f"- Tool calls: {m['tool_calls']}",
           f"- Tool errors: {m['tool_errors'] or 'none'}",
           f"- Files edited: {m['files_edited'] or 'none'}",
           "", "## User prompts (truncated)"]
    for i, p in enumerate(m["_prompts"], 1):
        p = " ".join(p.split())
        out.append(f"{i}. {p[:PROMPT_CHARS]}{'…' if len(p) > PROMPT_CHARS else ''}")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("transcript", nargs="?")
    ap.add_argument("--latest", action="store_true")
    ap.add_argument("--hook", action="store_true", help="read SessionEnd JSON from stdin")
    ap.add_argument("--log", help="append a metrics line to this JSONL file")
    a = ap.parse_args()

    path = a.transcript
    if a.hook:
        path = json.load(sys.stdin).get("transcript_path")
    elif a.latest or not path:
        path = latest_transcript()
    if not path or not os.path.exists(path):
        sys.exit(f"transcript not found: {path}")

    m = parse(path)
    if a.log:
        os.makedirs(os.path.dirname(os.path.abspath(a.log)), exist_ok=True)
        with open(a.log, "a", encoding="utf-8") as fh:
            fh.write(json.dumps({k: v for k, v in m.items() if not k.startswith("_")}) + "\n")
    if not a.hook:
        sys.stdout.reconfigure(encoding="utf-8")  # Windows defaults to cp1252
        print(markdown(m))


if __name__ == "__main__":
    main()
