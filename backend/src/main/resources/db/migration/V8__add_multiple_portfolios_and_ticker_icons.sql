INSERT INTO portfolio (name, base_currency)
SELECT 'Portafolio principal', 'COP'
WHERE NOT EXISTS (SELECT 1 FROM portfolio);

ALTER TABLE import_batch DROP CONSTRAINT IF EXISTS import_batch_file_hash_key;
CREATE UNIQUE INDEX uq_import_batch_portfolio_file_hash
    ON import_batch(portfolio_id, file_hash);

ALTER TABLE operation_audit ADD COLUMN portfolio_id BIGINT;
UPDATE operation_audit audit
SET portfolio_id = COALESCE(
    (SELECT batch.portfolio_id FROM import_batch batch WHERE batch.id = audit.import_batch_id),
    (SELECT MIN(id) FROM portfolio)
);
ALTER TABLE operation_audit ALTER COLUMN portfolio_id SET NOT NULL;
ALTER TABLE operation_audit
    ADD CONSTRAINT fk_operation_audit_portfolio
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE;
CREATE INDEX idx_operation_audit_portfolio_created
    ON operation_audit(portfolio_id, created_at DESC);

ALTER TABLE portfolio_report ADD COLUMN portfolio_id BIGINT;
ALTER TABLE portfolio_report ADD COLUMN portfolio_name VARCHAR(120);
UPDATE portfolio_report
SET portfolio_id = (SELECT MIN(id) FROM portfolio),
    portfolio_name = (SELECT name FROM portfolio ORDER BY id LIMIT 1);
ALTER TABLE portfolio_report ALTER COLUMN portfolio_id SET NOT NULL;
ALTER TABLE portfolio_report ALTER COLUMN portfolio_name SET NOT NULL;
ALTER TABLE portfolio_report
    ADD CONSTRAINT fk_portfolio_report_portfolio
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE;
CREATE INDEX idx_portfolio_report_portfolio_created
    ON portfolio_report(portfolio_id, created_at DESC);

ALTER TABLE market_instrument ADD COLUMN icon_data BYTEA;
ALTER TABLE market_instrument ADD COLUMN icon_content_type VARCHAR(40);
ALTER TABLE market_instrument ADD COLUMN icon_updated_at TIMESTAMP WITH TIME ZONE;
