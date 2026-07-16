from __future__ import annotations

import hashlib
import os
import re
import shutil
import sqlite3
import threading
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Callable, Iterable
from urllib.parse import unquote, urljoin, urlsplit, urlunsplit
from urllib.error import HTTPError
from urllib.request import Request, urlopen


DEFAULT_ARCHIVE_ROOT = "https://www.3gpp.org/ftp/Specs/Archive/"
DOWNLOAD_SUFFIXES = {
    ".zip",
    ".doc",
    ".docx",
    ".pdf",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
    ".json",
    ".html",
    ".htm",
}
USER_AGENT = "EviMind-3GPP-KB/1.0 (offline research mirror; resumable and rate-limited)"


def decode_version(code: str) -> tuple[int, int, int]:
    if not re.fullmatch(r"[0-9a-z][0-9][0-9]", code or ""):
        raise ValueError(f"Invalid 3GPP version code: {code!r}")
    major_char = code[0]
    major = int(major_char) if major_char.isdigit() else 10 + ord(major_char) - ord("a")
    return major, int(code[1]), int(code[2])


class _LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.hrefs: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "a":
            return
        for key, value in attrs:
            if key.lower() == "href" and value:
                self.hrefs.append(value)


def _canonical_directory_url(url: str) -> str:
    parsed = urlsplit(url)
    path = parsed.path if parsed.path.endswith("/") else parsed.path + "/"
    return urlunsplit((parsed.scheme.lower(), parsed.netloc.lower(), path, "", ""))


def _confined_url(current_url: str, href: str, root_url: str) -> str | None:
    if not href or href.startswith(("#", "?")):
        return None
    candidate = urlsplit(urljoin(current_url, href))
    root = urlsplit(_canonical_directory_url(root_url))
    if candidate.scheme.lower() != root.scheme or candidate.netloc.lower() != root.netloc:
        return None
    candidate_path = unquote(candidate.path)
    root_path = unquote(root.path)
    if not candidate_path.lower().startswith(root_path.lower()):
        return None
    if "/../" in candidate_path or candidate_path.endswith("/.."):
        return None
    return urlunsplit((root.scheme, root.netloc, candidate.path, "", ""))


def parse_directory(html: str, current_url: str, root_url: str) -> tuple[list[str], list[str]]:
    parser = _LinkParser()
    parser.feed(html)
    directories: set[str] = set()
    files: set[str] = set()
    current = _canonical_directory_url(current_url)
    for href in parser.hrefs:
        candidate = _confined_url(current, href, root_url)
        if not candidate or candidate == current:
            continue
        path = urlsplit(candidate).path
        suffix = Path(path).suffix.lower()
        if suffix in DOWNLOAD_SUFFIXES:
            files.add(candidate)
        else:
            directories.add(_canonical_directory_url(candidate))
    return sorted(directories), sorted(files)


@dataclass(frozen=True)
class ArchiveRecord:
    url: str
    relative_path: str
    series: str | None
    spec: str | None
    version_code: str | None
    version: tuple[int, int, int] | None
    size: int | None = None
    modified: str | None = None

    @classmethod
    def from_url(
        cls,
        url: str,
        root_url: str = DEFAULT_ARCHIVE_ROOT,
        size: int | None = None,
        modified: str | None = None,
    ) -> "ArchiveRecord":
        root_path = unquote(urlsplit(_canonical_directory_url(root_url)).path)
        path = unquote(urlsplit(url).path)
        if not path.lower().startswith(root_path.lower()):
            raise ValueError(f"Archive URL is outside configured root: {url}")
        relative_path = path[len(root_path) :].lstrip("/")
        parts = relative_path.split("/")
        series = None
        spec = None
        for index, part in enumerate(parts[:-1]):
            match = re.fullmatch(r"(\d{2})_series", part, re.IGNORECASE)
            if match:
                series = match.group(1)
                if index + 1 < len(parts) - 1 and re.fullmatch(r"\d{2}\.[0-9A-Za-z.-]+", parts[index + 1]):
                    spec = parts[index + 1]
                break
        version_match = re.search(r"-([0-9a-z][0-9][0-9])(?:\.[^.]+)?$", parts[-1], re.IGNORECASE)
        version_code = version_match.group(1).lower() if version_match else None
        version = decode_version(version_code) if version_code else None
        return cls(url, relative_path, series, spec, version_code, version, size, modified)


