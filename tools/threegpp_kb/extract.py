from __future__ import annotations

import io
import re
import subprocess
import tempfile
import zipfile
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath
from xml.etree import ElementTree


TEXT_SUFFIXES = {".txt", ".md", ".xml", ".yaml", ".yml", ".json", ".csv", ".html", ".htm"}
SUPPORTED_SUFFIXES = TEXT_SUFFIXES | {".doc", ".docx", ".pdf", ".zip"}


@dataclass(frozen=True)
class ExtractedDocument:
    member_name: str
    format: str
    text: str
    title: str


class _HtmlText(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        if data.strip():
            self.parts.append(data.strip())


class ContentExtractor:
    def __init__(
        self,
        max_nested_depth: int = 2,
        max_member_bytes: int = 100 * 1024 * 1024,
        max_total_bytes: int = 500 * 1024 * 1024,
        max_text_chars: int = 8_000_000,
    ) -> None:
        self.max_nested_depth = max_nested_depth
        self.max_member_bytes = max_member_bytes
        self.max_total_bytes = max_total_bytes
        self.max_text_chars = max_text_chars

    def extract(self, path: Path | str) -> list[ExtractedDocument]:
        source = Path(path)
        data = source.read_bytes()
        return self._extract_bytes(data, source.name, 0)

    def _extract_bytes(self, data: bytes, name: str, depth: int) -> list[ExtractedDocument]:
        suffix = Path(name).suffix.lower()
        if suffix == ".zip":
            if depth > self.max_nested_depth:
                return []
            return self._extract_zip(data, name, depth)
        text = self._extract_single(data, suffix)
        if not text or not text.strip():
            return []
        text = self._normalize(text)[: self.max_text_chars]
        title = next((line.strip() for line in text.splitlines() if line.strip()), Path(name).stem)
        return [ExtractedDocument(name, suffix.lstrip("."), text, title[:500])]

    def _extract_zip(self, data: bytes, container_name: str, depth: int) -> list[ExtractedDocument]:
        documents: list[ExtractedDocument] = []
        total = 0
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for info in archive.infolist():
                if info.is_dir() or not self._safe_member(info.filename):
                    continue
                suffix = Path(info.filename).suffix.lower()
                if suffix not in SUPPORTED_SUFFIXES:
                    continue
                total += info.file_size
                if info.file_size > self.max_member_bytes or total > self.max_total_bytes:
                    continue
                if info.compress_size and info.file_size / info.compress_size > 1000:
                    continue
                try:
                    member_data = archive.read(info)
                    member_name = info.filename
                    if suffix == ".zip":
                        if depth >= self.max_nested_depth:
                            continue
                        nested = self._extract_bytes(member_data, member_name, depth + 1)
                        documents.extend(
                            ExtractedDocument(
                                f"{member_name}!/{item.member_name}", item.format, item.text, item.title
                            )
                            for item in nested
                        )
                    else:
                        documents.extend(self._extract_bytes(member_data, member_name, depth))
                except (OSError, ValueError, zipfile.BadZipFile, ElementTree.ParseError):
                    continue
        return documents

    @staticmethod
    def _safe_member(name: str) -> bool:
        normalized = name.replace("\\", "/")
        path = PurePosixPath(normalized)
        return not path.is_absolute() and ".." not in path.parts and not re.match(r"^[A-Za-z]:", normalized)

    def _extract_single(self, data: bytes, suffix: str) -> str:
        if suffix == ".docx":
            return self._docx_text(data)
        if suffix == ".doc":
            return self._word_doc_text(data)
        if suffix == ".pdf":
            return self._pdf_text(data)
        if suffix in {".html", ".htm"}:
            parser = _HtmlText()
            parser.feed(self._decode_text(data))
            return "\n".join(parser.parts)
        if suffix in TEXT_SUFFIXES:
            return self._decode_text(data)
        return ""

    @staticmethod
    def _docx_text(data: bytes) -> str:
        with zipfile.ZipFile(io.BytesIO(data)) as document:
            xml = document.read("word/document.xml")
        root = ElementTree.fromstring(xml)
        namespace = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
        lines: list[str] = []
        for paragraph in root.iter(namespace + "p"):
            text = "".join(node.text or "" for node in paragraph.iter(namespace + "t")).strip()
            if text:
                lines.append(text)
        return "\n".join(lines)

    @staticmethod
    def _word_doc_text(data: bytes) -> str:
        try:
            import pythoncom
            import win32com.client
        except ImportError as error:
            raise RuntimeError("Legacy DOC extraction requires pywin32 and Microsoft Word") from error
        pythoncom.CoInitialize()
        word = None
        document = None
        try:
            with tempfile.NamedTemporaryFile(suffix=".doc", delete=False) as handle:
                handle.write(data)
                path = Path(handle.name)
            word = win32com.client.DispatchEx("Word.Application")
            word.Visible = False
            word.DisplayAlerts = 0
            document = word.Documents.Open(str(path), ReadOnly=True, AddToRecentFiles=False)
            return str(document.Content.Text)
        finally:
            ContentExtractor._safe_close_word(document, word)
            if "path" in locals():
                path.unlink(missing_ok=True)
            pythoncom.CoUninitialize()

    @staticmethod
    def _safe_close_word(document: object | None, word: object | None) -> None:
        if document is not None:
            try:
                document.Close(False)
            except Exception:
                pass
        if word is not None:
            try:
                word.Quit()
            except Exception:
                pass

    @staticmethod
    def _pdf_text(data: bytes) -> str:
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as handle:
            handle.write(data)
            path = Path(handle.name)
        try:
            result = subprocess.run(
                ["pdftotext", "-layout", str(path), "-"],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=120,
            )
            return result.stdout.decode("utf-8", errors="replace")
        finally:
            path.unlink(missing_ok=True)

    @staticmethod
    def _decode_text(data: bytes) -> str:
        for encoding in ("utf-8-sig", "utf-16", "windows-1252", "latin-1"):
            try:
                return data.decode(encoding)
            except UnicodeDecodeError:
                continue
        return data.decode("utf-8", errors="replace")

    @staticmethod
    def _normalize(text: str) -> str:
        text = text.replace("\x00", "").replace("\r\n", "\n").replace("\r", "\n")
        text = re.sub(r"[ \t]+", " ", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()
