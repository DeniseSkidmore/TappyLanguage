"""Download third-party audio listed in a language's INDEX.csv.

Usage: python -I -X utf8 fetch_audio.py <language-dir> [--dry-run]
  e.g. python -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/greek

- Reads <language-dir>/INDEX.csv (path,source_url,author,license,retrieved,start_ms,end_ms).
- Skips files already present. Resolves direct upload URLs with batched Commons API queries
  (50 titles per request, cached in <language-dir>/cache/commons_urls.json), then downloads
  one file at a time, PAUSE_S apart.
- Slice rows (start_ms/end_ms) share their source file; the whole file is downloaded once.
- A file the source no longer serves (HTTP 404/410) is reported as MISSING; other failures
  after retries are reported as FAILED. Neither stops the run. The app leaves out items whose
  audio is absent, so missing files simply drop out of the build.
- HTTP 429 (rate limited) stops the whole run at once, with no retries: re-run later; files
  already saved are skipped then, so nothing is downloaded twice.
- Connection loss (laptop undocked, wifi switch, sleep) is waited out, up to OFFLINE_MAX_S,
  without using a retry; then the run stops cleanly. See tappylib.polite_http.
- Supported sources: Wikimedia Commons file pages (https://commons.wikimedia.org/wiki/File:...).
- Needs the tappy conda env ($TAPPY_PYTHON), which provides tappylib.
"""
import csv
import json
import sys
import time
import urllib.parse
from pathlib import Path

from tappylib.polite_http import OFFLINE_MAX_S, PAUSE_S, contact, request, user_agent

USER_AGENT = user_agent("TappyLanguage-fetch/0.1")
API_URL = "https://commons.wikimedia.org/w/api.php"
BATCH = 50           # titles per API request (API limit for normal clients)


def commons_title(source_url: str) -> str:
    """'File:<name>' for a Commons file page URL; ValueError for other sources."""
    parsed = urllib.parse.urlparse(source_url)
    if parsed.netloc == "commons.wikimedia.org" and parsed.path.startswith("/wiki/File:"):
        return "File:" + urllib.parse.unquote(parsed.path[len("/wiki/File:"):]).replace("_", " ")
    raise ValueError(f"unsupported source: {source_url}")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def save_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".part")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(path)


def resolve_urls(titles: list[str], cache_path: Path) -> str | None:
    """Fill the cache {title: direct upload URL or None if absent} with batched imageinfo queries.

    Saves after every request. Returns None, or a request() status that stops the run.
    """
    cache = load_json(cache_path)
    todo = [t for t in titles if t not in cache]
    for i in range(0, len(todo), BATCH):
        if i:
            time.sleep(PAUSE_S)
        batch = todo[i:i + BATCH]
        query = urllib.parse.urlencode({"action": "query", "format": "json", "formatversion": "2",
                                        "prop": "imageinfo", "iiprop": "url", "titles": "|".join(batch)})
        print(f"resolve {len(batch)} URLs (API)", flush=True)
        status, body = request(f"{API_URL}?{query}", USER_AGENT)
        if status != "ok":
            return status
        q = json.loads(body.decode("utf-8")).get("query", {})
        alias = {n["to"]: n["from"] for n in q.get("normalized", [])}
        for page in q.get("pages", []):
            title = alias.get(page["title"], page["title"])
            info = page.get("imageinfo")
            cache[title] = info[0]["url"] if info and not page.get("missing") else None
        save_json(cache_path, cache)
    return None


def fetch(url: str, dest: Path) -> str:
    """Download url to dest. Returns a request() status."""
    status, data = request(url, USER_AGENT)
    if status == "ok":
        tmp = dest.with_suffix(dest.suffix + ".part")
        tmp.write_bytes(data)
        tmp.replace(dest)
    return status


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if len(args) != 1:
        print(__doc__)
        return 2
    if not contact() and not dry_run:
        print("TAPPY_CONTACT is not set: Wikimedia asks for a contact address. Set it, then re-run.")
        return 2
    lang_dir = Path(args[0])
    index = lang_dir / "INDEX.csv"
    with index.open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    seen, results = set(), {"present": [], "ok": [], "missing": [], "failed": []}
    todo = []  # (rel, title) of files not yet on disk
    for row in rows:
        rel = row["path"]
        if rel in seen:
            continue
        seen.add(rel)
        dest = lang_dir / rel
        if dest.exists() and dest.stat().st_size > 0:
            results["present"].append(rel)
            continue
        try:
            todo.append((rel, commons_title(row["source_url"])))
        except ValueError as e:
            results["failed"].append(f"{rel} ({e})")

    # Direct upload URLs: batched API lookups, cached, so each file costs one request to upload.wikimedia.org.
    cache_path = lang_dir / "cache" / "commons_urls.json"
    first = True
    if todo and not dry_run:
        uncached = [t for _, t in todo if t not in load_json(cache_path)]
        if uncached:
            status = resolve_urls(uncached, cache_path)
            first = False
            if status:
                print(f"STOPPED: URL lookup {status}. Re-run later; cached URLs are kept.")
                return 1
    urls = load_json(cache_path)

    for rel, title in todo:
        dest = lang_dir / rel
        url = urls.get(title)
        if dry_run:
            print(f"would fetch {rel} <- {url or title + ' (URL lookup pending)'}")
            continue
        if url is None:
            results["missing"].append(rel)
            continue
        if not first:
            time.sleep(PAUSE_S)
        first = False
        dest.parent.mkdir(parents=True, exist_ok=True)
        print(f"fetch {rel}", flush=True)
        status = fetch(url, dest)
        if status.startswith("ratelimited"):
            print(f"STOPPED: rate limited at {rel} ({status}). Re-run later; saved files are kept.")
            break
        if status == "offline":
            print(f"STOPPED: offline for over {OFFLINE_MAX_S // 60} min at {rel}. Re-run when online; "
                  "saved files are kept.")
            break
        if status == "ok":
            results["ok"].append(rel)
        elif status == "missing":
            results["missing"].append(rel)
        else:
            results["failed"].append(f"{rel} ({status[len('failed: '):]})")

    print()
    print(f"present {len(results['present'])}, downloaded {len(results['ok'])}, "
          f"missing {len(results['missing'])}, failed {len(results['failed'])}")
    for key in ("missing", "failed"):
        for item in results[key]:
            print(f"  {key.upper()}: {item}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
