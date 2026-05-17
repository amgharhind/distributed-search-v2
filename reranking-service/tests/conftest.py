"""
Patch sentence_transformers (and thus torch) out of the process before any
app module is imported.  This keeps CI fast — no 500 MB PyTorch download.
"""
import sys
from unittest.mock import MagicMock

_mock_model = MagicMock()

# Wire cos_sim(...)[0].tolist() to return a real list so _rerank_sync can
# iterate over it alongside zip(documents, ...).
_cos_inner = MagicMock()
_cos_inner.tolist.return_value = [0.9, 0.5, 0.7, 0.8]

_cos_matrix = MagicMock()
_cos_matrix.__getitem__ = MagicMock(return_value=_cos_inner)

_mock_st = MagicMock()
_mock_st.SentenceTransformer.return_value = _mock_model
_mock_st.util.cos_sim.return_value = _cos_matrix

sys.modules.setdefault("sentence_transformers", _mock_st)
