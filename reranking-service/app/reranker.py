import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import List

from sentence_transformers import SentenceTransformer, util

from app.models import DocumentItem, RankedDocument

logger = logging.getLogger(__name__)

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"


class Reranker:
    def __init__(self):
        logger.info("Loading model: %s", MODEL_NAME)
        self.model = SentenceTransformer(MODEL_NAME)
        self.model_name = MODEL_NAME
        # CPU-bound work runs in a thread pool to avoid blocking the event loop
        self._executor = ThreadPoolExecutor(max_workers=4)
        logger.info("Model loaded successfully")

    async def rerank(self, query: str, documents: List[DocumentItem]) -> List[RankedDocument]:
        if not documents:
            return []
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(self._executor, self._rerank_sync, query, documents)

    def _rerank_sync(self, query: str, documents: List[DocumentItem]) -> List[RankedDocument]:
        contents = [doc.content for doc in documents]

        query_embedding = self.model.encode(query, convert_to_tensor=True)
        doc_embeddings = self.model.encode(contents, convert_to_tensor=True, batch_size=32)
        similarities = util.cos_sim(query_embedding, doc_embeddings)[0].tolist()

        bm25_scores = [doc.bm25_score for doc in documents]
        max_bm25 = max(bm25_scores) if max(bm25_scores) > 0 else 1.0

        results: List[RankedDocument] = []
        for doc, semantic_score, bm25_score in zip(documents, similarities, bm25_scores):
            norm_bm25 = bm25_score / max_bm25
            # Hybrid score: 40% BM25 (lexical) + 60% semantic
            final_score = 0.4 * norm_bm25 + 0.6 * semantic_score
            results.append(
                RankedDocument(
                    id=doc.id,
                    content=doc.content,
                    bm25_score=bm25_score,
                    semantic_score=round(semantic_score, 4),
                    final_score=round(final_score, 4),
                )
            )

        return sorted(results, key=lambda x: x.final_score, reverse=True)
