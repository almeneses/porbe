CREATE TABLE portfolio_report_ai_guidance (
    portfolio_id BIGINT PRIMARY KEY REFERENCES portfolio(id) ON DELETE CASCADE,
    text VARCHAR(2000) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 0,
    used_revision BIGINT NOT NULL DEFAULT 0,
    claimed_report_id BIGINT,
    claimed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
