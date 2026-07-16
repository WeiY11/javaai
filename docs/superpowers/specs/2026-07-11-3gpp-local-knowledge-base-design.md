# 3GPP Local Knowledge Base Design

## Goal

Mirror the complete official 3GPP specification archive to local storage and turn it into an incrementally maintainable, offline full-text knowledge base.

## Source and scope

- Canonical source: `https://www.3gpp.org/ftp/Specs/Archive/`.
- Crawl every series, specification directory, historical version archive, and OpenAPI subtree exposed below that root.
- Keep original files unchanged under `data/3gpp/raw/`; record URL, size, modification metadata, SHA-256, status, and errors in SQLite.
- Index every extractable member. Search defaults to the newest discovered version of each specification and can include all historical versions.
- Never redistribute the mirrored corpus. Keep the data directory gitignored and retain source URLs in every record.

## Architecture

`tools/threegpp_kb` is a standalone Python 3.11 utility with no new package dependency. The crawler discovers same-origin links below the configured archive root. The downloader writes `.part` files, resumes with HTTP Range when supported, atomically renames completed files, and verifies content length and SHA-256. A manifest in `data/3gpp/3gpp.db` makes every stage idempotent.

The extractor reads ZIP members without trusting member paths. DOCX uses OOXML parsing, PDF uses the local `pdftotext`, text/OpenAPI formats use bounded decoding, and legacy DOC uses a hidden local Microsoft Word COM instance. Nested ZIPs are parsed with bounded recursion. Extracted text is stored in `document` and indexed in SQLite FTS5.

## Interfaces

- `python -m tools.threegpp_kb sync`: discover and download the complete archive.
- `python -m tools.threegpp_kb index`: extract all downloaded files and update FTS.
- `python -m tools.threegpp_kb build`: run sync followed by indexing.
- `python -m tools.threegpp_kb search "query"`: search newest versions; `--all-versions` searches history.
- `python -m tools.threegpp_kb status`: show discovered, downloaded, indexed, failed, and byte totals.

All commands accept `--root`, while sync/build accept `--series`, `--workers`, and `--max-files` for controlled validation. Re-running any command only processes changed or incomplete work.

## Failure handling

Network requests use timeouts, bounded retries, exponential backoff, a descriptive User-Agent, and resumable downloads. Failed items remain in the manifest with their last error and are retried on the next run. Extraction failure of one archive member cannot roll back other members. Interrupted writes never replace a completed raw file or committed FTS row.

## Verification

Unit tests cover link confinement, 3GPP version decoding, safe ZIP handling, DOCX extraction, latest-version selection, idempotent indexing, and search. An integration smoke test crawls and downloads a bounded sample from the official archive, indexes it, and proves a known term can be retrieved. Full synchronization runs as a restartable background process with status and log files under `data/3gpp/`.

