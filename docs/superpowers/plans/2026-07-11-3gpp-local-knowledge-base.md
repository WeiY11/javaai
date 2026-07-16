# 3GPP Local Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a restartable local mirror and offline full-text knowledge base for the complete official 3GPP specification archive.

**Architecture:** Add a standalone Python package that crawls the official archive, downloads files into a gitignored mirror, records durable state in SQLite, extracts text from 3GPP document formats, and indexes it with FTS5. Keep it independent of EviMind runtime credentials so the corpus remains usable even when PostgreSQL, Elasticsearch, or an embedding provider is unavailable.

**Tech Stack:** Python 3.11 standard library, SQLite FTS5, Microsoft Word COM through installed pywin32 for legacy DOC, local `pdftotext`, unittest.

## Global Constraints

- Canonical source is `https://www.3gpp.org/ftp/Specs/Archive/`.
- The default sync scope is the complete archive, including historical versions and OpenAPI subtrees.
- All remote links must remain on `www.3gpp.org` and below the configured archive root.
- Data is stored below `data/3gpp/` and must not be committed.
- Downloads and indexing must be idempotent and restartable.
- Search defaults to the latest discovered version per specification and supports all-version search explicitly.

---

### Task 1: Discovery and manifest

**Files:**
- Create: `tools/threegpp_kb/core.py`
- Create: `tools/threegpp_kb/tests/test_core.py`

**Interfaces:**
- Produces: `ArchiveCrawler.discover()`, `Manifest.upsert_archives()`, `decode_version(code)`.

- [ ] Write tests for same-origin/root-confined discovery, file metadata parsing, and numeric/letter version codes.
- [ ] Run `python -m unittest tools.threegpp_kb.tests.test_core -v` and confirm failures are caused by missing code.
- [ ] Implement URL confinement, HTML link parsing, archive records, SQLite schema, and latest-version calculation.
- [ ] Re-run the tests and confirm they pass.

### Task 2: Restartable downloader

**Files:**
- Modify: `tools/threegpp_kb/core.py`
- Modify: `tools/threegpp_kb/tests/test_core.py`

**Interfaces:**
- Produces: `Downloader.download_pending(max_files=None)` with `.part` resume and atomic completion.

- [ ] Add failing tests for resume headers, full-response fallback, size mismatch, and atomic rename.
- [ ] Run the focused tests and confirm expected failures.
- [ ] Implement bounded retry/backoff, SHA-256, manifest status/error updates, and worker concurrency.
- [ ] Re-run focused and complete unit tests.

### Task 3: Secure extraction and FTS indexing

**Files:**
- Create: `tools/threegpp_kb/extract.py`
- Create: `tools/threegpp_kb/index.py`
- Create: `tools/threegpp_kb/tests/test_extract.py`
- Create: `tools/threegpp_kb/tests/test_index.py`

**Interfaces:**
- Produces: `ContentExtractor.extract(path)`, `KnowledgeIndex.index_pending()`, `KnowledgeIndex.search()`.

- [ ] Add failing tests for DOCX paragraph/table extraction, ZIP traversal rejection, nested ZIP bounds, latest-version search, and re-index idempotency.
- [ ] Run both test modules and confirm missing behavior fails.
- [ ] Implement bounded format extraction, optional Word COM conversion, transactional document/FTS replacement, snippets, and ranking.
- [ ] Re-run all package tests.

### Task 4: CLI, operations, and real-source proof

**Files:**
- Create: `tools/__init__.py`
- Create: `tools/threegpp_kb/__init__.py`
- Create: `tools/threegpp_kb/__main__.py`
- Create: `tools/threegpp_kb/README.md`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `sync`, `index`, `build`, `search`, and `status` commands.

- [ ] Add failing CLI parsing/status tests.
- [ ] Implement command wiring, progress logging, filters, and operator documentation.
- [ ] Run `python -m unittest discover -s tools/threegpp_kb/tests -v`.
- [ ] Run a bounded official-source build into a temporary root and verify manifest, raw file, indexed text, and search output.
- [ ] Start the complete build as a hidden background process writing `data/3gpp/build.log` and `data/3gpp/build.err.log`.
- [ ] Run `git diff --check`, inspect the final diff, and record exact verification boundaries.

