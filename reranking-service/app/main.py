import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.models import RerankRequest, RerankResponse, HealthResponse
from app.reranker import Reranker

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s – %(message)s")

app = FastAPI(
    title="Distributed Search — Re-ranking Service",
    description="Semantic re-ranking using all-MiniLM-L6-v2. Accepts documents already retrieved by Elasticsearch and returns them re-ranked by hybrid BM25 + cosine score.",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

reranker = Reranker()


@app.get("/health", response_model=HealthResponse, tags=["Ops"])
async def health():
    return HealthResponse(status="healthy", model=reranker.model_name)


@app.post("/re-rank", response_model=RerankResponse, tags=["Search"])
async def rerank(request: RerankRequest):
    if not request.query.strip():
        raise HTTPException(status_code=400, detail="query must not be empty")

    ranked = await reranker.rerank(request.query, request.documents)
    return RerankResponse(ranked_results=ranked, model_used=reranker.model_name)
