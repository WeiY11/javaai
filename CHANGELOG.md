# Changelog

## Unreleased

- Hardened document ingestion with staged status, retry metadata, versioned chunks, active-version switching, and stale-version cleanup.
- Changed document upload storage keys to opaque UUID paths and added basic extension/MIME/size/path validation.
- Made document, knowledge-base, RAG, conversation, and permission checks fail closed when authentication context is missing.
- Unified Axios and streaming fetch token refresh with a shared refresh promise and one retry.
- Reworked SSE parsing for chunk boundaries, multi-line data, errors, aborts, duplicate events, and missing done events.
- Added explicit ingestion, retrieval, and LLM executors.
- Made Spotless, PMD, and JaCoCo gates blocking in CI with first-stage coverage thresholds.
