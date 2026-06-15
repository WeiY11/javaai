-- Document-level permissions and audit logging
CREATE TABLE document_permission (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    permission_type VARCHAR(20) NOT NULL DEFAULT 'READ',
    granted_by      BIGINT REFERENCES sys_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(document_id, user_id, permission_type)
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES sys_user(id),
    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50) NOT NULL,
    resource_id     BIGINT,
    detail          JSONB DEFAULT '{}',
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_doc_perm_doc ON document_permission(document_id);
CREATE INDEX idx_doc_perm_user ON document_permission(user_id);
CREATE INDEX idx_doc_perm_doc_user ON document_permission(document_id, user_id);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at DESC);