class ArchiveCrawler:
    def __init__(
        self,
        root_url: str = DEFAULT_ARCHIVE_ROOT,
        fetch_text: Callable[[str], str] | None = None,
        series: Iterable[str] | None = None,
        timeout: int = 30,
        request_interval: float = 0.25,
    ) -> None:
        self.root_url = _canonical_directory_url(root_url)
        self.fetch_text = fetch_text or self._fetch_text
        self.series = {item.zfill(2) for item in series or []}
        self.timeout = timeout
        self.request_interval = max(0.0, request_interval)
        self._last_request_at = 0.0

    def _fetch_text(self, url: str) -> str:
        last_error: Exception | None = None
        for attempt in range(4):
            try:
                wait = self.request_interval - (time.monotonic() - self._last_request_at)
                if wait > 0:
                    time.sleep(wait)
                request = Request(url, headers={"User-Agent": USER_AGENT})
                with urlopen(request, timeout=self.timeout) as response:
                    self._last_request_at = time.monotonic()
                    return response.read().decode("utf-8", errors="replace")
            except Exception as error:
                last_error = error
                if attempt < 3:
                    delay = 60 if isinstance(error, HTTPError) and error.code in (403, 429) else min(2**attempt, 8)
                    time.sleep(delay)
        assert last_error is not None
        raise last_error

    def _directory_selected(self, url: str) -> bool:
        if not self.series:
            return True
        relative = unquote(urlsplit(url).path)[len(unquote(urlsplit(self.root_url).path)) :]
        first = relative.strip("/").split("/", 1)[0]
        match = re.fullmatch(r"(\d{2})_series", first, re.IGNORECASE)
        return not first or (match is not None and match.group(1) in self.series)

    def discover(
        self,
        max_records: int | None = None,
        on_records: Callable[[list[ArchiveRecord]], None] | None = None,
        should_crawl: Callable[[str], bool] | None = None,
        on_directory_crawled: Callable[[str], None] | None = None,
    ) -> list[ArchiveRecord]:
        queue = [self.root_url]
        seen: set[str] = set()
        files: dict[str, ArchiveRecord] = {}
        while queue:
            current = queue.pop(0)
            if current in seen or not self._directory_selected(current):
                continue
            seen.add(current)
            if should_crawl and not should_crawl(current):
                continue
            html = self.fetch_text(current)
            directories, links = parse_directory(html, current, self.root_url)
            queue.extend(item for item in directories if item not in seen and self._directory_selected(item))
            page_records: list[ArchiveRecord] = []
            for link in links:
                record = ArchiveRecord.from_url(link, self.root_url)
                if not self.series or record.series in self.series:
                    files[record.url] = record
                    page_records.append(record)
                    if max_records is not None and len(files) >= max_records:
                        if on_records and page_records:
                            on_records(page_records)
                        if on_directory_crawled:
                            on_directory_crawled(current)
                        return sorted(files.values(), key=lambda item: item.relative_path.lower())[:max_records]
            if on_records and page_records:
                on_records(page_records)
            if on_directory_crawled:
                on_directory_crawled(current)
        return sorted(files.values(), key=lambda item: item.relative_path.lower())


