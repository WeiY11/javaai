# Standalone Keyword Search Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the standalone keyword-search N+1 query and unbounded result sorting while preserving search semantics.

**Architecture:** Move active-version filtering and term preselection into one parameterized MyBatis query. Keep the current Java score formula, but select final results through a priority queue bounded by `topK`.

**Tech Stack:** Java 17 source level on JDK 22, Spring Boot, MyBatis-Plus 3.5.12, H2, JUnit 5, Mockito.

## Global Constraints

- Do not overwrite or revert unrelated dirty-worktree changes.
- Do not add a database-specific search dependency or migration.
- Keep SQL valid for PostgreSQL and H2 PostgreSQL mode.
- Follow strict red-green-refactor: every production behavior change must first have a test that fails for the expected reason.
- Set `JAVA_HOME=C:\Program Files\Java\jdk-22` for every Maven command.
- Do not create a commit from the dirty worktree because touched files already contain user changes.

---

### Task 1: Prove the mapper query contract

**Files:**
- Create: `src/test/java/com/example/evimind/mapper/DocumentChunkMapperSqlTest.java`
- Modify: `src/main/java/com/example/evimind/mapper/DocumentChunkMapper.java`

**Interfaces:**
- Consumes: `DocumentChunk` column mapping and the existing `document` / `document_chunk` schema.
- Produces: `List<DocumentChunk> findActiveContainingAnyTerm(Long knowledgeBaseId, List<String> terms)`.

- [ ] **Step 1: Write the failing H2 mapper integration test**

Build a MyBatis-Plus `SqlSessionFactory` over a unique in-memory H2 database. Create minimal `document` and `document_chunk` tables, insert an active matching chunk, inactive matching chunk, other-KB matching chunk, and active nonmatching chunk, then call:

```java
List<DocumentChunk> results =
    mapper.findActiveContainingAnyTerm(7L, List.of("alpha", "beta"));
assertEquals(List.of(101L), results.stream().map(DocumentChunk::getId).toList());
```

- [ ] **Step 2: Run the mapper test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs "-Dtest=DocumentChunkMapperSqlTest" test
```

Expected: test compilation fails because `findActiveContainingAnyTerm` is absent.

- [ ] **Step 3: Add the minimal parameterized mapper query**

Add this signature and an annotation-based MyBatis script:

```java
List<DocumentChunk> findActiveContainingAnyTerm(
    @Param("knowledgeBaseId") Long knowledgeBaseId,
    @Param("terms") List<String> terms);
```

The SQL selects `dc.*`, joins `document d` on `d.id = dc.document_id` and `d.active_ingestion_version = dc.ingestion_version`, filters `dc.knowledge_base_id`, and emits one parameterized `LOWER(dc.content) LIKE CONCAT('%', #{term}, '%')` predicate per term through `<foreach>`.

- [ ] **Step 4: Run the mapper test and verify GREEN**

Run the command from Step 2. Expected: one test passes with no SQL error.

### Task 2: Replace full scan, N+1 lookups, and full sorting

**Files:**
- Modify: `src/test/java/com/example/evimind/retrieval/SimpleKeywordSearchServiceTest.java`
- Modify: `src/main/java/com/example/evimind/retrieval/SimpleKeywordSearchService.java`

**Interfaces:**
- Consumes: `DocumentChunkMapper.findActiveContainingAnyTerm(Long, List<String>)` from Task 1.
- Produces: unchanged `List<SearchResult> search(String query, Long knowledgeBaseId, int topK)` behavior with one mapper call and `O(topK)` selection memory.

- [ ] **Step 1: Write failing service tests**

Add tests that:

```java
DocumentChunk first = chunk(1L, 11L, "alpha alpha beta");
DocumentChunk second = chunk(2L, 12L, "alpha beta");
DocumentChunk third = chunk(3L, 13L, "beta");
when(mapper.findActiveContainingAnyTerm(7L, List.of("alpha", "beta")))
    .thenReturn(List.of(first, second, third));
List<SearchResult> results = service.search("Alpha beta", 7L, 2);
verify(mapper).findActiveContainingAnyTerm(7L, List.of("alpha", "beta"));
verify(mapper, never()).selectList(any());
assertEquals(2, results.size());
assertTrue(results.get(0).getScore() >= results.get(1).getScore());

private static DocumentChunk chunk(Long id, Long documentId, String content) {
  DocumentChunk chunk = new DocumentChunk();
  chunk.setId(id);
  chunk.setDocumentId(documentId);
  chunk.setKnowledgeBaseId(7L);
  chunk.setChunkIndex(Math.toIntExact(id));
  chunk.setContent(content);
  return chunk;
}
```

Also verify `topK == 0` returns empty without mapper interaction and equal scores are ordered by chunk ID ascending.

- [ ] **Step 2: Run service tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs "-Dtest=SimpleKeywordSearchServiceTest" test
```

Expected: the new interaction/ranking tests fail because production still calls `selectList` and `DocumentMapper.selectById` and does not define deterministic bounded selection.

- [ ] **Step 3: Implement the bounded Top-K path**

Remove `DocumentMapper`. Call `findActiveContainingAnyTerm` once. Extract the existing term-count score calculation into a private method. Maintain a `PriorityQueue<ScoredChunk>` bounded by `topK`, using score ascending and chunk ID descending for the weakest-first heap; sort selected values by score descending and chunk ID ascending before mapping them to `SearchResult`.

- [ ] **Step 4: Run focused mapper and service tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs "-Dtest=DocumentChunkMapperSqlTest,SimpleKeywordSearchServiceTest" test
```

Expected: all focused tests pass.

### Task 3: Verify performance invariants and repository gates

**Files:**
- Inspect: `src/main/java/com/example/evimind/retrieval/SimpleKeywordSearchService.java`
- Inspect: `src/main/java/com/example/evimind/mapper/DocumentChunkMapper.java`
- Inspect: current worktree diff only; do not modify unrelated files.

**Interfaces:**
- Consumes: completed Task 1 and Task 2 behavior.
- Produces: fresh proof for the optimized path and its regression boundary.

- [ ] **Step 1: Run focused tests with normal Maven output**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -DskipITs "-Dtest=DocumentChunkMapperSqlTest,SimpleKeywordSearchServiceTest" test
```

Expected: Maven reports `BUILD SUCCESS`, zero failures, and zero errors.

- [ ] **Step 2: Run all unit tests and quality gates**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs test
.\mvnw.cmd -q -DskipITs verify
```

Expected: both commands exit 0. If a pre-existing gate fails, isolate whether the changed path caused it and report the exact boundary.

- [ ] **Step 3: Prove the old hot-path patterns are absent**

```powershell
rg -n "documentMapper|selectList\(|selectById\(" src/main/java/com/example/evimind/retrieval/SimpleKeywordSearchService.java
rg -n "findActiveContainingAnyTerm|PriorityQueue" src/main/java/com/example/evimind/retrieval/SimpleKeywordSearchService.java src/main/java/com/example/evimind/mapper/DocumentChunkMapper.java
```

Expected: the first command returns no matches; the second finds the new single-query and bounded-selection path.

- [ ] **Step 4: Check formatting and diff hygiene**

```powershell
git diff --check
git diff -- src/main/java/com/example/evimind/mapper/DocumentChunkMapper.java src/main/java/com/example/evimind/retrieval/SimpleKeywordSearchService.java src/test/java/com/example/evimind/mapper/DocumentChunkMapperSqlTest.java src/test/java/com/example/evimind/retrieval/SimpleKeywordSearchServiceTest.java
```

Expected: no whitespace errors; diff contains only the planned performance slice on top of existing user changes.
