# EviMind

EviMind is an evidence-grounded RAG workspace for document ingestion, hybrid retrieval, cited answers, research notes, citation export, and batch document analysis.

The current workspace path is `D:\EviMind`. There is no built-in default account. Open the login page, register a user first, then log in with that account.

## Quick Start

### Option 1: Development Mode

Use this mode for local development and demos. The backend runs with the `standalone` profile, using H2 and local file storage. PostgreSQL, Elasticsearch, and MinIO are not required.

Start the backend in PowerShell:

```powershell
cd D:\EviMind
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
$env:DEEPSEEK_API_KEY = "your-key"   # Optional. AI chat is limited when unset.
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=standalone"
```

Health probes:

```text
http://localhost:8080/actuator/health
```

The detailed runtime diagnosis at `/api/v1/health` is available only to administrators after login.

Start the frontend in another PowerShell window:

```powershell
cd D:\EviMind\frontend
npm install
npm run dev -- --host 127.0.0.1
```

Open:

```text
http://127.0.0.1:5173
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

### Option 2: Single JAR

Use this mode for a local handoff or offline demo. The frontend build is copied into Spring Boot static resources.

```powershell
cd D:\EviMind
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
$env:DEEPSEEK_API_KEY = "your-key"   # Optional
.\build.bat
.\start.bat
```

Open:

```text
http://localhost:8080
```

Linux/macOS:

```bash
cd /path/to/EviMind
export DEEPSEEK_API_KEY=your-key      # Optional
./build.sh
./start.sh
```

### Option 3: Docker Compose

Use this mode for a full local stack with PostgreSQL, pgvector, Elasticsearch, MinIO, backend, and frontend containers.

```powershell
cd D:\EviMind
copy .env.example .env
docker compose up -d
```

Populate the required database, object-storage, and JWT secrets in `.env` before starting Docker Compose. The stack refuses to start when they are absent. Backend and infrastructure ports bind to `127.0.0.1`; the frontend remains available on port `5173`.

## Core Workflow

```text
Upload document
  -> UPLOADED
  -> extract text
  -> clean text
  -> split chunks
  -> persist versioned chunks
  -> create embeddings and keyword index
  -> enrich metadata/citations/knowledge graph
  -> atomically activate the completed ingestion version
  -> ask a question
  -> hybrid retrieval
  -> RRF fusion
  -> LLM answer with cited evidence
```

## Features

| Area | Capability |
| --- | --- |
| Knowledge bases | Create, edit, delete, membership control, evidence threshold, chunking configuration |
| Documents | Batch upload, drag-and-drop upload, PDF/Word/Excel/CSV/JSON/Markdown/Text support |
| ETL | Extract, clean, split, embed, index, retry failed ingestion |
| Retrieval | pgvector semantic search, Elasticsearch keyword search, RRF fusion, local keyword fallback |
| RAG chat | SSE streaming, source citations, chunk numbers, relevance scores |
| Model providers | DeepSeek, GLM-4, Qianwen, OpenAI-style runtime switching |
| Analysis | Single-file and batch document analysis, Markdown/PDF report export |
| Research support | Paper metadata extraction, BibTeX/APA citation export, research notes |

## API Summary

All paths below are under `/api/v1`.

| Module | Main endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me` |
| Knowledge bases | `GET /knowledge-bases`, `POST /knowledge-bases`, `PUT /knowledge-bases/{id}`, `DELETE /knowledge-bases/{id}` |
| Documents | `POST /documents/upload`, `GET /documents`, `GET /documents/{id}`, `GET /documents/{id}/chunks`, `POST /documents/{id}/retry` |
| Conversations | `GET /conversations`, `POST /conversations`, `POST /conversations/{id}/messages/stream`, `GET /conversations/{id}/export` |
| Citations | `POST /citations/export` |
| Notes | `GET /notes`, `POST /notes`, `PUT /notes/{id}`, `DELETE /notes/{id}` |
| Analysis | Batch document analysis and progress APIs |

Swagger UI is available when the backend is running:

```text
http://localhost:8080/swagger-ui/index.html
```

## Engineering Notes

- Architecture: `docs/architecture.md`
- Security: `docs/security.md`
- RAG evaluation plan: `docs/rag-evaluation.md`
- Current change log: `CHANGELOG.md`

## Configuration