class Manifest:
    def __init__(self, root: Path | str) -> None:
        self.root = Path(root).resolve()
        self.raw_dir = self.root / "raw"
        self.root.mkdir(parents=True, exist_ok=True)
        self.raw_dir.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.root / "3gpp.db", check_same_thread=False)
        self.connection.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        self._create_schema()

    def _create_schema(self) -> None:
        with self.connection:
            self.connection.executescript(
                """
                PRAGMA journal_mode=WAL;
                PRAGMA foreign_keys=ON;
                CREATE TABLE IF NOT EXISTS archive (
                    id INTEGER PRIMARY KEY,
                    url TEXT NOT NULL UNIQUE,
                    relative_path TEXT NOT NULL,
                    series TEXT,
                    spec TEXT,
                    version_code TEXT,
                    version_major INTEGER,
                    version_minor INTEGER,
                    version_patch INTEGER,
                    remote_size INTEGER,
                    remote_modified TEXT,
                    local_size INTEGER,
                    sha256 TEXT,
                    status TEXT NOT NULL DEFAULT 'DISCOVERED',
                    error TEXT,
                    is_latest INTEGER NOT NULL DEFAULT 0,
                    downloaded_at TEXT,
                    indexed_sha256 TEXT,
                    indexed_at TEXT
                );
                CREATE INDEX IF NOT EXISTS idx_archive_status ON archive(status);
                CREATE INDEX IF NOT EXISTS idx_archive_spec_version
                    ON archive(spec, version_major, version_minor, version_patch);
                CREATE TABLE IF NOT EXISTS crawl_directory (
                    url TEXT PRIMARY KEY,
                    crawled_at TEXT NOT NULL
                );
                """
            )

    def close(self) -> None:
        self.connection.close()

    def upsert_archives(self, records: Iterable[ArchiveRecord], refresh_latest: bool = True) -> int:
        rows = list(records)
        with self._lock, self.connection:
            for record in rows:
                version = record.version or (None, None, None)
                self.connection.execute(
                    """
                    INSERT INTO archive(
                        url, relative_path, series, spec, version_code,
                        version_major, version_minor, version_patch, remote_size, remote_modified
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(url) DO UPDATE SET
                        relative_path=excluded.relative_path,
                        series=excluded.series,
                        spec=excluded.spec,
                        version_code=excluded.version_code,
                        version_major=excluded.version_major,
                        version_minor=excluded.version_minor,
                        version_patch=excluded.version_patch,
                        remote_size=COALESCE(excluded.remote_size, archive.remote_size),
                        remote_modified=COALESCE(excluded.remote_modified, archive.remote_modified)
                    """,
                    (
                        record.url,
                        record.relative_path,
                        record.series,
                        record.spec,
                        record.version_code,
                        *version,
                        record.size,
                        record.modified,
                    ),
                )
            if refresh_latest:
                self._refresh_latest()
        return len(rows)

    def refresh_latest(self) -> None:
        with self._lock, self.connection:
            self._refresh_latest()

    def is_directory_crawled(self, url: str) -> bool:
        with self._lock:
            return (
                self.connection.execute(
                    "SELECT 1 FROM crawl_directory WHERE url=?", (url,)
                ).fetchone()
                is not None
            )

    def mark_directory_crawled(self, url: str) -> None:
        with self._lock, self.connection:
            self.connection.execute(
                "INSERT OR REPLACE INTO crawl_directory(url, crawled_at) VALUES (?, datetime('now'))",
                (url,),
            )

    def _refresh_latest(self) -> None:
        self.connection.execute("UPDATE archive SET is_latest=0")
        self.connection.execute(
            """
            UPDATE archive AS candidate SET is_latest=1
            WHERE candidate.spec IS NOT NULL
              AND candidate.id = (
                SELECT newest.id FROM archive AS newest
                WHERE newest.spec = candidate.spec
                ORDER BY COALESCE(newest.version_major, -1) DESC,
                         COALESCE(newest.version_minor, -1) DESC,
                         COALESCE(newest.version_patch, -1) DESC,
                         newest.relative_path DESC
                LIMIT 1
              )
            """
        )
        self.connection.execute("UPDATE archive SET is_latest=1 WHERE spec IS NULL")

    def raw_path(self, record: ArchiveRecord | sqlite3.Row) -> Path:
        relative = record.relative_path if isinstance(record, ArchiveRecord) else record["relative_path"]
        candidate = (self.raw_dir / Path(relative)).resolve()
        if self.raw_dir != candidate and self.raw_dir not in candidate.parents:
            raise ValueError(f"Unsafe mirror path: {relative}")
        return candidate

    def pending_archives(self, max_files: int | None = None) -> list[sqlite3.Row]:
        with self._lock:
            rows = list(self.connection.execute("SELECT * FROM archive ORDER BY relative_path"))
        pending = [
            row
            for row in rows
            if row["status"] != "DOWNLOADED" or not self.raw_path(row).is_file()
        ]
        return pending[:max_files] if max_files is not None else pending

    def get_archive(self, url: str) -> sqlite3.Row:
        with self._lock:
            row = self.connection.execute("SELECT * FROM archive WHERE url=?", (url,)).fetchone()
        if row is None:
            raise KeyError(url)
        return row

    def mark_downloaded(self, url: str, size: int, sha256: str) -> None:
        with self._lock, self.connection:
            self.connection.execute(
                """UPDATE archive SET status='DOWNLOADED', local_size=?, sha256=?, error=NULL,
                   downloaded_at=datetime('now') WHERE url=?""",
                (size, sha256, url),
            )

    def mark_failure(self, url: str, error: str) -> None:
        with self._lock, self.connection:
            self.connection.execute(
                "UPDATE archive SET status='FAILED', error=? WHERE url=?",
                (error[:2000], url),
            )

    def mark_index_failure(self, url: str, error: str) -> None:
        with self._lock, self.connection:
            self.connection.execute(
                "UPDATE archive SET error=? WHERE url=?",
                (error[:2000], url),
            )

    def status(self) -> dict[str, int]:
        with self._lock:
            status_rows = self.connection.execute(
                "SELECT status, COUNT(*) AS count FROM archive GROUP BY status"
            ).fetchall()
            totals = self.connection.execute(
                "SELECT COUNT(*) AS discovered, COALESCE(SUM(local_size), 0) AS bytes FROM archive"
            ).fetchone()
            indexed = self.connection.execute(
                "SELECT COUNT(*) FROM archive WHERE indexed_sha256=sha256 AND sha256 IS NOT NULL"
            ).fetchone()[0]
        result = {row["status"].lower(): row["count"] for row in status_rows}
        result.update({"discovered": totals["discovered"], "bytes": totals["bytes"], "indexed": indexed})
        return result


