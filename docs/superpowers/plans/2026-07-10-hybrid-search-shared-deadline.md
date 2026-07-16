# Hybrid Search Shared Deadline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce one end-to-end timeout budget across both parallel retrieval backends.

**Architecture:** Submit two `FutureTask` values to the named retrieval executor, compute one `System.nanoTime()` deadline, and pass it to both waits. Each wait uses only the remaining nanoseconds; timeout, caller interruption, and executor rejection cancel the real worker tasks while preserving completed results, fallback, and fusion semantics.

**Tech Stack:** Java 17 source level on JDK 22, `FutureTask`, JUnit 5, Mockito.

## Global Constraints

- Preserve unrelated dirty-worktree changes.
- Do not replace the named retrieval executor or alter backend candidate sizes.
- Follow red-green-refactor and set `JAVA_HOME=C:\Program Files\Java\jdk-22` for Maven.
- Do not commit from the dirty main checkout.

---

### Task 1: Make the timeout a shared deadline

**Files:**
- Modify: `src/test/java/com/example/evimind/retrieval/HybridSearchServiceTest.java`
- Modify: `src/main/java/com/example/evimind/retrieval/HybridSearchService.java`

**Interfaces:**
- Consumes: existing `backendTimeoutMillis` and two executor-backed `FutureTask<List<SearchResult>>` values.
- Produces: `awaitResults(String, Future<List<SearchResult>>, long deadlineNanos)`.

- [ ] **Step 1: Write the failing latency regression test**

Configure both mocked backends to sleep for one second, set the backend timeout to 200 ms, run `search`, and assert the empty result returns in less than 330 ms. The current code takes about 400 ms because both waits receive 200 ms.

- [ ] **Step 2: Run the test and verify RED**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs "-Dtest=HybridSearchServiceTest#shouldShareOneTimeoutBudgetAcrossBothBackends" test
```

Expected: the elapsed-time assertion fails around 400 ms.

- [ ] **Step 3: Implement one deadline**

After submitting both `FutureTask` values, compute:

```java
long deadlineNanos =
    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, backendTimeoutMillis));
```

Pass it to both `awaitResults` calls. Inside the helper, compute `remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime())` and call `future.get(remainingNanos, TimeUnit.NANOSECONDS)`.

- [ ] **Step 4: Prove real worker cancellation and caller interruption**

Add regression tests that require timed-out backend tasks to observe interruption, require caller interruption to return promptly with the interrupt flag restored, and verify synchronous fallback is skipped for an interrupted caller.

- [ ] **Step 5: Degrade cleanly when the bounded executor rejects work**

Add a regression test where the first backend starts and the second submission is rejected. Cancel both tasks and continue through the existing fallback path without propagating `RejectedExecutionException`.

- [ ] **Step 6: Run all hybrid-search tests and verify GREEN**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -DskipITs "-Dtest=HybridSearchServiceTest" test
```

Expected: all tests pass and Maven reports `BUILD SUCCESS`.

### Task 2: Re-run combined retrieval and repository gates

**Files:**
- Inspect: all files modified by both performance slices.

**Interfaces:**
- Consumes: optimized fallback query and shared hybrid-search deadline.
- Produces: fresh verification evidence and a bounded final report.

- [ ] **Step 1: Run all retrieval tests**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs "-Dtest=com.example.evimind.retrieval.*Test,DocumentChunkMapperSqlTest" test
```

- [ ] **Step 2: Run the full local gates**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\mvnw.cmd -q -DskipITs test
.\mvnw.cmd -q -DskipITs verify
git diff --check
```

- [ ] **Step 3: Inspect exact diffs and performance invariants**

Confirm the fallback service contains no `DocumentMapper`, `selectList`, or `selectById`; confirm both `awaitResults` calls receive the same `deadlineNanos`; and confirm the H2 mapper test covers active-version, knowledge-base, nonmatch, and wildcard-literal filtering.
