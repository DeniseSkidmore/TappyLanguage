"""Look up English Wiktionary senses for a language's pack words, politely, with a durable cache.

Usage: python -I -X utf8 lookup_wiktionary.py <language-dir> [extra words...] [--dry-run]
  e.g. python -I -X utf8 .claude/skills/fetch-audio/lookup_wiktionary.py data/language/greek θεωρία

- Words: the `text` of every item in <language-dir>/packs/*.json, plus any extra words given.
- Cache: <language-dir>/cache/wiktionary_en.json, {word: ["[Part of speech] sense", ...]}.
  A word with no entry is cached as [] so it is not requested again. Cached words are never requested.
- Requests: en.wiktionary.org API, prop=revisions (page wikitext), up to 50 titles per request,
  PAUSE_S apart. The cache is saved after every request.
- HTTP 429 stops the run at once, with no retry. Re-run no sooner than an hour later.
- Requests go through tappylib.polite_http.request(): retries on 5xx, and waits out connection
  loss (up to OFFLINE_MAX_S) without using a retry. Needs the tappy conda env ($TAPPY_PYTHON).
- The User-Agent carries the contact address from TAPPY_CONTACT (see data/CLAUDE.md); the script
  refuses to send requests while it is unset.
- Senses are taken from the section for the language's heading (LANG_HEADINGS); templates are
  rendered roughly to plain text ("inflection of είμαι (3|s|pres)").
"""
import json
import re
import sys
import time
import urllib.parse
from pathlib import Path

from tappylib.polite_http import PAUSE_S, contact, request, user_agent

USER_AGENT = user_agent("TappyLanguage-lookup/0.1")
API_URL = "https://en.wiktionary.org/w/api.php"
BATCH = 50
LANG_HEADINGS = {"greek": "Greek"}
POS = {"Noun", "Verb", "Adjective", "Adverb", "Pronoun", "Preposition", "Conjunction", "Interjection",
       "Particle", "Numeral", "Determiner", "Article", "Phrase", "Proper noun", "Contraction", "Participle"}


def render_template(body: str) -> str:
    parts = [p.strip() for p in body.split("|")]
    name, args = parts[0].lower(), [p for p in parts[1:] if "=" not in p]
    if name == "w":
        return args[0] if args else ""
    if name in ("l", "m", "link", "mention"):  # {{l|lang|term|alt}}
        if len(args) > 2 and args[2]:
            return args[2]
        return args[1] if len(args) > 1 else ""
    if name in ("lb", "lbl", "label"):
        return "(" + ", ".join(a for a in args[1:] if a and a != "_") + ")"
    if name in ("q", "qualifier", "i", "gloss", "gl"):
        return "(" + ", ".join(args) + ")"
    if name.endswith(" of") or name in ("infl of", "alt form", "alt sp"):
        lemma = args[1] if len(args) > 1 else ""
        rest = "|".join(a for a in args[2:] if a)
        return f"{name} {lemma}" + (f" ({rest})" if rest else "")
    return ""


def plain(text: str) -> str:
    text = re.sub(r"<ref[^>]*/>|<ref.*?</ref>", "", text, flags=re.S)
    while True:
        new = re.sub(r"\{\{([^{}]*)\}\}", lambda m: render_template(m.group(1)), text)
        if new == text:
            break
        text = new
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<[^>]+>|'''|''", "", text)
    return re.sub(r"\s+", " ", text).strip()


def senses(wikitext: str, heading: str) -> list[str]:
    m = re.search(rf"^=={re.escape(heading)}==\s*$(.*?)(?=^==[^=]|\Z)", wikitext, flags=re.M | re.S)
    if not m:
        return []
    out, pos = [], None
    for line in m.group(1).splitlines():
        h = re.match(r"^(={3,6})\s*(.*?)\s*\1\s*$", line)
        if h:
            pos = h.group(2) if h.group(2) in POS else None
            continue
        s = re.match(r"^#(?![:*#])\s*(.*)", line)
        if pos and s:
            sense = plain(s.group(1))
            if sense:
                out.append(f"[{pos}] {sense}")
    return out


def pack_words(lang_dir: Path) -> list[str]:
    words = []
    for p in sorted((lang_dir / "packs").glob("*.json")):
        for item in json.loads(p.read_text(encoding="utf-8")).get("items", []):
            if item.get("text") and item["text"] not in words:
                words.append(item["text"])
    return words


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if not args:
        print(__doc__)
        return 2
    lang_dir = Path(args[0])
    heading = LANG_HEADINGS.get(lang_dir.name)
    if not heading:
        print(f"no Wiktionary heading known for '{lang_dir.name}'; add it to LANG_HEADINGS")
        return 2
    cache_path = lang_dir / "cache" / "wiktionary_en.json"
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
    words = pack_words(lang_dir) + [w for w in args[1:]]
    todo = list(dict.fromkeys(w for w in words if w not in cache))
    print(f"{len(words)} words, {len(cache)} cached, {len(todo)} to look up "
          f"({(len(todo) + BATCH - 1) // BATCH} requests)")
    if dry_run or not todo:
        for w in todo:
            print(f"  would look up {w}")
        return 0
    if not contact():
        print("TAPPY_CONTACT is not set: Wikimedia asks for a contact address. Set it, then re-run.")
        return 2

    for i in range(0, len(todo), BATCH):
        if i:
            time.sleep(PAUSE_S)
        batch = todo[i:i + BATCH]
        query = urllib.parse.urlencode({"action": "query", "format": "json", "formatversion": "2",
                                        "prop": "revisions", "rvprop": "content", "rvslots": "main",
                                        "titles": "|".join(batch)})
        print(f"lookup {len(batch)} words", flush=True)
        status, body = request(f"{API_URL}?{query}", USER_AGENT)
        if status != "ok":
            hint = " Re-run no sooner than an hour from now." if status.startswith("ratelimited") else ""
            print(f"STOPPED: {status}.{hint} The cache is kept.")
            return 1
        q = json.loads(body.decode("utf-8")).get("query", {})
        alias = {n["to"]: n["from"] for n in q.get("normalized", [])}
        for page in q.get("pages", []):
            word = alias.get(page["title"], page["title"])
            revs = page.get("revisions") or []
            text = revs[0]["slots"]["main"].get("content", "") if revs else ""
            cache[word] = senses(text, heading)
        for w in batch:  # anything the API didn't return: cache as empty so it isn't asked again
            cache.setdefault(w, [])
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        tmp = cache_path.with_suffix(".json.part")
        tmp.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
        tmp.replace(cache_path)

    empty = [w for w in todo if not cache.get(w)]
    print(f"done: {len(todo) - len(empty)} with senses, {len(empty)} without")
    for w in empty:
        print(f"  NO SENSES: {w}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
