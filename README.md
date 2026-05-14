# Distributed Search System — v2

A production-grade distributed search platform that combines **BM25 lexical retrieval** with **semantic re-ranking** via a dedicated ML microservice. One `docker compose up` starts the full stack.

---

## What's new in v2

| Area | v1 | v2 |
|---|---|---|
| Elasticsearch | 7.12 (EOL) · `RestHighLevelClient` (deprecated) | **8.13** · new `ElasticsearchClient` |
| Re-ranking service | Flask (single-threaded) · re-fetches documents from ES | **FastAPI + Gunicorn** · receives docs directly, no redundant ES call |
| ML model | `distilbert-base-nli-stsb-mean-tokens` (deprecated) | **`all-MiniLM-L6-v2`** — 5× faster, higher MTEB score |
| Scoring | BM25 only | **Hybrid: 40 % BM25 + 60 % cosine similarity** |
| HTTP client | `RestTemplate` (deprecated) | **`WebClient`** (non-blocking) |
| Re-ranker resilience | Hard crash on failure | **Resilience4j circuit breaker** → graceful fallback to BM25 order |
| Redis safety | `KEYS *` (blocks Redis) | **`SCAN` cursor** (non-blocking) |
| Service URL | Hardcoded `localhost:5000` | **`RERANKING_SERVICE_URL` env-var** |
| API docs | None | **Swagger UI** at `/swagger-ui.html` |
| Metrics | None | **Prometheus + Actuator** at `/actuator/prometheus` |
| Deployment | 4 separate manual steps | **Single `docker compose up`** |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTP
                        ▼
┌─────────────────────────────────────────────────────────┐
│            Spring Boot Search API  :8080                │
│                                                         │
│  SearchController  ──►  SearchService                   │
│       │                     │  BM25 query               │
│       │                     ▼                           │
│       │           ┌─────────────────┐                   │
│       │           │ ES 8.x Cluster  │  3 nodes          │
│       │           │ es01 es02 es03  │  dense_vector      │
│       │           └────────┬────────┘                   │
│       │                    │ top-N docs                 │
│       │                    ▼                            │
│       │           RerankingService                      │
│       │           (Resilience4j CB)                     │
│       │                    │ docs payload               │
│       │                    ▼                            │
│       │           ┌─────────────────┐                   │
│       │           │  FastAPI :8001   │                  │
│       │           │  Gunicorn        │                  │
│       │           │  all-MiniLM-L6  │                  │
│       │           │  hybrid scoring  │                  │
│       │           └────────┬────────┘                   │
│       │                    │ re-ranked docs             │
│       ▼                    ▼                            │
│   CacheService  ◄──── final results                     │
│   (Redis SCAN)                                          │
│       │                                                 │
│   InteractionService  (click-through boost)             │
└─────────────────────────────────────────────────────────┘
                        │
          ┌─────────────┴──────────────┐
          ▼                            ▼
   ┌─────────────┐             ┌─────────────┐
   │    Redis    │             │  Prometheus  │
   │   :6379     │             │  /actuator   │
   └─────────────┘             └─────────────┘
```

---

## Quick start

```bash
git clone https://github.com/amgharhind/distributed-search-v2.git
cd distributed-search-v2
docker compose up --build
```

That's it. All five services start in the correct order (health-checked dependencies).

| Service | URL |
|---|---|
| Search API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Re-ranking service docs | http://localhost:8001/docs |
| Elasticsearch | http://localhost:9200 |

---

## API reference

### Search

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v2/search` | Full-text search with optional caching and re-ranking |
| `GET` | `/api/v2/search/wildcard` | Wildcard search (`pattern=mach*`) |
| `GET` | `/api/v2/search/exact-phrase` | Exact phrase match |
| `GET` | `/api/v2/search/proximity` | Proximity search (slop parameter) |
| `GET` | `/api/v2/search/range` | Range search on date or numeric field |
| `POST` | `/api/v2/search/interaction/{id}` | Record a document click for personalised ranking |

**Main search parameters**

| Param | Default | Description |
|---|---|---|
| `query` | — | Search terms |
| `field` | `content` | Field to search |
| `fileType` | — | Filter by file type |
| `page` | `0` | Page number |
| `size` | `10` | Results per page |
| `cache` | `true` | Use Redis cache |
| `rerank` | `true` | Apply semantic re-ranking |

### Cache

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v2/cache/keys` | List cached keys (safe SCAN) |
| `DELETE` | `/api/v2/cache` | Evict a specific key |
| `DELETE` | `/api/v2/cache/all` | Clear all cache |

---

## How the hybrid scoring works

```
final_score = 0.40 × norm_bm25 + 0.60 × cosine_similarity
```

1. **BM25** (Elasticsearch) — fast lexical retrieval, top-N candidates
2. **all-MiniLM-L6-v2** (sentence-transformers) — encodes query and each candidate document
3. **Cosine similarity** — measures semantic closeness in embedding space
4. **Hybrid score** — combines both signals; BM25 ensures keyword recall, cosine ensures semantic relevance

If the re-ranking service is unavailable the **Resilience4j circuit breaker** opens and results fall back to BM25 order automatically — the search endpoint never returns an error.

---

## Tech stack

| Layer | Technology |
|---|---|
| Core API | Spring Boot 3.2 · Java 21 |
| Search engine | Elasticsearch 8.13 (3-node cluster) |
| Re-ranking | FastAPI · Gunicorn · sentence-transformers |
| ML model | `all-MiniLM-L6-v2` |
| Cache | Redis 7.2 |
| Resilience | Resilience4j circuit breaker |
| HTTP client | Spring WebClient |
| API docs | Springdoc OpenAPI (Swagger UI) |
| Metrics | Micrometer · Prometheus |
| Containerisation | Docker · Docker Compose |

---

## Project structure

```
distributed-search-v2/
├── docker-compose.yml              # One-command full-stack startup
├── .env                            # Environment variables
├── search-api/                     # Spring Boot application
│   ├── Dockerfile                  # Multi-stage build (Maven → JRE 21)
│   ├── pom.xml
│   └── src/main/java/com/search/distributed/
│       ├── config/                 # ES, Redis, WebClient beans
│       ├── controller/             # SearchController, CacheController
│       ├── service/                # SearchService, RerankingService,
│       │                           # CacheService, InteractionService
│       ├── model/                  # Document, SearchResult, RerankRequest
│       └── exception/              # GlobalExceptionHandler
└── reranking-service/              # FastAPI re-ranking microservice
    ├── Dockerfile
    ├── gunicorn.conf.py
    └── app/
        ├── main.py                 # FastAPI app + /health + /re-rank
        ├── reranker.py             # Async ThreadPoolExecutor + hybrid scoring
        └── models.py               # Pydantic request/response models
```

---

## Compared to v1

The original v1 implementation had these production blockers — all fixed in v2:

- `http://localhost:5000` hardcoded → breaks inside Docker containers
- `KEYS *` Redis command → blocks the entire Redis instance under load  
- Flask dev server → single-threaded, queues up under concurrent searches
- Re-ranking service re-fetched documents from ES → doubled the ES load
- No docker-compose for Spring Boot or Flask → four manual startup steps
- Deprecated `distilbert-base-nli-stsb-mean-tokens` model
- No fallback when re-ranker was down → entire search request failed
- Dead/commented-out code left in `SearchService`

---

## License

MIT