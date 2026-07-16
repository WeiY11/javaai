# RAG Evaluation Plan

This repository now has stronger ingestion/versioning and stream reliability foundations, but it still does not contain a complete offline RAG evaluation harness.

## Dataset Shape

Evaluation datasets should use JSON records like:

```json
{
  "question": "...",
  "knowledgeBaseId": 1,
  "relevantChunkIds": [],
  "expectedDocuments": [],
  "referenceAnswer": "...",
  "shouldRefuse": false
}
```

## Required Metrics

Retrieval metrics:

- Recall@5
- Recall@10
- MRR
- nDCG@10
- no-result rate
- degraded-search rate

Answer metrics:

- answer correctness
- faithfulness
- citation precision
- citation recall
- unsupported claim rate
- refusal precision
- refusal recall

Performance metrics:

- query rewrite latency
- vector search latency
- keyword search latency
- rerank latency
- TTFT
- total latency
- input/output tokens
- estimated request cost

## Experiment Matrix

The harness should compare vector only, keyword only, vector + keyword, RRF, RRF + reranker, query rewrite on/off, and evidence gate on/off. Reports should be emitted as JSON, CSV, and Markdown. Do not claim quality improvements until this harness has real datasets and measured outputs.
