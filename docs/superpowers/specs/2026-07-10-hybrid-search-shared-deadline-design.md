# Hybrid Search Shared Deadline Design

## Problem

`HybridSearchService` starts pgvector and Elasticsearch searches concurrently, but waits for each future with the full `backendTimeoutMillis`. When both backends stall, the waits accumulate and the request can take almost twice the configured backend budget.

## Selected Design

Create one absolute deadline immediately after both backend tasks are submitted. `awaitResults` receives that deadline and waits only for the remaining nanoseconds. A task that already completed is still read even when the deadline has elapsed; an incomplete task is cancelled and degrades to an empty result.

Use executor-backed `FutureTask` instances instead of `CompletableFuture.supplyAsync`. `FutureTask.cancel(true)` retains a handle to the actual worker and can interrupt a blocked backend; `CompletableFuture.cancel(true)` only changes the completion state. Caller interruption is restored and cancels both tasks without entering the synchronous fallback. Executor rejection cancels any partial submission and reuses the existing empty-result fallback path.

This keeps the current partial-result and RRF behavior, avoids mutating completion stages with `orTimeout`, and does not let one exceptional backend short-circuit the other.

## Success Criteria

- Two blocked backends consume one configured timeout budget, not two.
- A backend completed before the deadline is still consumed after the other backend times out.
- Timed-out backends receive a best-effort interrupt so retrieval threads can be released.
- Caller interruption is preserved, cancels both backend tasks, and skips fallback work.
- Bounded-executor rejection cannot leak a partially submitted task or escape as an API error.
- Timeout, exception, fallback, and fusion behavior remain unchanged.
- The existing named retrieval executor remains the only async boundary.
- A red-green timing regression test uses a wide enough margin to distinguish one budget from two.

## Verification

Run `HybridSearchServiceTest`, all retrieval tests, the full unit suite, and Maven `verify` without Testcontainers. Scan the implementation to confirm both waits receive the same deadline and all cancellation paths target the underlying `FutureTask` values.
