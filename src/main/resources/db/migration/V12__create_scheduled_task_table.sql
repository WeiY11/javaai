-- Scheduled tasks table
CREATE TABLE scheduled_task (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    cron_expression VARCHAR(50) NOT NULL,
    task_type       VARCHAR(50) NOT NULL,
    config          JSONB DEFAULT '{}',
    last_run_at     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    creator_id      BIGINT REFERENCES sys_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_task_status ON scheduled_task(status);
CREATE INDEX idx_scheduled_task_type ON scheduled_task(task_type);
CREATE INDEX idx_scheduled_task_next_run ON scheduled_task(next_run_at) WHERE status = 'ACTIVE';
