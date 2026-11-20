"""Polite HTTP GET for Wikimedia and other non-profit providers (rules: data/CLAUDE.md).

- One request at a time; callers sleep PAUSE_S between requests.
- HTTP 429 is never retried: request() returns 'ratelimited: ...' and the caller stops the run.
- 5xx and network errors with the host reachable: RETRIES retries, BACKOFF_S doubling
  (or longer if the server sends Retry-After).
- Connection loss (laptop undocked, wifi switch, sleep) is not a failed request: if the host is
  unreachable, wait (checking every OFFLINE_POLL_S, no HTTP) and repeat the same request without
  using a retry. After OFFLINE_MAX_S offline, return 'offline' so the caller stops cleanly.
- User-Agent per Wikimedia policy: tool name, link to the skill page, contact address from the
  TAPPY_CONTACT environment variable (set by the user, never committed).
"""
import os
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

TOOL_URL = "https://github.com/DeniseSkidmore/TappyLanguage/blob/main/.claude/skills/fetch-audio/SKILL.md"
PAUSE_S = 120             # between requests (Wikimedia sent Retry-After: 120)
RETRIES = 2               # on 5xx / network errors with the host reachable
BACKOFF_S = 120           # first backoff; doubles each retry
OFFLINE_POLL_S = 60       # while offline, check reachability this often
OFFLINE_MAX_S = 600       # stop cleanly after this long offline


def contact() -> str:
    return os.environ.get("TAPPY_CONTACT", "").strip()


def user_agent(tool_name: str) -> str:
    """e.g. user_agent('TappyLanguage-fetch/0.1'). Contact is omitted if TAPPY_CONTACT is unset;
    callers that send requests should refuse to run in that case."""
    c = contact()
    return f"{tool_name} ({TOOL_URL}" + (f"; {c}" if c else "") + ") python-urllib"


def reachable(host: str) -> bool:
    """DNS lookup plus a TCP connect (no HTTP request). False when the network is down."""
    try:
        with socket.create_connection((host, 443), timeout=10):
            return True
    except OSError:
        return False


def wait_for_network(host: str) -> bool:
    """Return True once host is reachable (at once if it already is); False after OFFLINE_MAX_S.

    Wall-clock time; after the laptop wakes from sleep there is always one more check before giving up.
    """
    if reachable(host):
        return True
    start = time.time()
    print(f"    offline (cannot reach {host}); checking every {OFFLINE_POLL_S} s", flush=True)
    while True:
        time.sleep(OFFLINE_POLL_S)
        if reachable(host):
            print(f"    network back after {int(time.time() - start)} s", flush=True)
            return True
        if time.time() - start >= OFFLINE_MAX_S:
            return False


def request(url: str, agent: str) -> tuple[str, bytes | None]:
    """GET url. Returns ('ok', body), ('missing', None) for 404/410, ('ratelimited: ...', None),
    ('offline', None) or ('failed: <reason>', None)."""
    host = urllib.parse.urlparse(url).hostname
    wait, attempt = BACKOFF_S, 0
    while True:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": agent})
            with urllib.request.urlopen(req, timeout=60) as resp:
                return "ok", resp.read()
        except urllib.error.HTTPError as e:
            if e.code in (404, 410):
                return "missing", None
            if e.code == 429:
                return f"ratelimited: Retry-After {e.headers.get('Retry-After')} s", None
            if e.code < 500:
                return f"failed: HTTP {e.code}", None
            reason = f"HTTP {e.code}"
            retry_after = e.headers.get("Retry-After")
            if retry_after and retry_after.isdigit():
                wait = max(wait, int(retry_after))
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            reason = str(e)
            if not reachable(host):
                if not wait_for_network(host):
                    return "offline", None
                continue  # same request again; offline time is not a retry
        if attempt >= RETRIES:
            return f"failed: {reason}", None
        print(f"    retry in {wait} s ({reason})", flush=True)
        time.sleep(wait)
        wait *= 2
        attempt += 1
