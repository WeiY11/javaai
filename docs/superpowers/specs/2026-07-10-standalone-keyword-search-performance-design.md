# Standalone Keyword Search Performance Design

## Problem

`SimpleKeywordSearchService` is the active keyword fallback in the standalone profile. A search currently loads every chunk in a knowledge base, queries its parent document once per chunk, discards inactive ingestion versions in Java, creates every matching `SearchResult`, and sorts the full match set.

For `N` chunks and `M` matches this costs `1 + N` database calls, transfers all `N` chunk bodies, allocates `O(M)` result objects, and sorts in `O(M log M)`. The path is exercised whenever Elasticsearch is absent or returns no results, which is the default standalone deployment shape.

## Success Criteria

- Preserve the existing term parsing and score formula.
- Return only chunks from each document's active ingestion version.
- Preserve descending-score Top-K behavior with deterministic tie handling.
- Use one mapper call per non-empty search, independent of chunk count.
- Make the database return only chunks containing at least one query term.
- Keep at most `K` scored candidates in the Java selection structure.
- Return immediately without database access for blank/non-searchable queries or `topK <= 0`.
- Keep the SQL compatible with PostgreSQL and H2 PostgreSQL mode.
- Verify the mapper SQL against an in-memory H2 database, not only Mockito.

## Considered Approaches

### 1. PostgreSQL and H2 native full-text indexes

This offers the best asymptotic lookup performance, but PostgreSQL `tsvector` and H2 full-text facilities require different schemas, tokenization, migrations, and ranking semantics. It would make the fallback behavior depend on the deployment database and is too broad for a safe continuation in the current dirty worktree.

### 2. Cache documents or search results

A document cache removes repeated `selectById` calls after warm-up, but cold requests still have the N+1 pattern and invalidation must track ingestion-version activation, deletion, and retry. A query cache also risks stale authorization-sensitive results. It treats the symptom rather than the query shape.

### 3. Joined active-chunk prefilter plus bounded Top-K

Add one mapper query joining `document_chunk` to `document` on document ID and active ingestion version, with parameterized `LOWER(content) LIKE` predicates for searchable terms. Score only those matches and keep a min-heap bounded by `topK`. This removes N+1 access, reduces database-to-JVM transfer, preserves the current scoring formula, and works on both configured databases.

This is the selected approach.

## Design

### Mapper boundary

`DocumentChunkMapper.findActiveContainingAnyTerm(Long knowledgeBaseId, List<String> terms)` returns active chunks in the requested knowledge base whose lower-cased content contains at least one lower-cased term. Terms are bound through MyBatis parameters; no query text is concatenated into SQL.

The join condition is:

```sql
document.id = document_chunk.document_id
AND document.active_ingestion_version = document_chunk.ingestion_version
```

The service never calls `DocumentMapper` on this path.

### Top-K selection

The service calculates the existing score for each returned chunk. A priority queue retains the weakest selected item at its head. When the queue reaches `topK`, a new candidate replaces the head only when it ranks higher. Final output is sorted by score descending and chunk ID ascending for stable ties.

The queue contains a private `(chunk, score)` value, so `SearchResult` objects are created only for final results.

### Edge and failure behavior

- Blank queries and queries containing only one-character terms return an immutable empty list.
- `topK <= 0` returns an immutable empty list.
- Null content is tolerated defensively even though the schema marks it non-null.
- Empty mapper results return an immutable empty list.
- Database failures continue to propagate to `HybridSearchService`, which already catches fallback failures and degrades safely.

## Verification

1. A service unit test must fail before implementation because the optimized mapper method does not exist or is not used.
2. Unit tests verify one mapper call, no legacy full-table API use, active results supplied by the mapper, exact ranking, deterministic tie behavior, and no interaction for invalid inputs.
3. A mapper integration test builds a real MyBatis-Plus session over H2, inserts active, inactive, unrelated, and nonmatching rows, and proves that the annotated SQL returns only active matching rows.
4. Run the focused tests, the complete unit-test lifecycle, Maven `verify` without Testcontainers, `git diff --check`, and targeted scans for the removed N+1/full-scan pattern.

## Scope Boundary

This slice does not introduce database-specific full-text indexes, change RRF/reranker behavior, alter authorization, or modify ingestion activation semantics. Those require separate evidence and plans.
