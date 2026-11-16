"""Download third-party audio listed in a language's INDEX.csv.

Usage: python -I -X utf8 fetch_audio.py <language-dir> [--dry-run]
  e.g. python -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/greek

- Reads <language-dir>/INDEX.csv (path,source_url,author,license,retrieved,start_ms,end_ms).
- Skips files already present. Downloads the rest, one at a time, with a pause between requests.
- Slice rows (start_ms/end_ms) share their source file; the whole file is downloaded once.
- A file the source no longer serves (HTTP 404/410) is reported as MISSING; other failures
  after retries are reported as FAILED. Neither stops the run. The app leaves out items whose
  audio is absent, so missing files simply drop out of the build.
- Supported sources: Wikimedia Commons file pages (https://commons.wikimedia.org/wiki/File:...).
"""
import csv
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = "audio-fetch-script/0.1 (python-urllib; low-rate batch download of CC-licensed files)"
PAUSE_S = 3          # between requests
RETRIES = 4          # on 429 / 5xx / network errors
BACKOFF_S = 30       # first backoff; doubles each retry


def download_url(source_url: str) -> str:
    """Turn a source page URL into a direct file URL."""
    parsed = urllib.parse.urlparse(source_url)
    if parsed.netloc == "commons.wikimedia.org" and parsed.path.startswith("/wiki/File:"):
        name = urllib.parse.unquote(parsed.path[len("/wiki/File:"):])
        return "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(name)
    raise ValueError(f"unsupported source: {source_url}")


def fetch(url: str, dest: Path) -> str:
    """Return 'ok', 'missing' or 'failed: <reason>'."""
    wait = BACKOFF_S
    for attempt in range(RETRIES + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
            tmp = dest.with_suffix(dest.suffix + ".part")
            tmp.write_bytes(data)
            tmp.replace(dest)
            return "ok"
        except urllib.error.HTTPError as e:
            if e.code in (404, 410):
                return "missing"
            if e.code != 429 and e.code < 500:
                return f"failed: HTTP {e.code}"
            retry_after = e.headers.get("Retry-After")
            reason = f"HTTP {e.code}"
            if retry_after and retry_after.isdigit():
                wait = max(wait, int(retry_after))
        except (urllib.error.URLError, TimeoutError) as e:
            reason = str(e)
        if attempt < RETRIES:
            print(f"    retry in {wait} s ({reason})")
            time.sleep(wait)
            wait *= 2
    return f"failed: {reason}"


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if len(args) != 1:
        print(__doc__)
        return 2
    lang_dir = Path(args[0])
    index = lang_dir / "INDEX.csv"
    with index.open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    seen, results = set(), {"present": [], "ok": [], "missing": [], "failed": []}
    first = True
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
            url = download_url(row["source_url"])
        except ValueError as e:
            results["failed"].append(f"{rel} ({e})")
            continue
        if dry_run:
            print(f"would fetch {rel} <- {url}")
            continue
        if not first:
            time.sleep(PAUSE_S)
        first = False
        dest.parent.mkdir(parents=True, exist_ok=True)
        print(f"fetch {rel}")
        status = fetch(url, dest)
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
