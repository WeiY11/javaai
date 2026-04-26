CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(64)     NOT NULL UNIQUE,
    password        VARCHAR(256)    NOT NULL,
    email           VARCHAR(128),
    system_role     VARCHAR(16)     NOT NULL DEFAULT 'USER',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_group (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    org_code        VARCHAR(64)     UNIQUE,
    creator_id      BIGINT          NOT NULL REFERENCES sys_user(id),
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_member (
    id              BIGSERIAL       PRIMARY KEY,
    group_id        BIGINT          NOT NULL REFERENCES sys_group(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role            VARCHAR(16)     NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, user_id)
);

CREATE TABLE IF NOT EXISTS knowledge_base (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    group_id        BIGINT          NOT NULL REFERENCES sys_group(id) ON DELETE CASCADE,
    evidence_threshold  DECIMAL(3,2) NOT NULL DEFAULT 0.50,
    chunk_strategy  VARCHAR(32)     NOT NULL DEFAULT 'PARAGRAPH',
    chunk_size      INT             NOT NULL DEFAULT 500,
    chunk_overlap   INT             NOT NULL DEFAULT 100,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    creator_id      BIGINT          NOT NULL REFERENCES sys_user(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_member (
    id              BIGSERIAL       PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role            VARCHAR(16)     NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(knowledge_base_id, user_id)
);

CREATE TABLE IF NOT EXISTS document (
    id              BIGSERIAL       PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    file_name       VARCHAR(256)    NOT NULL,
    file_format     VARCHAR(32)     NOT NULL,
    file_size       BIGINT          NOT NULL,
    storage_path    VARCHAR(512)    NOT NULL,
    ingestion_status VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    chunk_count     INT             DEFAULT 0,
    uploader_id     BIGINT          NOT NULL REFERENCES sys_user(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_chunk (
    id              BIGSERIAL       PRIMARY KEY,
    document_id     BIGINT          NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT       NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    content         TEXT            NOT NULL,
    chunk_index     INT             NOT NULL,
    vector_id       VARCHAR(128),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT       REFERENCES knowledge_base(id) ON DELETE SET NULL,
    model_provider  VARCHAR(32)     NOT NULL DEFAULT 'deepseek',
    title           VARCHAR(256),
    summary         TEXT,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id              BIGSERIAL       PRIMARY KEY,
    conversation_id BIGINT          NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role            VARCHAR(16)     NOT NULL,
    content         TEXT            NOT NULL,
    citations       JSONB,
    tool_calls      JSONB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analysis_report (
    id              BIGSERIAL       PRIMARY KEY,
    file_path       VARCHAR(512)    NOT NULL,
    file_name       VARCHAR(256)    NOT NULL,
    provider        VARCHAR(32),
    session_id      VARCHAR(128),
    content         TEXT,
    file_size       BIGINT,
    file_category   VARCHAR(32),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    token_hash      VARCHAR(256)    NOT NULL UNIQUE,
    expires_at      TIMESTAMP       NOT NULL,
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);
