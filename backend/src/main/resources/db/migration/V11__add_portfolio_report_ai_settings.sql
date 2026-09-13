ALTER TABLE portfolio_report_schedule
    ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE portfolio_report_schedule
    ADD COLUMN ai_model VARCHAR(120) NOT NULL DEFAULT 'gpt-5.4-mini';

ALTER TABLE portfolio_report_schedule
    ADD COLUMN ai_effort VARCHAR(10) NOT NULL DEFAULT 'low';

ALTER TABLE portfolio_report_schedule
    ADD CONSTRAINT chk_report_ai_effort CHECK (
        ai_effort IN ('low', 'medium', 'high', 'xhigh', 'max', 'ultra')
    );
