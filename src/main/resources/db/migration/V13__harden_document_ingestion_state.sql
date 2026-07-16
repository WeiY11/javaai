ALTER TABLE document ADD COLUMN IF NOT EXISTS failed_stage VARCHAR(32);
ALTER TABLE document ADD COLUMN IF NOT EXISTS error_code VARCHAR(64);
ALTER TABLE document ADD COLUMN IF NOT EXISTS error_message VARCHAR(1024);
ALTER TABLE document ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE document ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP;
ALTER TABLE document ADD COLUMN IF NOT EXISTS ingestion_version INT NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS active_ingestion_version INT;

ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS ingestion_version INT NOT NULL DEFAULT 0;

DELETE FROM document_chunk dc
WHERE EXISTS (
    SELECT 1
    FROM document_chunk keep
    WHERE keep.document_id = dc.document_id
      AND keep.ingestion_version = dc.ingestion_version
      AND keep.chunk_index = dc.chunk_index
      AND keep.id < dc.id
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_chunk_version_index
    ON document_chunk(document_id, ingestion_version, chunk_index);
