CREATE TABLE portfolio_report_schedule (
    schedule_key VARCHAR(40) PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,
    run_time TIME NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    last_run_at TIMESTAMP WITH TIME ZONE,
    last_run_status VARCHAR(20),
    last_run_message VARCHAR(500),
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_report_schedule_day CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    )
);

INSERT INTO portfolio_report_schedule (
    schedule_key,
    enabled,
    day_of_week,
    run_time,
    timezone,
    updated_by
) VALUES (
    'WEEKLY_REPORT',
    TRUE,
    'FRIDAY',
    '17:30:00',
    'America/Bogota',
    'system'
);
