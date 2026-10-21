r"""Read-only private-album feasibility probe. No third-party dependencies.

Usage: python probe_google_photos.py C:\path\cookies.txt [--account 0]
Never paste cookies into chat. This tool never prints or writes credentials.
Protocol and response layout adapted from xob0t/google_photos_web_client (MIT).
See GOOGLE_PHOTOS_CLIENT_LICENSE.txt. This is an unofficial web interface.
"""
import argparse
import datetime as dt
import http.cookiejar
import json
import re
import sys
import urllib.parse
import urllib.request


def at(value, *keys):
    try:
        for key in keys:
            value = value[key]
        return value
    except (IndexError, KeyError, TypeError):
        return None


def parse_rpc(text, rpc):
    for line in text.splitlines():
        if '"wrb.fr"' not in line:
            continue
        for row in json.loads(line):
            if at(row, 0) == "wrb.fr" and at(row, 1) == rpc:
                if not at(row, 2):
                    raise ValueError("Google returned no album data; session or protocol may have changed.")
                return json.loads(row[2])
    raise ValueError("Unrecognized response from Google; the web protocol may have changed.")


class Photos:
    def __init__(self, path, account):
        jar = http.cookiejar.MozillaCookieJar(path)
        jar.load(ignore_discard=True, ignore_expires=False)
        # Retain only cookies applicable to photos.google.com; never send unrelated cookies.
        for cookie in list(jar):
            domain = cookie.domain.lstrip(".").lower()
            if domain not in ("google.com", "photos.google.com", "photos.fife.usercontent.google.com"):
                jar.clear(cookie.domain, cookie.path, cookie.name)
        if not list(jar):
            raise ValueError("No unexpired Google Photos cookies found in this file.")
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
        self.opener.addheaders = [("User-Agent", "Mozilla/5.0" )]
        self.path = f"/u/{account}/" if account else "/"
        with self.opener.open("https://photos.google.com" + self.path, timeout=30) as response:
            html = response.read(20_000_000).decode("utf-8")
            if "accounts.google.com" in response.url:
                raise ValueError("Google requires sign-in again. Export a fresh session.")
        match = re.search(r"window\.WIZ_global_data\s*=\s*(\{.*?\})\s*;", html, re.S)
        if not match:
            raise ValueError("Cannot read Photos session metadata. Sign-in or the website format may have changed.")
        self.g = json.loads(match.group(1))
        for key in ("FdrFJe", "cfb2h", "SNlM0e", "Im6cmf"):
            if not self.g.get(key):
                raise ValueError("Incomplete Photos session; export cookies after opening your photo library.")

    def rpc(self, rpc, data):
        # Allowlist read operations. No upload, edit, share, or delete operations.
        if rpc not in ("Z5xsfc", "snAcKc"):
            raise ValueError("Unsupported operation")
        query = urllib.parse.urlencode({"rpcids": rpc, "source-path": self.path,
                                      "f.sid": self.g["FdrFJe"], "bl": self.g["cfb2h"], "rt": "c"})
        body = urllib.parse.urlencode({"f.req": json.dumps([[[rpc, json.dumps(data), None, "generic"]]]),
                                      "at": self.g["SNlM0e"]}).encode()
        endpoint = self.g["Im6cmf"]
        if not isinstance(endpoint, str) or not endpoint.startswith("/") or endpoint.startswith("//"):
            raise ValueError("Unexpected API endpoint")
        request = urllib.request.Request("https://photos.google.com" + endpoint + "/data/batchexecute?" + query,
                                         data=body, headers={"Content-Type": "application/x-www-form-urlencoded"})
        with self.opener.open(request, timeout=45) as response:
            return parse_rpc(response.read(25_000_000).decode("utf-8"), rpc)

    def albums(self):
        token, seen, albums = None, set(), []
        while True:
            page = self.rpc("Z5xsfc", [token, None, None, None, 1, None, None, 100, [2], 5])
            for row in at(page, 0) or []:
                key = at(row, 0)
                title = at(row, -1, "72930366", 1) or "Untitled album"
                count = at(row, -1, "72930366", 3)
                if key:
                    albums.append((key, title, count))
            token = at(page, 1)
            if not token:
                return albums
            if token in seen:
                raise ValueError("Album pagination repeated; stopping instead of looping.")
            seen.add(token)

    def scan(self, key):
        token, seen, media = None, set(), {}
        while True:
            page = self.rpc("snAcKc", [key, token, None, None])
            for row in at(page, 1) or []:
                if at(row, 0):
                    media[row[0]] = row
            print(f"\rRead {len(media):,} album items...", end="", flush=True)
            token = at(page, 2)
            if not token:
                print()
                return list(media.values())
            if token in seen:
                raise ValueError("Photo pagination repeated; stopping instead of looping.")
            seen.add(token)


