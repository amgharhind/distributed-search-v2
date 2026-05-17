"""
Replaces the keyword-stuffed demo document with one that properly
demonstrates BM25's weakness: a document about fast cars that shares
keywords (fast, document, retrieval) but is semantically off-topic.
"""
import json
import urllib.request

BASE = "http://localhost:8080"


def api(path, method="GET", body=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body else None,
        headers={"Content-Type": "application/json"} if body else {},
        method=method,
    )
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read()) if r.status != 204 else None


# 1. Find and delete the stuffed document
docs = api("/api/v2/documents?size=50")
deleted = 0
for d in docs:
    if "Fast Fast Fast" in (d.get("title") or ""):
        api(f"/api/v2/documents/{d['id']}", method="DELETE")
        print(f"Deleted: {d['id']}  ({d['title']})")
        deleted += 1

if deleted == 0:
    print("No stuffed document found (already replaced or never loaded).")

# 2. Add the corrected demo document — off-topic keywords, same surface words
replacement = [{
    "title": "Fast Cars: Ferrari Document Delivery Service",
    "author": "BM25 Weakness Demo",
    "fileType": "txt",
    "createdDate": "2024-01-10",
    "content": (
        "Fast cars dominate the automotive world. Ferrari document registration "
        "must be filed fast at the vehicle bureau. Retrieval of car ownership "
        "documents from the registry office is a fast process. The fast lane "
        "on motorways requires a valid driving document. Fast delivery couriers "
        "transport physical document packages across the city. Ferrari produces "
        "the fastest cars. Document your speed on the track. Fast vehicles need "
        "retrieval of maintenance records from authorized dealers. Fast. Cars. "
        "Document. Retrieval. Ferrari. Speed. Fast cars, fast document filing."
    ),
}]

result = api("/api/v2/documents/bulk", method="POST", body=replacement)
print(f"Inserted replacement document: {result['documents'][0]['id']}")
print()
print("Now run 'fast document retrieval' in the Search tab:")
print("  BM25 only  → Ferrari cars doc ranks #1 (keyword match)")
print("  Re-ranked  → Ferrari cars doc drops to last (semantic = cars, not search)")
