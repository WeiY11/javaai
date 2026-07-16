import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.threegpp_kb.core import (
    ArchiveCrawler,
    ArchiveRecord,
    Downloader,
    Manifest,
    decode_version,
    parse_directory,
)


ROOT = "https://www.3gpp.org/ftp/Specs/Archive/"


class VersionTest(unittest.TestCase):
    def test_decodes_numeric_and_letter_major_versions(self):
        self.assertEqual((1, 2, 1), decode_version("121"))
        self.assertEqual((19, 0, 0), decode_version("j00"))
        self.assertEqual((20, 1, 2), decode_version("k12"))

    def test_rejects_invalid_version_code(self):
        with self.assertRaises(ValueError):
            decode_version("20")


class DirectoryParsingTest(unittest.TestCase):
    def test_keeps_only_links_below_official_root(self):
        html = """
        <a href="23_series/">series</a>
        <a href="/ftp/Specs/Archive/24_series/">absolute</a>
        <a href="../">parent</a>
        <a href="?sortby=name">sort</a>
        <a href="https://evil.example/file.zip">evil</a>
        <a href="23287-j00.zip">file</a>
        """
        directories, files = parse_directory(html, ROOT, ROOT)
        self.assertEqual(
            [
                "https://www.3gpp.org/ftp/Specs/Archive/23_series/",
                "https://www.3gpp.org/ftp/Specs/Archive/24_series/",
            ],
            directories,
        )
        self.assertEqual(
            ["https://www.3gpp.org/ftp/Specs/Archive/23287-j00.zip"], files
        )

    def test_treats_extensionless_official_links_as_directories(self):
        html = '<a href="https://www.3gpp.org/ftp/Specs/Archive/38_series">38</a>'
        directories, files = parse_directory(html, ROOT, ROOT)
        self.assertEqual([ROOT + "38_series/"], directories)
        self.assertEqual([], files)

    def test_crawler_records_series_spec_and_version_from_path(self):
        pages = {
            ROOT: '<a href="23_series/">23</a>',
            ROOT + "23_series/": '<a href="23.287/">23.287</a>',
            ROOT + "23_series/23.287/": '<a href="23287-j00.zip">zip</a>',
        }
        crawler = ArchiveCrawler(ROOT, fetch_text=pages.__getitem__)
        records = crawler.discover()
        self.assertEqual(1, len(records))
        record = records[0]
        self.assertEqual("23", record.series)
        self.assertEqual("23.287", record.spec)
        self.assertEqual("j00", record.version_code)
        self.assertEqual((19, 0, 0), record.version)

    def test_crawler_skips_checkpointed_directory_without_requesting_it(self):
        child = ROOT + "23_series/"
        pages = {ROOT: '<a href="23_series/">23</a>', child: '<a href="23287-j00.zip">zip</a>'}
        requested = []
        completed = []

        def fetch(url):
            requested.append(url)
            return pages[url]

        crawler = ArchiveCrawler(ROOT, fetch_text=fetch)
        records = crawler.discover(
            should_crawl=lambda url: url != child,
            on_directory_crawled=completed.append,
        )

        self.assertEqual([ROOT], requested)
        self.assertEqual([ROOT], completed)
        self.assertEqual([], records)


class FakeResponse(io.BytesIO):
    def __init__(self, body: bytes, status: int, headers: dict[str, str]):
        super().__init__(body)
        self.status = status
        self.headers = headers

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()


class DownloaderTest(unittest.TestCase):
    def test_missing_completed_file_is_downloaded_again(self):
        calls = []

        def opener(request, timeout):
            calls.append(request.full_url)
            return FakeResponse(b"restored", 200, {"Content-Length": "8"})

        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            record = ArchiveRecord.from_url(ROOT + "OpenAPI/sample.txt", ROOT)
            manifest.upsert_archives([record])
            manifest.mark_downloaded(record.url, 8, "old")

            Downloader(manifest, opener=opener, workers=1).download_pending()

            self.assertEqual([record.url], calls)
            self.assertEqual(b"restored", manifest.raw_path(record).read_bytes())
            manifest.close()


class ManifestTest(unittest.TestCase):
    def test_checkpoint_survives_reopen(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = Manifest(root)
            manifest.mark_directory_crawled(ROOT)
            manifest.close()

            reopened = Manifest(root)
            self.assertTrue(reopened.is_directory_crawled(ROOT))
            reopened.close()

    def test_page_upsert_can_defer_expensive_latest_refresh(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            record = ArchiveRecord.from_url(ROOT + "23_series/23.287/23287-j00.zip", ROOT)
            with patch.object(manifest, "_refresh_latest") as refresh:
                manifest.upsert_archives([record], refresh_latest=False)
            refresh.assert_not_called()
            self.assertEqual(1, manifest.connection.execute("SELECT COUNT(*) FROM archive").fetchone()[0])
            manifest.close()

    def test_resumes_partial_file_and_atomically_completes(self):
        requests = []

        def opener(request, timeout):
            requests.append(request)
            return FakeResponse(b"def", 206, {"Content-Range": "bytes 3-5/6"})

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = Manifest(root)
            record = ArchiveRecord.from_url(ROOT + "OpenAPI/sample.txt", ROOT)
            manifest.upsert_archives([record])
            target = manifest.raw_path(record)
            target.parent.mkdir(parents=True)
            target.with_suffix(target.suffix + ".part").write_bytes(b"abc")

            Downloader(manifest, opener=opener, workers=1).download_pending()

            self.assertEqual(b"abcdef", target.read_bytes())
            self.assertFalse(target.with_suffix(target.suffix + ".part").exists())
            self.assertEqual("bytes=3-", requests[0].get_header("Range"))
            self.assertEqual("DOWNLOADED", manifest.get_archive(record.url)["status"])
            manifest.close()

    def test_server_ignoring_range_restarts_instead_of_appending(self):
        def opener(request, timeout):
            return FakeResponse(b"fresh", 200, {"Content-Length": "5"})

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = Manifest(root)
            record = ArchiveRecord.from_url(ROOT + "OpenAPI/sample.txt", ROOT)
            manifest.upsert_archives([record])
            target = manifest.raw_path(record)
            target.parent.mkdir(parents=True)
            target.with_suffix(target.suffix + ".part").write_bytes(b"stale")

            Downloader(manifest, opener=opener, workers=1).download_pending()

            self.assertEqual(b"fresh", target.read_bytes())
            manifest.close()

    def test_corrupt_zip_is_not_marked_downloaded(self):
        def opener(request, timeout):
            return FakeResponse(b"not-a-zip", 200, {"Content-Length": "9"})

        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            record = ArchiveRecord.from_url(ROOT + "23_series/23.287/23287-j00.zip", ROOT)
            manifest.upsert_archives([record])

            completed, failed = Downloader(
                manifest, opener=opener, workers=1, retries=1, reserve_bytes=0
            ).download_pending()

            self.assertEqual((0, 1), (completed, failed))
            self.assertEqual("FAILED", manifest.get_archive(record.url)["status"])
            self.assertFalse(manifest.raw_path(record).exists())
            manifest.close()


if __name__ == "__main__":
    unittest.main()
