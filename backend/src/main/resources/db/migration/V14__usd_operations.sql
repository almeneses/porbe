ALTER TABLE portfolio_operation ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'COP';
ALTER TABLE portfolio_operation ADD CONSTRAINT chk_operation_currency CHECK (currency IN ('COP', 'USD'));
ALTER TABLE portfolio_operation DROP CONSTRAINT chk_portfolio_operation_type;
ALTER TABLE portfolio_operation ADD CONSTRAINT chk_portfolio_operation_type CHECK (operation_type IN ('COMPRA', 'VENTA', 'DIVIDENDO', 'DEPOSITO', 'RETIRO', 'COMPRA_USD', 'VENTA_USD'));
