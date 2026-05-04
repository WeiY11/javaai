CREATE TABLE IF NOT EXISTS research_note (
    id                  BIGSERIAL       PRIMARY KEY,
    chunk_id            BIGINT          REFERENCES document_chunk(id) ON DELETE CASCADE,
    document_id         BIGINT          REFERENCES document(id) ON DELETE CASCADE,
    knowledge_base_id   BIGINT          REFERENCES knowledge_base(id) ON DELETE CASCADE,
    user_id             BIGINT          NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    content             TEXT            NOT NULL,
    highlight           TEXT,
    start_offset        INT,
    end_offset          INT,
    tags                VARCHAR(256),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_research_note_chunk ON research_note(chunk_id);
CREATE INDEX IF NOT EXISTS idx_research_note_document ON research_note(document_id);
CREATE INDEX IF NOT EXISTS idx_research_note_user ON research_note(user_id);
