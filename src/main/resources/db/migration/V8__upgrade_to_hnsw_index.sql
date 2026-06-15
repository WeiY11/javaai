-- V8: 将 IVFFlat 索引升级为 HNSW 索引
--
-- HNSW (Hierarchical Navigable Small World) 索引相比 IVFFlat 的优势：
-- 1. 更高的召回率（Recall）：HNSW 通过多层图结构实现近似最近邻搜索，
--    在相同延迟下通常比 IVFFlat 高 5-15% 的召回率
-- 2. 无需训练阶段：IVFFlat 需要预先对数据做 k-means 聚类（lists 参数），
--    而 HNSW 直接构建图结构，新增数据无需重建索引
-- 3. 更好的小数据集表现：IVFFlat 在数据量少于 lists 数时性能退化，
--    HNSW 不受此限制
--
-- 参数说明：
-- - m = 16: 每个节点的最大连接数（控制图的密度和内存占用）
-- - ef_construction = 64: 构建时的搜索宽度（越大构建越精确但越慢）
--
-- 面试点：ANN 索引原理对比（IVF vs HNSW），recall-latency tradeoff

-- 删除旧的 IVFFlat 索引
DROP INDEX IF EXISTS idx_chunk_embedding_ivfflat;

-- 创建 HNSW 索引（cosine 距离）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw
    ON document_chunk_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
