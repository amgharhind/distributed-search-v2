import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

DOC_A = {"id": "1", "content": "Distributed search with Elasticsearch", "bm25_score": 12.0}
DOC_B = {"id": "2", "content": "Redis caching strategies", "bm25_score": 6.0}


def test_health_returns_200():
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "healthy"
    assert "model" in body


def test_rerank_empty_query_returns_400():
    resp = client.post("/re-rank", json={"query": "   ", "documents": [DOC_A]})
    assert resp.status_code == 400


def test_rerank_empty_documents_returns_empty_list():
    resp = client.post("/re-rank", json={"query": "search", "documents": []})
    assert resp.status_code == 200
    assert resp.json()["ranked_results"] == []


def test_rerank_returns_all_documents():
    resp = client.post("/re-rank", json={"query": "search", "documents": [DOC_A, DOC_B]})
    assert resp.status_code == 200
    results = resp.json()["ranked_results"]
    assert len(results) == 2


def test_rerank_results_sorted_by_final_score():
    resp = client.post("/re-rank", json={"query": "search", "documents": [DOC_A, DOC_B]})
    results = resp.json()["ranked_results"]
    scores = [r["final_score"] for r in results]
    assert scores == sorted(scores, reverse=True)


def test_rerank_result_fields():
    resp = client.post("/re-rank", json={"query": "search", "documents": [DOC_A]})
    result = resp.json()["ranked_results"][0]
    assert set(result.keys()) == {"id", "content", "bm25_score", "semantic_score", "final_score"}
    assert result["id"] == DOC_A["id"]
