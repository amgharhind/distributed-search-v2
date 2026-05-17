"""
Sample data loader for distributed-search-v2.

Documents are designed to demonstrate three key system capabilities:
  1. Re-ranking value  — queries where BM25 keyword order diverges from semantic relevance
  2. Cache value       — repeated queries that benefit from Redis caching
  3. Distributed search value — documents that justify the full stack

Usage:
    python scripts/load-sample-data.py
    python scripts/load-sample-data.py --host http://localhost:8080
"""

import json, sys, urllib.request, urllib.error, argparse

BASE = "http://localhost:8080"

DOCUMENTS = [

    # ── Cluster 1: BM25 weakness / re-ranking value ───────────────────────────
    # Query to run: "fast document retrieval"
    # BM25 will surface Doc-A (keyword-stuffed). Re-ranker surfaces Doc-B & Doc-C.
    {
        "title": "Fast Document Retrieval Fast Fast Fast",
        "author": "BM25 Weakness Demo",
        "fileType": "txt",
        "createdDate": "2024-01-10",
        "content": (
            "Fast document retrieval. Fast document retrieval is fast. "
            "Fast retrieval of documents is important for fast search. "
            "Fast fast fast document retrieval systems retrieve documents fast. "
            "The word fast appears many times. Fast retrieval. Document retrieval fast. "
            "This document is about fast document retrieval and nothing else. "
            "Fast. Document. Retrieval. Fast. Fast. Fast. Retrieval. Documents. Fast."
        ),
    },
    {
        "title": "Low-Latency Search: Engineering Sub-10ms Response Times",
        "author": "Amina Benali",
        "fileType": "pdf",
        "createdDate": "2024-03-15",
        "content": (
            "Achieving sub-10ms search latency at scale requires a combination of inverted index "
            "optimization, in-memory caching layers, and efficient query planning. Elasticsearch "
            "stores index segments in the OS page cache, ensuring that hot data is served from RAM "
            "rather than disk. Shard routing eliminates the need to scan the full cluster for every "
            "query — only the relevant shards receive the request. Connection pooling and HTTP/2 "
            "multiplexing reduce round-trip overhead between the application tier and the search "
            "cluster. For extremely latency-sensitive workloads, pre-warming the index cache after "
            "deployment is essential. Circuit breakers prevent memory pressure from degrading "
            "throughput during traffic spikes. The combination of these techniques delivers "
            "consistently fast query responses even under high concurrency, without sacrificing "
            "result quality or index freshness."
        ),
    },
    {
        "title": "Inverted Index Internals: How Search Engines Find Documents Instantly",
        "author": "Carlos Mendes",
        "fileType": "pdf",
        "createdDate": "2024-02-20",
        "content": (
            "The inverted index is the fundamental data structure that makes full-text search "
            "possible at scale. Instead of scanning each document for a query term, the index maps "
            "every unique term to a posting list — a sorted array of document identifiers that "
            "contain that term, along with term frequency and position information. Lucene, the "
            "engine beneath Elasticsearch, segments the index into immutable files called segments. "
            "Each segment contains its own inverted index, term dictionary, and stored fields. "
            "Queries are evaluated against all segments in parallel, and results are merged by score. "
            "Skip lists within posting lists allow the engine to jump over large blocks of "
            "non-matching documents, dramatically reducing intersection cost for AND queries. "
            "This design is why even a billion-document index can answer a query in milliseconds — "
            "it never scans the corpus linearly."
        ),
    },

    # ── Cluster 2: Vocabulary mismatch — re-ranking value ────────────────────
    # Query: "how does semantic search understand meaning"
    # BM25 boosts exact keyword matches. Semantic re-ranker finds conceptually relevant docs.
    {
        "title": "The Vocabulary Mismatch Problem in Information Retrieval",
        "author": "Sara El-Amrani",
        "fileType": "pdf",
        "createdDate": "2023-11-05",
        "content": (
            "One of the most persistent challenges in information retrieval is the vocabulary "
            "mismatch problem: users express their information need using different words than "
            "authors used when writing the relevant documents. A user searching for 'cardiac arrest "
            "treatment' may miss a paper that uses 'myocardial infarction therapy' exclusively. "
            "Traditional lexical models like BM25 are blind to this synonymy — they score documents "
            "purely on term overlap. Early attempts to solve this included query expansion using "
            "thesauri (WordNet) and pseudo-relevance feedback. These techniques helped but were "
            "brittle. The real breakthrough came with dense vector representations from models like "
            "BERT, where semantically equivalent sentences map to nearby points in a high-dimensional "
            "embedding space, regardless of surface-level word choice. Hybrid retrieval — combining "
            "BM25's precision for exact matches with vector similarity for conceptual matching — "
            "consistently outperforms either approach alone on standard IR benchmarks."
        ),
    },
    {
        "title": "Sentence Transformers and Cosine Similarity for Document Re-ranking",
        "author": "Mehdi Ouali",
        "fileType": "pdf",
        "createdDate": "2024-01-28",
        "content": (
            "Sentence transformers produce fixed-length dense embeddings that encode the semantic "
            "meaning of a sentence, not just its keywords. The all-MiniLM-L6-v2 model maps text "
            "to a 384-dimensional vector space, trained on over a billion sentence pairs using "
            "contrastive learning. Cosine similarity between a query embedding and a document "
            "embedding measures the angle between the two vectors, ranging from -1 (opposite "
            "meaning) to 1 (identical meaning). In a hybrid re-ranking pipeline, BM25 provides "
            "an initial candidate set (typically top-100 documents) and the sentence transformer "
            "re-orders them by semantic relevance. This two-stage approach is computationally "
            "efficient — running dense inference over 100 candidates rather than the entire corpus "
            "— while delivering ranking quality close to full semantic retrieval. The 40% BM25 "
            "plus 60% cosine score blending used in production systems reflects the empirical "
            "finding that semantic similarity should dominate but lexical signals remain valuable "
            "for precision on exact-match queries."
        ),
    },
    {
        "title": "Why BERT Understands 'King - Man + Woman = Queen'",
        "author": "Fatima Zahra Idrissi",
        "fileType": "md",
        "createdDate": "2023-09-14",
        "content": (
            "Large language models trained on massive corpora develop internal representations "
            "that capture semantic relationships between concepts. The famous word2vec analogy "
            "'king minus man plus woman equals queen' demonstrated that word embeddings encode "
            "relational structure in geometric space. BERT extends this by producing "
            "context-dependent embeddings: the word 'bank' has a different vector in 'river bank' "
            "versus 'bank account'. This context-sensitivity makes BERT-family models superior "
            "for understanding user intent in search queries. When a user asks 'what causes "
            "fever', a BERT-based re-ranker can identify documents about inflammation and immune "
            "response as highly relevant, even if they never use the word 'fever'. This "
            "understanding of meaning beyond keywords is what separates modern neural search "
            "from earlier keyword-based systems and makes semantic re-ranking so valuable in "
            "production information retrieval pipelines."
        ),
    },

    # ── Cluster 3: Cache value ────────────────────────────────────────────────
    # Run the same query twice to see the second response come from Redis cache.
    {
        "title": "Redis as a Search Cache Layer: Architecture and Best Practices",
        "author": "Yassir Benmoussa",
        "fileType": "pdf",
        "createdDate": "2024-04-02",
        "content": (
            "Caching search results in Redis reduces Elasticsearch load by up to 90% for "
            "repeated queries, which are far more common than analytics suggest. In e-commerce "
            "search, the top 1000 queries account for 60-80% of all traffic. Storing "
            "serialized result sets in Redis with a TTL of 5-15 minutes delivers sub-millisecond "
            "cache hits compared to 50-200ms Elasticsearch queries. The cache key should be a "
            "deterministic hash of all query parameters — query string, filters, pagination, "
            "sort order, and feature flags — to avoid serving stale results for different "
            "request shapes. Redis SCAN is preferred over KEYS for cache inspection in "
            "production, as KEYS blocks the event loop. Selective cache invalidation on index "
            "updates prevents serving outdated results without a full cache flush. This "
            "distributed caching pattern is essential for any search system expected to handle "
            "thousands of queries per second."
        ),
    },
    {
        "title": "Cache Invalidation: The Second Hardest Problem in Computer Science",
        "author": "Nadia Tazi",
        "fileType": "pdf",
        "createdDate": "2023-12-18",
        "content": (
            "Phil Karlton's famous quip — 'There are only two hard things in computer science: "
            "cache invalidation and naming things' — remains painfully accurate. In a search "
            "system, a document update must invalidate all cache entries whose result set would "
            "change as a consequence. This is computationally intractable in the general case: "
            "you cannot know which queries a document update affects without re-running every "
            "cached query. Practical strategies include TTL-based expiry (accept some staleness), "
            "tag-based invalidation (attach document IDs to cache entries and evict on update), "
            "and write-through caching (update the cache synchronously on every index operation). "
            "For most search applications, a TTL of 5-10 minutes with manual flush endpoints "
            "for critical updates strikes the right balance between freshness and performance. "
            "The cache eviction API in this system allows operators to surgically remove "
            "individual keys without flushing the entire cache."
        ),
    },
    {
        "title": "Measuring Cache Effectiveness: Hit Rate, Latency P95, and Cost Savings",
        "author": "Omar Lahlou",
        "fileType": "pdf",
        "createdDate": "2024-02-10",
        "content": (
            "Cache effectiveness is measured along three dimensions: hit rate, latency impact, "
            "and infrastructure cost reduction. A hit rate above 40% is generally considered "
            "worthwhile for a search cache; elite e-commerce platforms achieve 70-85%. The "
            "latency benefit is non-linear: a cached response returns in under 2ms while an "
            "uncached query involving BM25 scoring, re-ranking inference, and serialization "
            "takes 80-300ms. At P95, the difference is even more dramatic because the tail of "
            "the latency distribution is dominated by Elasticsearch GC pauses and re-ranker "
            "cold-start overhead. From a cost perspective, each cache hit is an Elasticsearch "
            "query that did not run — reducing CPU cycles, JVM heap pressure, and network "
            "bandwidth. A Redis instance costing $50/month can eliminate $2000/month in "
            "Elasticsearch compute by absorbing repeated traffic. Monitoring cache hit rate "
            "alongside P50/P95/P99 query latency in Prometheus and Grafana is essential for "
            "validating that the caching layer is delivering its intended value."
        ),
    },

    # ── Cluster 4: Distributed system value ──────────────────────────────────
    {
        "title": "Elasticsearch Cluster: Sharding, Replication, and Fault Tolerance",
        "author": "Hamid Berrada",
        "fileType": "pdf",
        "createdDate": "2024-03-28",
        "content": (
            "An Elasticsearch cluster distributes its index across multiple nodes using shards — "
            "independent Lucene instances that each hold a subset of the data. Primary shards "
            "handle writes; replica shards serve reads and provide failover. With two nodes and "
            "one replica, the cluster survives the loss of one node without data loss or "
            "downtime. Query fan-out sends the request to one copy of each shard in parallel; "
            "the coordinating node merges the per-shard top-k results into the global ranking. "
            "This horizontal scaling model means that doubling the number of shards roughly "
            "halves query latency (up to the point of coordination overhead). Index lifecycle "
            "management (ILM) automates moving older data to cheaper storage tiers, keeping "
            "the hot tier fast. The distributed architecture also enables zero-downtime "
            "rolling upgrades — updating one node at a time while the cluster continues serving "
            "queries on the remaining nodes."
        ),
    },
    {
        "title": "Resilience4j Circuit Breaker: Protecting Search from Re-ranker Failures",
        "author": "Ines Benkirane",
        "fileType": "pdf",
        "createdDate": "2024-01-15",
        "content": (
            "In a microservices architecture, a downstream service failure should not cascade "
            "into a full system outage. The Resilience4j circuit breaker pattern monitors "
            "call failure rates over a sliding window of recent requests. When the failure "
            "rate exceeds the configured threshold (50% in this system), the circuit opens "
            "and subsequent calls are rejected immediately, returning the BM25 fallback ranking "
            "without waiting for the re-ranker to time out. After a configurable wait period "
            "(10 seconds), the circuit transitions to half-open: a limited number of probe "
            "requests are allowed through to test if the re-ranker has recovered. If probes "
            "succeed, the circuit closes and normal operation resumes. This pattern prevents "
            "re-ranker latency spikes from degrading the entire search response time and "
            "ensures the system degrades gracefully — users still get relevant BM25 results "
            "even when the neural re-ranker is unavailable."
        ),
    },
    {
        "title": "CAP Theorem Applied: Why Elasticsearch Chooses Consistency over Availability",
        "author": "Rachid El Fassi",
        "fileType": "pdf",
        "createdDate": "2023-10-22",
        "content": (
            "The CAP theorem states that a distributed system can guarantee at most two of: "
            "Consistency, Availability, and Partition tolerance. Since network partitions are "
            "unavoidable in practice, the real choice is between CP (consistency + partition "
            "tolerance) and AP (availability + partition tolerance). Elasticsearch is a CP "
            "system by default: during a network partition, a shard with no quorum (majority "
            "of copies) rejects writes rather than risk diverging from the cluster state. "
            "This prevents 'split-brain' scenarios where two nodes simultaneously accept "
            "conflicting writes for the same document. For a search index where result "
            "correctness matters more than accepting every write under any network condition, "
            "CP is the right trade-off. Redis, by contrast, can be configured as either CP "
            "(using Redlock) or AP, depending on the use case — for a search cache, AP is "
            "acceptable because serving a slightly stale cached result is far better than "
            "returning an error."
        ),
    },

    # ── Cluster 5: System evaluation / benchmarking ───────────────────────────
    {
        "title": "NDCG, MRR and MAP: Evaluating Search Ranking Quality",
        "author": "Leila Moussaoui",
        "fileType": "pdf",
        "createdDate": "2024-02-05",
        "content": (
            "Offline evaluation of search ranking quality uses three primary metrics. "
            "Normalized Discounted Cumulative Gain (NDCG) measures the quality of the entire "
            "ranked list, applying a logarithmic discount to lower positions — a relevant "
            "document at rank 1 contributes more than the same document at rank 5. Mean "
            "Reciprocal Rank (MRR) focuses on the rank of the first relevant result, making it "
            "ideal for navigational queries where users need one specific answer. Mean Average "
            "Precision (MAP) averages precision at each relevant document's rank, rewarding "
            "systems that find all relevant documents early. Hybrid re-ranking consistently "
            "improves NDCG@10 by 8-15% over pure BM25 on standard BEIR benchmark datasets. "
            "The improvement is largest on queries with vocabulary mismatch between the query "
            "and the relevant documents — exactly the scenario where semantic understanding "
            "adds value over lexical matching. Online A/B testing complements offline metrics "
            "by measuring user engagement signals: click-through rate, dwell time, and "
            "result abandonment rate."
        ),
    },
    {
        "title": "Hybrid Search Architecture: BM25 + Cosine at 40/60 — Design Decisions",
        "author": "Adam Chraibi",
        "fileType": "pdf",
        "createdDate": "2024-04-10",
        "content": (
            "The distributed-search-v2 system implements a two-stage hybrid retrieval pipeline. "
            "Stage one: Elasticsearch BM25 retrieves the top-50 candidate documents using "
            "its inverted index — this is extremely fast (2-10ms) and covers the vast majority "
            "of relevant documents. Stage two: the FastAPI re-ranking service computes "
            "sentence embeddings for the query and all 50 candidates using all-MiniLM-L6-v2, "
            "then blends scores as final = 0.4 * BM25_normalized + 0.6 * cosine_similarity. "
            "The 60% weight on cosine reflects empirical testing showing semantic similarity "
            "is the stronger relevance signal for this corpus, while the 40% BM25 weight "
            "preserves precision for exact-match queries (product codes, names, acronyms). "
            "The re-ranker is wrapped in a Resilience4j circuit breaker, falling back to "
            "pure BM25 order when the neural service is unavailable. Redis caches the full "
            "re-ranked result set per query, so the 30-80ms re-ranking cost is paid only once "
            "per unique query in the cache TTL window."
        ),
    },
    {
        "title": "Distributed Search at Scale: Lessons from Production Deployments",
        "author": "Zineb Alaoui",
        "fileType": "pdf",
        "createdDate": "2024-05-01",
        "content": (
            "After operating distributed search systems at scale, several lessons emerge. "
            "First, the cache hit rate is the most important operational metric — a system "
            "with 70% cache hit rate is fundamentally different from one at 20%, both in "
            "cost and user experience. Second, re-ranking adds the most value for long-tail "
            "queries where BM25's keyword bias produces poor results; for head queries "
            "('iPhone 15 price'), BM25 alone is usually sufficient. Third, circuit breakers "
            "around the re-ranking service are non-negotiable in production — a 30ms timeout "
            "without a breaker turns into a 30-second cascade failure during model cold starts. "
            "Fourth, Elasticsearch cluster sizing should be driven by indexing throughput and "
            "heap requirements, not query latency — the query latency problem is solved by "
            "sharding and caching, not by bigger nodes. Fifth, the full observability stack "
            "(Prometheus metrics, structured logs, distributed traces) pays for itself within "
            "the first production incident. The combination of BM25, semantic re-ranking, "
            "Redis caching, and circuit breakers represents the current state of the art "
            "for production information retrieval systems."
        ),
    },
]


def bulk_index(host: str, docs: list) -> None:
    url = f"{host}/api/v2/documents/bulk"
    payload = json.dumps(docs).encode()
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read())
            print(f"[OK] Indexed {body['indexed']} documents")
    except urllib.error.HTTPError as e:
        print(f"[ERR] HTTP {e.code}: {e.read().decode()}")
        sys.exit(1)
    except Exception as e:
        print(f"[ERR] {e}")
        sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=BASE, help="Search API base URL")
    args = parser.parse_args()

    print(f"Loading {len(DOCUMENTS)} sample documents into {args.host} ...")
    bulk_index(args.host, DOCUMENTS)
    print()
    print("Done! Suggested queries to test:")
    print("  Search tab → Full-text:")
    print("    'fast document retrieval'       → run without re-rank, then with re-rank — compare order")
    print("    'how does semantic search work' → BM25 misses conceptual matches; re-ranker fixes it")
    print("    'cache latency performance'     → run twice; second hit shows ⚡ Cached badge")
    print("    'circuit breaker microservices' → tests resilience documentation cluster")
    print("    'distributed search production' → broad query showing full corpus coverage")