| Variable | Standalone | Docker/production | Notes |
| --- | --- | --- | --- |
| `DEEPSEEK_API_KEY` | Optional | Recommended | AI chat is limited when unset |
| `ZHIPU_API_KEY` | Optional | Optional | GLM provider |
| `QIANWEN_API_KEY` | Optional | Optional | Qianwen provider |
| `OPENAI_API_KEY` | Optional | Optional | OpenAI-compatible provider |
| `JWT_SECRET` | Required | Required | At least 32 characters; no bundled development key outside local profiles |
| `PORT` | Optional | Optional | Defaults to `8080` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | Optional | Required when frontend origin changes | Example: `https://example.com,http://localhost:5173` |
| `EMBEDDING_ENABLED` | Optional | Recommended | Defaults depend on profile |
| `EMBEDDING_API_KEY` | Optional | Required when embeddings use an external provider | |
| `EXTRACTOR_OCR_ENABLED` | Optional | Optional | Set to `true` to OCR image files and scanned PDFs |
| `EXTRACTOR_OCR_TESSDATA_PATH` | Optional | Required when OCR is enabled | Tesseract language data directory |
| `EXTRACTOR_OCR_LANGUAGE` | Optional | Optional | Defaults to `chi_sim+eng` |
| `EXTRACTOR_PDF_MIN_NATIVE_TEXT_CHARS` | Optional | Optional | Defaults to `24`; below this threshold scanned PDFs use OCR or fail explicitly |
| `EXTRACTOR_OCR_PDF_DPI` | Optional | Optional | Defaults to `220`; higher values improve OCR at higher CPU cost |
| `EXTRACTOR_OCR_PDF_MAX_PAGES` | Optional | Optional | Defaults to `20`; bounds scanned-PDF OCR work |
| `POSTGRES_*` | Not needed | Required | Docker/production database |
| `MINIO_*` | Not needed | Required | Docker/production object storage |
| `GRAFANA_ADMIN_USER` | Optional | Required for `docker-compose.grafana.yml` | Monitoring administrator username |
| `GRAFANA_ADMIN_PASSWORD` | Optional | Required for `docker-compose.grafana.yml` | Monitoring administrator password |

Standalone data files:

```text
data\evimind-standalone.mv.db
data\documents
```

## Tech Stack

Backend: Spring Boot 3.5, Spring AI 1.0, MyBatis-Plus, Spring Security, JWT, Flyway, PostgreSQL/pgvector, Elasticsearch, MinIO, PDFBox, Apache POI, Tess4J, OpenPDF.

Frontend: Vue 3, TypeScript, Vite, Element Plus, Pinia, Markdown-It, Axios, SSE streaming.

## Project Structure

```text
EviMind/
  src/main/java/com/example/evimind/
    assistant/       Conversation and streaming chat
    auth/            JWT authentication
    config/          AI, security, storage, search configuration
    controller/      Analysis, file, citation, note APIs
    document/        Document upload and management
    extractor/       PDF, Word, Excel extraction and paper metadata
    ingestion/       ETL pipeline
    knowledgebase/   Knowledge base CRUD and permissions
    qa/              RAG pipeline and evidence selection
    retrieval/       Hybrid search, RRF, local fallback
    service/         Business services
    storage/         MinIO and local storage
  frontend/          Vue frontend
  docker-compose.yml Production-like stack
  build.bat/.sh      Build single-JAR distribution
  start.bat/.sh      Start standalone single-JAR service
  PROJECT_OVERVIEW.md Detailed project notes
```

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `JAVA_HOME not found` | Set `JAVA_HOME` to a local JDK 21+ path. |
| Frontend API calls fail in dev mode | Start the backend first and confirm `http://localhost:8080/actuator/health`. |
| AI chat returns limited output | Set `DEEPSEEK_API_KEY` or another configured provider key. |
| Scanned PDFs fail during extraction | Set `EXTRACTOR_OCR_ENABLED=true`, install Tesseract language data, and point `EXTRACTOR_OCR_TESSDATA_PATH` to that directory. |
| Browser cannot access backend after deployment | Set `CORS_ALLOWED_ORIGIN_PATTERNS` to the frontend origin. |
| Need a clean standalone database | Stop the app, back up `data\evimind-standalone.mv.db`, then remove it. |

## License

MIT
