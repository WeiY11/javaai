-- 引用关系表：记录学术论文之间的引用关系
CREATE TABLE IF NOT EXISTS citation_link (
    id              BIGSERIAL       PRIMARY KEY,
    document_id     BIGINT          NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT        NOT NULL,
    cited_doi       VARCHAR(256),
    cited_title     VARCHAR(1024),
    cited_authors   VARCHAR(2048),
    cited_year      INTEGER,
    raw_reference   TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_citation_link_doc_id ON citation_link(document_id);
CREATE INDEX IF NOT EXISTS idx_citation_link_kb_id ON citation_link(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_citation_link_doi ON citation_link(cited_doi);