class Downloader:
    def __init__(
        self,
        manifest: Manifest,
        opener: Callable[..., object] = urlopen,
        workers: int = 4,
        timeout: int = 90,
        retries: int = 4,
        reserve_bytes: int = 5 * 1024 * 1024 * 1024,
    ) -> None:
        self.manifest = manifest
        self.opener = opener
        self.workers = max(1, min(workers, 8))
        self.timeout = timeout
        self.retries = max(1, retries)
        self.reserve_bytes = max(0, reserve_bytes)

    def download_pending(self, max_files: int | None = None) -> tuple[int, int]:
        rows = self.manifest.pending_archives(max_files)
        completed = 0
        failed = 0
        with ThreadPoolExecutor(max_workers=self.workers, thread_name_prefix="3gpp-download") as pool:
            futures = [pool.submit(self._download_with_retry, row) for row in rows]
            for future in as_completed(futures):
                if future.result():
                    completed += 1
                else:
                    failed += 1
        return completed, failed

    def _download_with_retry(self, row: sqlite3.Row) -> bool:
        last_error: Exception | None = None
        for attempt in range(self.retries):
            try:
                self._download_one(row)
                return True
            except Exception as error:  # each item remains retryable in the manifest
                last_error = error
                if attempt + 1 < self.retries:
                    time.sleep(min(2**attempt, 8))
        assert last_error is not None
        self.manifest.mark_failure(row["url"], f"{type(last_error).__name__}: {last_error}")
        return False

    def _download_one(self, row: sqlite3.Row) -> None:
        target = self.manifest.raw_path(row)
        target.parent.mkdir(parents=True, exist_ok=True)
        part = target.with_suffix(target.suffix + ".part")
        offset = part.stat().st_size if part.exists() else 0
        headers = {"User-Agent": USER_AGENT, "Accept-Encoding": "identity"}
        if offset:
            headers["Range"] = f"bytes={offset}-"
        request = Request(row["url"], headers=headers)
        with self.opener(request, timeout=self.timeout) as response:
            status = getattr(response, "status", 200)
            append = offset > 0 and status == 206
            expected = self._expected_total(response.headers, offset if append else 0)
            free = shutil.disk_usage(self.manifest.root).free
            remaining = max(0, (expected or 0) - (offset if append else 0))
            if free - remaining < self.reserve_bytes:
                raise OSError(
                    f"Insufficient disk space: free={free}, required={remaining}, reserve={self.reserve_bytes}"
                )
            mode = "ab" if append else "wb"
            with part.open(mode) as output:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    output.write(chunk)
        actual = part.stat().st_size
        remote_size = row["remote_size"]
        if expected is not None and actual != expected:
            raise IOError(f"Incomplete download: expected {expected} bytes, got {actual}")
        if remote_size is not None and actual != remote_size:
            raise IOError(f"Remote size mismatch: expected {remote_size} bytes, got {actual}")
        if target.suffix.lower() == ".zip":
            try:
                with zipfile.ZipFile(part) as archive:
                    bad_member = archive.testzip()
                    if bad_member is not None:
                        raise zipfile.BadZipFile(f"CRC failure in {bad_member}")
            except zipfile.BadZipFile:
                part.unlink(missing_ok=True)
                raise
        digest = hashlib.sha256()
        with part.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        os.replace(part, target)
        self.manifest.mark_downloaded(row["url"], actual, digest.hexdigest())

    @staticmethod
    def _expected_total(headers: object, offset: int) -> int | None:
        content_range = headers.get("Content-Range") if hasattr(headers, "get") else None
        if content_range:
            match = re.search(r"/(\d+)$", content_range)
            if match:
                return int(match.group(1))
        content_length = headers.get("Content-Length") if hasattr(headers, "get") else None
        return offset + int(content_length) if content_length is not None else None
