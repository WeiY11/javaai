import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.threegpp_kb.core import ArchiveRecord, Manifest
from tools.threegpp_kb.index import KnowledgeIndex


ROOT = "https://www.3gpp.org/ftp/Specs/Archive/"


class KnowledgeIndexTest(unittest.TestCase):
    def add_archive(self, manifest: Manifest, spec: str, code: str, text: str):
        series = spec.split(".", 1)[0]
        compact = spec.replace(".", "")
        record = ArchiveRecord.from_url(
            f"{ROOT}{series}_series/{spec}/{compact}-{code}.zip", ROOT
        )
        manifest.upsert_archives([record])
        path = manifest.raw_path(record)
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(f"{compact}-{code}.txt", text)
        manifest.mark_downloaded(record.url, path.stat().st_size, "sha")
        return record

    def test_search_defaults_to_latest_version_and_can_search_history(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            self.add_archive(manifest, "23.287", "i00", "legacy sidelink behavior")
            self.add_archive(manifest, "23.287", "j00", "current sidelink behavior")
            index = KnowledgeIndex(manifest)
            index.index_pending()

            latest = index.search("sidelink")
            history = index.search("sidelink", all_versions=True)

            self.assertEqual(["j00"], [item.version_code for item in latest])
            self.assertEqual({"i00", "j00"}, {item.version_code for item in history})
            manifest.close()

    def test_reindex_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            self.add_archive(manifest, "38.331", "j00", "radio resource control")
            index = KnowledgeIndex(manifest)

            index.index_pending()
            index.index_pending(force=True)

            self.assertEqual(1, manifest.connection.execute("SELECT COUNT(*) FROM document").fetchone()[0])
            self.assertEqual(1, len(index.search("resource control", all_versions=True)))
            manifest.close()

    def test_extraction_failure_keeps_downloaded_file_retryable_without_redownload(self):
        class BrokenExtractor:
            def extract(self, path):
                raise RuntimeError("temporary Word failure")

        with tempfile.TemporaryDirectory() as directory:
            manifest = Manifest(Path(directory))
            record = self.add_archive(manifest, "38.101", "j00", "content")

            indexed, failed = KnowledgeIndex(manifest, BrokenExtractor()).index_pending()

            self.assertEqual((0, 1), (indexed, failed))
            row = manifest.get_archive(record.url)
            self.assertEqual("DOWNLOADED", row["status"])
            self.assertIn("temporary Word failure", row["error"])
            manifest.close()


if __name__ == "__main__":
    unittest.main()
