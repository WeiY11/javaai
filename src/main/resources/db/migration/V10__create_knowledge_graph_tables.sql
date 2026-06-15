-- Knowledge graph: entities and relations extracted from documents
CREATE TABLE kg_entity (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    entity_type     VARCHAR(100),
    description     TEXT,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    document_id     BIGINT REFERENCES document(id) ON DELETE CASCADE,
    embedding       vector(1536),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE kg_relation (
    id              BIGSERIAL PRIMARY KEY,
    source_entity_id BIGINT NOT NULL REFERENCES kg_entity(id) ON DELETE CASCADE,
    target_entity_id BIGINT NOT NULL REFERENCES kg_entity(id) ON DELETE CASCADE,
    relation_type   VARCHAR(100) NOT NULL,
    properties      JSONB DEFAULT '{}',
    document_id     BIGINT REFERENCES document(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_kg_entity_kb ON kg_entity(knowledge_base_id);
CREATE INDEX idx_kg_entity_doc ON kg_entity(document_id);
CREATE INDEX idx_kg_entity_name ON kg_entity(name);
CREATE INDEX idx_kg_entity_type ON kg_entity(entity_type);
CREATE INDEX idx_kg_relation_source ON kg_relation(source_entity_id);
CREATE INDEX idx_kg_relation_target ON kg_relation(target_entity_id);
CREATE INDEX idx_kg_relation_type ON kg_relation(relation_type);
CREATE INDEX idx_kg_relation_kb ON kg_relation(knowledge_base_id);
CREATE INDEX idx_kg_relation_doc ON kg_relation(document_id);
