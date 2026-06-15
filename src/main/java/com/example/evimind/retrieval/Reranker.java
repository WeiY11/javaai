package com.example.evimind.retrieval;

import java.util.List;

/**
 * Reranker 接口 — Two-stage Retrieval 的精排阶段。
 *
 * <p>在 HybridSearch + RRF 融合（粗排/召回）之后，对 top-N 候选做更精细的相关性打分。 面试点：Bi-Encoder（用于召回的向量检索）vs
 * Cross-Encoder（用于精排的 Reranker）的原理对比。
 *
 * <p>设计为接口 + 多实现，通过 @ConditionalOnProperty 切换： - PromptBasedReranker: 基于 LLM prompt 的轻量级
 * reranker（默认） - ApiReranker: 集成外部 Reranker API（BGE-Reranker / Cohere Rerank）（可选）
 */
public interface Reranker {

  /**
   * 对候选结果进行重排序。
   *
   * @param query 用户查询（已改写后的独立查询）
   * @param candidates 粗排阶段的候选结果（按 RRF 融合分数排序）
   * @param topN 精排后保留的最大结果数
   * @return 重排序后的结果列表
   */
  List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN);
}
