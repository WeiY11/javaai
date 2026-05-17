# Evidence Portfolio Optimization for EviMind RAG

## Motivation

Standard RAG pipelines usually pass the top-ranked chunks directly to the generator. This is efficient, but it has three failure modes:

1. Redundant chunks consume context budget without adding new evidence.
2. High-scoring but narrow evidence can miss parts of a multi-aspect query.
3. Long chunks can crowd out short but complementary support.

EviMind now treats context construction as a budgeted evidence-portfolio optimization problem instead of a top-k truncation problem.

## Proposed Contribution

The method is **Evidence Portfolio Optimization (EPO)**: given hybrid retrieval candidates, select a compact evidence subset that maximizes marginal utility under a context budget. Utility combines:

- retrieval confidence from calibrated RRF scores,
- query-term coverage gain,
- document-level diversity,
- redundancy penalty against already selected evidence.

This creates a clear algorithmic contribution that can be evaluated independently of the language model.

## Algorithm

For each query and candidate chunk list:

1. Deduplicate candidates by `(documentId, chunkIndex)`.
2. Tokenize the query and evidence content, including CJK bigrams for Chinese text.
3. Greedily select the candidate with the highest marginal utility:

   `utility = 0.62 * confidence + 0.22 * coverage_gain + 0.12 * document_diversity - 0.24 * redundancy`

4. Stop when the context budget is exhausted or no candidate has enough marginal value.
5. Render only the selected portfolio into the prompt.

This is a deterministic greedy approximation to a monotone, budget-aware portfolio objective. The deterministic design improves reproducibility and makes ablations straightforward.

## NeurIPS-Style Evaluation Plan

Primary metrics:

- answer faithfulness judged against cited chunks,
- citation precision and recall,
- context-token usage,
- latency under slow or failed retrieval backends,
- redundancy ratio among selected evidence chunks.

Baselines:

- top-k fused retrieval,
- score-threshold-only filtering,
- diversity-only MMR,
- no-budget full-context packing.

Ablations:

- remove coverage gain,
- remove document diversity,
- remove redundancy penalty,
- remove CJK n-gram tokenization,
- vary context budget and backend timeout.

Stress tests:

- duplicate chunks,
- multi-aspect queries,
- Chinese queries without spaces,
- one retrieval backend timing out,
- long evidence chunks competing with short complementary chunks.

## Current Implementation

Code entry points:

- `EvidencePortfolioSelector`: deterministic portfolio selection.
- `RagPipeline`: portfolio-aware context construction.
- `RrfFusionService`: confidence-calibrated hybrid retrieval scores.
- `HybridSearchService`: bounded candidate expansion and backend timeout.
- `AgentTools`: bounded tool retrieval output.

Configuration:

- `RAG_MAX_EVIDENCE_CONTEXT_CHARS`, default `6000`.
- `RAG_SEARCH_BACKEND_TIMEOUT_MS`, default `1500`.

## Known Risks and Mitigations

Risk: lexical coverage may miss semantic paraphrases.
Mitigation: confidence remains the dominant term, and semantic retrieval still supplies the candidate pool.

Risk: overly aggressive redundancy filtering may remove useful continuation chunks.
Mitigation: chunks from a new document or with new query coverage can still enter; the stop threshold only blocks low-marginal candidates after initial evidence is selected.

Risk: Chinese text lacks whitespace.
Mitigation: CJK bigrams are added to query/content terms for coverage and redundancy computation.

Risk: backend latency creates tail-latency spikes.
Mitigation: each retrieval backend has a configurable timeout and graceful degradation.

Risk: prompt context grows unpredictably.
Mitigation: context construction is budgeted before prompt rendering.
