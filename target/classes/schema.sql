
    create table portfolio (
        cash float,
        total float,
        id integer,
        owner varchar(255) not null,
        primary key (id)
    );

    create table portfolio_operations (
        portfolio_id bigint not null,
        stock_operation_id bigint not null,
        primary key (portfolio_id, stock_operation_id)
    );

    create table portfolio_stock (
        quantity float not null,
        created_at timestamp,
        portfolio_id bigint not null,
        stock_id bigint not null,
        primary key (portfolio_id, stock_id)
    );

    create table report (
        is_wa_message_sent boolean not null,
        week_ending_date date not null,
        created_at timestamp not null,
        id integer,
        total_gain_loss bigint not null,
        best_perf_stock varchar(255) not null,
        report_image_path varchar(255),
        worst_perf_stock varchar(255) not null,
        primary key (id)
    );

    create table stock (
        id integer,
        currency varchar(255),
        name varchar(255) not null,
        ticker varchar(255) not null unique,
        primary key (id)
    );

    create table stock_price (
        close_price float not null,
        date date not null,
        created_at timestamp not null,
        id integer,
        stock_id bigint not null,
        primary key (id)
    );

    create table stock_operation (
        comission float not null,
        price float not null,
        quantity integer not null,
        total float not null,
        created_at timestamp not null,
        date timestamp not null,
        id integer,
        stock_ticker bigint not null,
        type varchar(255) not null,
        primary key (id)
    );
