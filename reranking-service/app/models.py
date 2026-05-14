from pydantic import BaseModel
from typing import List


class DocumentItem(BaseModel):
    id: str
    content: str
    bm25_score: float


class RerankRequest(BaseModel):
    query: str
    documents: List[DocumentItem]


class RankedDocument(BaseModel):
    id: str
    content: str
    bm25_score: float
    semantic_score: float
    final_score: float


class RerankResponse(BaseModel):
    ranked_results: List[RankedDocument]
    model_used: str


class HealthResponse(BaseModel):
    status: str
    model: str
