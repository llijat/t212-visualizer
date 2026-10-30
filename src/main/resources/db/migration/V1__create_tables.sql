CREATE TABLE account_transactions (
    reference   VARCHAR(255) NOT NULL,
    type        VARCHAR(255),
    amount      DOUBLE PRECISION NOT NULL,
    currency    VARCHAR(255),
    date_time   TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT account_transactions_pkey PRIMARY KEY (reference)
);

create table positions (
    ticker varchar(255) not null,
    name varchar(255),
    quantity double precision not null,
    average_price_paid double precision not null,
    current_price double precision not null,
    current_value double precision not null,
    unrealized_profit_loss double precision not null,
    fx_impact double precision,
    last_updated TIMESTAMP(6) WITH TIME ZONE,
    total_cost double precision not null,
    currency_type varchar(255),
    account_currency varchar(255),
    CONSTRAINT positions_pkey PRIMARY KEY (ticker)
);