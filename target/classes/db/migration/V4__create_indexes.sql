CREATE INDEX IF NOT EXISTS idx_user_system_role ON sys_user (system_role);
CREATE INDEX IF NOT EXISTS idx_user_status ON sys_user (status);

CREATE INDEX IF NOT EXISTS idx_group_creator_id ON sys_group (creator_id);
CREATE INDEX IF NOT EXISTS idx_group_status ON sys_group (status);

CREATE INDEX IF NOT EXISTS idx_group_member_user_id ON group_member (user_id);
CREATE INDEX IF NOT EXISTS idx_group_member_group_id ON group_member (group_id);

CREATE INDEX IF NOT EXISTS idx_kb_group_id ON knowledge_base (group_id);
CREATE INDEX IF NOT EXISTS idx_kb_creator_id ON knowledge_base (creator_id);
CREATE INDEX IF NOT EXISTS idx_kb_status ON knowledge_base (status);

CREATE INDEX IF NOT EXISTS idx_kb_member_user_id ON kb_member (user_id);
CREATE INDEX IF NOT EXISTS idx_kb_member_kb_id ON kb_member (knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_doc_kb_id ON document (knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_doc_uploader_id ON document (uploader_id);
CREATE INDEX IF NOT EXISTS idx_doc_ingestion_status ON document (ingestion_status);

CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON document_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id ON document_chunk (knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_conv_user_id ON conversation (user_id);
CREATE INDEX IF NOT EXISTS idx_conv_kb_id ON conversation (knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_conv_status ON conversation (status);

CREATE INDEX IF NOT EXISTS idx_msg_conv_id ON message (conversation_id);
CREATE INDEX IF NOT EXISTS idx_msg_role ON message (role);
CREATE INDEX IF NOT EXISTS idx_msg_created_at ON message (created_at);

CREATE INDEX IF NOT EXISTS idx_rt_user_id ON refresh_token (user_id);
CREATE INDEX IF NOT EXISTS idx_rt_expires_at ON refresh_token (expires_at);
