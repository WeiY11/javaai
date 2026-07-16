# Security Notes

## Authentication And Authorization

Business services must fail closed when `GroupContext.getUserId()` is missing. Document, knowledge-base, RAG, conversation, and document-permission paths now throw authentication errors instead of silently returning.

Knowledge-base membership is still the coarse document access guard. Document-level permissions are present and owner grants are created at upload time when the permission service is enabled.

## Upload Safety

Uploaded object paths no longer use the user supplied filename. Storage keys use:

`knowledgeBaseId/UUID/UUID.ext`

The original filename is kept only for display metadata. Upload validation rejects empty files, unsupported extensions, path-traversal filenames, oversize files, and common extension/MIME mismatches.

## Production Guardrails Still Needed

Swagger, the H2 console, detailed runtime checks, and actuator metrics require the `ADMIN` role. `/actuator/health` and `/actuator/info` remain public, while health component details require `ADMIN`.

The default Docker Compose stack binds backend and infrastructure ports to `127.0.0.1`. It explicitly permits local Prometheus scraping; non-Compose deployments retain administrator-only metrics access.

Production still needs profile-level validation for the H2 console, JWT secret strength, and database credentials.
