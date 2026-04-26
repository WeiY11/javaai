CREATE TABLE IF NOT EXISTS document_chunk_embedding (
    id              BIGSERIAL       PRIMARY KEY,
    chunk_id        BIGINT          NOT NULL REFERENCES document_chunk(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT       NOT NULL,
    embedding       vector(1536)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chunk_embedding_ivfflat
    ON document_chunk_embedding
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_chunk_embedding_kb_id
    ON document_chunk_embedding (knowledge_base_id);
