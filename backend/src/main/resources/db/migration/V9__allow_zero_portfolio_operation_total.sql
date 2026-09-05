alter table portfolio_operation
    drop constraint chk_portfolio_operation_total;

alter table portfolio_operation
    add constraint chk_portfolio_operation_total
        check (total_amount >= 0);