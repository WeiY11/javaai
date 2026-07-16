from __future__ import annotations

import re
from dataclasses import dataclass

from .core import Manifest
from .extract import ContentExtractor


@dataclass(frozen=True)
class SearchResult:
    spec: str | None
    version_code: str | None
    member_name: str
    title: str
    source_url: str
    snippet: str
    score: float


class KnowledgeIndex:
    def __init__(self, manifest: Manifest, extractor: ContentExtractor | None = None) -> None:
        self.manifest = manifest
        self.extractor = extractor or ContentExtractor()
        self._create_schema()

    def _create_schema(self) -> None:
        with self.manifest.connection:
            self.manifest.connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS document (
                    id INTEGER PRIMARY KEY,
                    archive_id INTEGER NOT NULL REFERENCES archive(id) ON DELETE CASCADE,
                    member_name TEXT NOT NULL,
                    format TEXT NOT NULL,
                    spec TEXT,
                    version_code TEXT,
                    title TEXT NOT NULL,
                    source_url TEXT NOT NULL,
                    text TEXT NOT NULL,
                    is_latest INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(archive_id, member_name)
                );
                CREATE INDEX IF NOT EXISTS idx_document_archive ON document(archive_id);
                CREATE INDEX IF NOT EXISTS idx_document_latest_spec ON document(is_latest, spec);
                CREATE VIRTUAL TABLE IF NOT EXISTS document_fts USING fts5(
                    title, spec, version_code, member_name, text,
                    tokenize='unicode61 remove_diacritics 2'
                );
                """
            )

    def index_pending(self, force: bool = False, max_files: int | None = None) -> tuple[int, int]:
        where = "status='DOWNLOADED'"
        if not force:
            where += " AND (indexed_sha256 IS NULL OR indexed_sha256 != sha256)"
        query = f"SELECT * FROM archive WHERE {where} ORDER BY relative_path"
        params: tuple[object, ...] = ()
        if max_files is not None:
            query += " LIMIT ?"
            params = (max_files,)
        rows = list(self.manifest.connection.execute(query, params))
        indexed = 0
        failed = 0
        for row in rows:
            try:
                documents = self.extractor.extract(self.manifest.raw_path(row))
                with self.manifest.connection:
                    old_ids = [
                        item[0]
                        for item in self.manifest.connection.execute(
                            "SELECT id FROM document WHERE archive_id=?", (row["id"],)
                        )
                    ]
                    self.manifest.connection.executemany(
                        "DELETE FROM document_fts WHERE rowid=?", ((item,) for item in old_ids)
                    )
                    self.manifest.connection.execute("DELETE FROM document WHERE archive_id=?", (row["id"],))
                    for document in documents:
                        cursor = self.manifest.connection.execute(
                            """
                            INSERT INTO document(
                                archive_id, member_name, format, spec, version_code,
                                title, source_url, text, is_latest
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            (
                                row["id"],
                                document.member_name,
                                document.format,
                                row["spec"],
                                row["version_code"],
                                document.title,
                                row["url"],
                                document.text,
                                row["is_latest"],
                            ),
                        )
                        self.manifest.connection.execute(
                            """INSERT INTO document_fts(rowid, title, spec, version_code, member_name, text)
                               VALUES (?, ?, ?, ?, ?, ?)""",
                            (
                                cursor.lastrowid,
                                document.title,
                                row["spec"] or "",
                                row["version_code"] or "",
                                document.member_name,
                                document.text,
                            ),
                        )
                    self.manifest.connection.execute(
                        """UPDATE archive SET indexed_sha256=sha256, indexed_at=datetime('now'), error=NULL
                           WHERE id=?""",
                        (row["id"],),
                    )
                indexed += 1
            except Exception as error:
                self.manifest.mark_index_failure(
                    row["url"], f"Index {type(error).__name__}: {error}"
                )
                failed += 1
        with self.manifest.connection:
            self.manifest.connection.execute(
                """UPDATE document SET is_latest=(
                       SELECT is_latest FROM archive WHERE archive.id=document.archive_id
                   )"""
            )
        return indexed, failed

    def search(self, query: str, limit: int = 10, all_versions: bool = False) -> list[SearchResult]:
        terms = re.findall(r"[\w.-]+", query, flags=re.UNICODE)
        if not terms:
            return []
        fts_query = " AND ".join('"' + term.replace('"', '""') + '"' for term in terms)
        latest_clause = "" if all_versions else "AND d.is_latest=1"
        rows = self.manifest.connection.execute(
            f"""
            SELECT d.spec, d.version_code, d.member_name, d.title, d.source_url,
                   snippet(document_fts, 4, '[', ']', ' ... ', 28) AS snippet,
                   bm25(document_fts, 3.0, 1.5, 1.0, 0.5, 1.0) AS score
            FROM document_fts
            JOIN document AS d ON d.id=document_fts.rowid
            WHERE document_fts MATCH ? {latest_clause}
            ORDER BY score ASC
            LIMIT ?
            """,
            (fts_query, max(1, min(limit, 100))),
        ).fetchall()
        return [
            SearchResult(
                row["spec"],
                row["version_code"],
                row["member_name"],
                row["title"],
                row["source_url"],
                row["snippet"],
                row["score"],
            )
            for row in rows
        ]
