# Distributed Search System — v2

![CI](https://github.com/amgharhind/distributed-search-v2/actions/workflows/build.yml/badge.svg)

A production-grade distributed search platform combining **BM25 lexical retrieval** with **neural semantic re-ranking**, a **Redis cache layer**, a **dark-themed SPA frontend**, and full **Docker Compose** orchestration. One command starts the entire stack.

---

## What's new in v2

| Area | v1 | v2 |
|---|---|---|
| Elasticsearch | 7.12 (EOL) · `RestHighLevelClient` (deprecated) | **8.13** · new `ElasticsearchClient` |
| Re-ranking service | Flask (single-threaded) · re-fetches documents from ES | **FastAPI + Gunicorn** · receives docs directly |
| ML model | `distilbert-base-nli-stsb-mean-tokens` (deprecated) | **`all-MiniLM-L6-v2`** — 5× faster, higher MTEB score |
| Scoring | BM25 only | **Hybrid: 20 % BM25 + 80 % cosine similarity** |
| HTTP client | `RestTemplate` (deprecated) | **`WebClient`** (non-blocking) |
| Re-ranker resilience | Hard crash on failure | **Resilience4j circuit breaker** → graceful BM25 fallback |
| Redis safety | `KEYS *` (blocks Redis) | **`SCAN` cursor** (non-blocking) |
| Service URL | Hardcoded `localhost:5000` | **`RERANKING_SERVICE_URL` env-var** |
| API docs | None | **Swagger UI** at `/swagger-ui.html` |
| Metrics | None | **Prometheus + Actuator + Grafana** dashboard |
| Frontend | None | **Dark SPA** with search, cache manager, document CRUD, live stats, side-by-side re-ranking comparison |
| Document ingestion | Manual JSON only | **File upload** (PDF, DOCX, TXT, CSV, JSON, MD…) with text extraction |
| Deployment | 4 separate manual steps | **Single `docker compose up`** |

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│                    Browser                        │
└─────────────────────┬────────────────────────────┘
                      │ HTTP :3000
                      ▼
┌──────────────────────────────────────────────────┐
│              Nginx  (frontend SPA)                │
│  /api/       → proxy → search-api:8080            │
│  /actuator/  → proxy → search-api:8080            │
│  /reranker/  → proxy → reranker:8001              │
│  /es/        → proxy → es01:9200                  │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│         Spring Boot Search API  :8080             │
│                                                   │
│  SearchController ──► SearchService               │
│  DocumentController ─► DocumentService            │
│  CacheController  ──► CacheService                │
│         │                   │  BM25 query         │
│         │                   ▼                     │
│         │        ┌─────────────────┐              │
│         │        │  ES 8.x Cluster │  2 nodes     │
│         │        │   es01  es02    │              │
│         │        └────────┬────────┘              │
│         │                 │ top-50 docs            │
│         │                 ▼                        │
│         │        RerankingService                  │
│         │        (Resilience4j CB)                 │
│         │                 │                        │
│         │                 ▼                        │
│         │        ┌─────────────────┐              │
│         │        │  FastAPI :8001   │             │
│         │        │  all-MiniLM-L6  │             │
│         │        │  20% BM25       │             │
│         │        │  80% cosine     │             │
│         │        └────────┬────────┘              │
│         ▼                 ▼                        │
│   CacheService ◄── final ranked results            │
│   (Redis SCAN)                                    │
└──────────────────────────────────────────────────┘
          │                       │
   ┌──────┴──────┐        ┌───────┴──────┐
   │    Redis    │        │  Prometheus  │
   │   :6379     │        │  /actuator   │
   └─────────────┘        └─────────────┘
```

---

## Quick start

```bash
git clone https://github.com/amgharhind/distributed-search-v2.git
cd distributed-search-v2
docker compose up -d --build
```

All services start in dependency order (health-checked). Elasticsearch takes ~2 min on first boot.

### Load sample documents

```bash
python scripts/load-sample-data.py
```

Loads 15 documents designed to demonstrate re-ranking, caching, and distributed search value.

---

## Service URLs

| Service | URL |
|---|---|
| **Frontend SPA** | http://localhost:3000 |
| Search API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Health check | http://localhost:8080/actuator/health |
| Re-ranking service docs | http://localhost:8001/docs |
| Elasticsearch | http://localhost:9200 |
| **Prometheus** | http://localhost:9090 |
| **Grafana dashboards** | http://localhost:3001 (admin / admin) |

---

## Docker commands

### Start / stop

```bash
# Start all services in background
docker compose up -d --build

# Stop all services (keeps data)
docker compose down

# Stop and wipe all volumes (deletes ES index and Redis cache)
docker compose down -v
```

### Rebuild individual services

```bash
# Java backend changed (controllers, services, pom.xml)
docker compose up -d --build search-api

# Python re-ranker changed (reranker.py, models.py, requirements)
docker compose up -d --build reranker

# Frontend changed (index.html, nginx.conf)
# No rebuild needed — frontend is volume-mounted, changes are instant
# Just reload the browser.
```

### Inspect logs

```bash
docker logs search-api --tail 50 -f
docker logs reranker   --tail 50 -f
docker logs es01       --tail 50 -f
docker logs redis      --tail 50 -f
```

### Check container health

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

---

## API reference

### Search

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v2/search` | Full-text search with caching and re-ranking |
| `GET` | `/api/v2/search/wildcard` | Wildcard search (`pattern=distrib*`) |
| `GET` | `/api/v2/search/exact-phrase` | Exact phrase match |
| `GET` | `/api/v2/search/proximity` | Proximity / slop search |
| `GET` | `/api/v2/search/range` | Range search on date or numeric field |
| `POST` | `/api/v2/search/interaction/{id}` | Record a document click |

**Full-text search parameters**

| Param | Default | Description |
|---|---|---|
| `query` | — | Search terms |
| `field` | `content` | Field to search |
| `fileType` | — | Filter by file type (`pdf`, `docx`, `txt`…) |
| `sortField` | — | Sort by field (`createdDate`, `title`…) |
| `sortOrder` | `desc` | `asc` or `desc` |
| `page` | `0` | Page number (0-indexed) |
| `size` | `10` | Results per page |
| `cache` | `true` | Use Redis cache |
| `rerank` | `true` | Apply semantic re-ranking |

### Documents

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v2/documents` | Index a document from JSON body |
| `POST` | `/api/v2/documents/bulk` | Bulk-index multiple documents |
| `POST` | `/api/v2/documents/upload` | Upload a file (PDF, DOCX, TXT, CSV, MD…) — text extracted automatically |
| `GET` | `/api/v2/documents` | List documents with pagination |
| `GET` | `/api/v2/documents/{id}` | Get document by ID |
| `PUT` | `/api/v2/documents/{id}` | Update a document |
| `DELETE` | `/api/v2/documents/{id}` | Delete a document |

**Upload example**

```bash
curl -X POST http://localhost:8080/api/v2/documents/upload \
  -F "file=@report.pdf" \
  -F "author=Jane Doe" \
  -F "title=Q4 Report"
```

### Cache

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v2/cache/keys` | List all cached keys (safe SCAN) |
| `DELETE` | `/api/v2/cache?key=…` | Evict a specific cache key |
| `DELETE` | `/api/v2/cache/all` | Clear entire cache |

---

## How the hybrid scoring works

```
final_score = 0.05 × norm_bm25 + 0.95 × cosine_similarity
```

1. **BM25** (Elasticsearch) retrieves the top-50 candidates using the inverted index — fast (~5 ms)
2. **all-MiniLM-L6-v2** encodes the query and all 50 candidates into 384-dimensional vectors
3. **Cosine similarity** measures semantic closeness between query and document embeddings
4. **Hybrid score** blends both signals — BM25 provides recall for exact-match queries, cosine dominates relevance ranking
5. Results are **sorted by final score** and cached in Redis for subsequent identical queries

The 95 % semantic weight means the re-ranker visibly reorders results for vocabulary-mismatch queries — documents conceptually relevant but lacking exact query keywords will rise significantly in rank. Try the "fast document retrieval" demo query with re-rank toggled on and off to see a live BM25-vs-semantic side-by-side comparison in the frontend.

If the re-ranking service is unavailable, the **Resilience4j circuit breaker** opens immediately and returns BM25 order — the search endpoint never fails.

---

## Demo queries

Run these in the **Search tab** of the frontend to see each system feature:

| Query | What it shows |
|---|---|
| `fast document retrieval` | Toggle Re-rank off/on — the keyword-stuffed doc drops from #1 |
| `how does semantic search work` | BM25 misses conceptual matches; re-ranker surfaces them |
| `cache latency performance` | Run twice — second response shows ⚡ Cached badge and near-0 ms |
| `circuit breaker microservices` | Finds resilience docs via semantic similarity, not exact keywords |
| `distributed search production` | Broad query — shows full corpus coverage and score bars |

---

## CI / CD

GitHub Actions runs on every push to `master`:

- **Java job** — `mvn test` inside `search-api/`
- **Python job** — `pytest tests/ -v` with lightweight mocks (no PyTorch in CI)

Badge at the top of this file reflects the latest build status.

---

## Tech stack

| Layer | Technology |
|---|---|
| Core API | Spring Boot 3.2 · Java 21 |
| Search engine | Elasticsearch 8.13 (2-node cluster) |
| Re-ranking | FastAPI · Gunicorn · sentence-transformers |
| ML model | `all-MiniLM-L6-v2` |
| Cache | Redis 7.2 |
| Resilience | Resilience4j circuit breaker |
| HTTP client | Spring WebClient |
| File extraction | Apache PDFBox 3 · Apache POI |
| API docs | Springdoc OpenAPI (Swagger UI) |
| Metrics | Micrometer · Prometheus · Grafana |
| Frontend | Vanilla JS SPA · nginx reverse proxy |
| Containerisation | Docker · Docker Compose (8 services) |
| CI | GitHub Actions |

---

## Project structure

```
distributed-search-v2/
├── docker-compose.yml              # Full-stack orchestration (8 services)
├── .env.example                    # Environment variable template (copy → .env)
├── .github/
│   └── workflows/build.yml         # CI — Java + Python parallel jobs
├── prometheus/
│   └── prometheus.yml              # Prometheus scrape config
├── grafana/
│   └── provisioning/               # Auto-provisioned datasource + dashboard
├── frontend/
│   ├── index.html                  # Dark SPA (search, stats, side-by-side, cache, documents)
│   └── nginx.conf                  # Reverse proxy config
├── scripts/
│   ├── load-sample-data.py         # 15 demo documents via bulk API
│   └── fix-demo-doc.py             # Replaces BM25-weakness demo document
├── search-api/                     # Spring Boot application
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/search/distributed/
│       ├── config/                 # ES, Redis, WebClient beans
│       ├── controller/             # SearchController, CacheController, DocumentController
│       ├── service/                # SearchService, RerankingService, CacheService,
│       │                           # DocumentService, InteractionService
│       ├── model/                  # Document, SearchResult, RerankRequest, DocumentRequest
│       └── exception/              # GlobalExceptionHandler
└── reranking-service/              # FastAPI re-ranking microservice
    ├── Dockerfile
    ├── gunicorn.conf.py
    ├── requirements.txt
    ├── requirements-dev.txt        # Lightweight deps for CI (no PyTorch)
    ├── tests/                      # pytest — 6 API tests with mocked model
    └── app/
        ├── main.py                 # FastAPI app + /health + /re-rank
        ├── reranker.py             # Async hybrid scoring (20% BM25 + 80% cosine)
        └── models.py               # Pydantic request/response models
```

---

## Compared to v1

The original v1 implementation had these production blockers — all fixed in v2:

- `http://localhost:5000` hardcoded → breaks inside Docker containers
- `KEYS *` Redis command → blocks the entire Redis instance under load
- Flask dev server → single-threaded, queues under concurrent searches
- Re-ranking service re-fetched documents from ES → doubled the ES load
- No docker-compose for Spring Boot or Flask → four manual startup steps
- Deprecated `distilbert-base-nli-stsb-mean-tokens` model
- No fallback when re-ranker was down → entire search request failed
- No frontend — API-only, required curl or Postman for every interaction

---

## License

MIT
