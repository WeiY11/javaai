# EviMind Architecture Notes

## Ingestion

Document ingestion is now versioned. A document starts at `UPLOADED`, then a worker atomically claims it and moves through:

`EXTRACTING -> CLEANING -> CHUNKING -> PERSISTING -> EMBEDDING -> INDEXING -> ENRICHING -> COMPLETED`

Failures move the document to `FAILED` and record `failedStage`, `errorCode`, `errorMessage`, `retryCount`, `startedAt`, `finishedAt`, and `ingestionVersion`.

The pipeline no longer wraps file parsing, LLM calls, embedding, Elasticsearch indexing, and enrichment in one database transaction. Database writes are short status or persistence steps. A retry builds a new `ingestionVersion`; `activeIngestionVersion` switches only after the new version completes.

## Retrieval

Vector retrieval joins active document versions, so stale chunks left by a failed cleanup are not returned by pgvector or local keyword fallback. Elasticsearch writes include `ingestionVersion`; stale ES versions are deleted after a successful active-version switch.

## Streaming Chat

Streaming chat uses the same token refresh path as Axios requests through `authenticatedFetch`. SSE parsing handles chunk boundaries, multi-line `data`, comments, `error` events, missing `done`, duplicate ids, and aborts.
