import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.threegpp_kb.extract import ContentExtractor


def docx_bytes(paragraph: str, table_cell: str = "") -> bytes:
    import io

    output = io.BytesIO()
    document_xml = f"""<?xml version="1.0" encoding="UTF-8"?>
    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
      <w:body>
        <w:p><w:r><w:t>{paragraph}</w:t></w:r></w:p>
        <w:tbl><w:tr><w:tc><w:p><w:r><w:t>{table_cell}</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
      </w:body>
    </w:document>"""
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("word/document.xml", document_xml)
        archive.writestr("[Content_Types].xml", "<Types/>")
    return output.getvalue()


class ContentExtractorTest(unittest.TestCase):
    def test_word_cleanup_errors_do_not_fail_completed_extraction(self):
        class BrokenComObject:
            def Close(self, value):
                raise RuntimeError("close failed")

            def Quit(self):
                raise RuntimeError("quit failed")

        ContentExtractor._safe_close_word(BrokenComObject(), BrokenComObject())

    def test_extracts_docx_paragraphs_and_tables_from_spec_zip(self):
        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "23287-j00.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(
                    "23287-j00.docx", docx_bytes("Vehicle-to-everything services", "V2X")
                )

            documents = ContentExtractor().extract(archive_path)

            self.assertEqual(1, len(documents))
            self.assertEqual("23287-j00.docx", documents[0].member_name)
            self.assertIn("Vehicle-to-everything services", documents[0].text)
            self.assertIn("V2X", documents[0].text)

    def test_rejects_zip_traversal_members(self):
        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "bad.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("../escape.txt", "must not be indexed")

            documents = ContentExtractor().extract(archive_path)

            self.assertEqual([], documents)

    def test_bounds_nested_zip_recursion(self):
        import io

        inner = io.BytesIO()
        with zipfile.ZipFile(inner, "w") as archive:
            archive.writestr("spec.txt", "bounded nested content")
        outer = io.BytesIO()
        with zipfile.ZipFile(outer, "w") as archive:
            archive.writestr("inner.zip", inner.getvalue())

        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "outer.zip"
            archive_path.write_bytes(outer.getvalue())
            self.assertEqual([], ContentExtractor(max_nested_depth=0).extract(archive_path))
            docs = ContentExtractor(max_nested_depth=1).extract(archive_path)
            self.assertEqual("inner.zip!/spec.txt", docs[0].member_name)


if __name__ == "__main__":
    unittest.main()
