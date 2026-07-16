# Roadmap Implementation: S1 Foundation + S2.1 Agent Wiring

This note records the first implementation slice from the roadmap.

## Completed

Quality gates:

- CI now runs `./mvnw -B verify`; integration tests are no longer skipped by default.
- Spotless no longer uses `continue-on-error`.
- Maven Enforcer blocks dependency convergence and upper-bound drift.
- JaCoCo now has bundle and package-level ratchet rules.
- PMD now fails the build with a critical correctness/security ruleset.

Integration test profile:

- `application-test.yml` disables external AI calls and configures deterministic test defaults.
- `BaseIntegrationTest` starts PostgreSQL, Elasticsearch, and MinIO through Testcontainers.
- Elasticsearch URI parsing preserves dynamic `host:port` values from Testcontainers.

Runtime hardening:

- `GlobalExceptionHandler` now handles validation, malformed JSON, upload-size, access-denied, illegal-argument, and runtime exceptions with stable `ErrorCode` responses.
- Runtime errors no longer echo internal exception messages to API callers.
- `RagPipeline` uses constructor injection.
- RAG stream search failures no longer leak raw exception text.
- PDF export closes the underlying output stream and document.
- Simple keyword search avoids assignment in loop conditions.

Agent activation:

- `AiConfig` registers `FunctionCallbackContext`.
- Chat clients are built with `kbSearch`, `listDirectory`, `readFileContent`, and `queryAnalysisHistory` as default function names.
- A regression test verifies these function names are attached to the default ChatClient request.

Scheduler:

- `TaskSchedulerService.calculateNextRun` now parses real cron expressions with Spring `CronExpression`.

## Current Gate Results

Verified locally:

```powershell
.\mvnw.cmd -q -DskipITs verify
```

Result: passed.

Full gate attempted:

```powershell
.\mvnw.cmd -q verify
```

Result: blocked by local environment. Testcontainers entered Failsafe, but this machine has no `docker` command on `PATH`, so `AuthDocumentIntegrationTest` failed before containers could start.

## Next Ratchet

- Run `.\mvnw.cmd verify` on a machine with Docker Desktop.
- If Testcontainers reaches service startup, verify PostgreSQL, Elasticsearch, and MinIO connectivity end to end.
- Raise bundle coverage after the next focused service-test batch.
- Continue S2 with an explicit AgentExecutor loop, trace persistence, and SSE `agent_step` events.