def capture_date(row):
    millis = at(row, 2)
    if not isinstance(millis, (int, float)):
        return None
    try:
        offset = at(row, 4) or 0
        return dt.datetime.fromtimestamp((millis + offset) / 1000, dt.timezone.utc).date()
    except (ValueError, OverflowError, OSError):
        return None


def calendar_distance(a, b):
    # Anchor month/day to a leap year; wraps across New Year's Day.
    delta = abs((a.replace(year=2000) - b.replace(year=2000)).days)
    return min(delta, 366 - delta)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("cookies", help="Local Netscape cookies.txt file")
    parser.add_argument("--account", type=int, default=0)
    args = parser.parse_args()
    photos = Photos(args.cookies, args.account)
    albums = photos.albums()
    if not albums:
        raise ValueError("Signed-in session returned no albums; check the selected account.")
    for i, (_, title, count) in enumerate(albums, 1):
        print(f"{i}. {title} ({count if count is not None else '?'} items)")
    choice = int(input("Choose album number (Ctrl+C to stop): "))
    if not 1 <= choice <= len(albums):
        raise ValueError("Album number is out of range")
    rows = photos.scan(albums[choice - 1][0])
    dates = [date for row in rows if (date := capture_date(row)) is not None]
    today = dt.date.today()
    recent = sum(0 <= (today - date).days <= 90 for date in dates)
    memories = sum(date.year < today.year and calendar_distance(date, today) <= 14 for date in dates)
    print(f"Photo dates available: {len(dates):,}/{len(rows):,}")
    print(f"Taken in last 90 days: {recent:,}")
    print(f"Within 14 calendar days of today in past years: {memories:,}")
    print("Recent here means capture date, not date added to the album.")
    if rows and not dates:
        raise ValueError("Album read succeeded, but capture dates could not be parsed.")
    url = next((at(row, 1, 0) for row in rows if isinstance(at(row, 1, 0), str)), None)
    if url:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or not ((parsed.hostname or "").endswith(".googleusercontent.com") or parsed.hostname == "photos.fife.usercontent.google.com"):
            raise ValueError("Unexpected image host; image access was not tested.")
        # A small display-sized preview, discarded immediately; no photo files are created.
        with photos.opener.open(url + "=w320-h320", timeout=30) as response:
            data = response.read(2_000_000)
            if not response.headers.get("Content-Type", "").startswith("image/") or not data:
                raise ValueError("Preview did not return image data.")
        print("Image download: passed (preview discarded).")
    else:
        print("Image download: not tested (no image URLs found).")
    print("No changes made to Google Photos or the frame. No credentials saved by this tool.")
    print("Next test: repeat later after adding a photo to the album to verify fresh access.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nStopped.")
        sys.exit(1)
    except Exception as error:
        # Do not print raw HTTP bodies, URLs, cookie values, or session metadata.
        safe = str(error) if isinstance(error, ValueError) else type(error).__name__
        print("Access test stopped: " + safe, file=sys.stderr)
        sys.exit(1)
