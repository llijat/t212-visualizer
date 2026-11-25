create table price_history (
    symbol varchar(32),
    day date,
    close numeric(19,4) not null,
    constraint price_history_pkey primary key (symbol, day)
);