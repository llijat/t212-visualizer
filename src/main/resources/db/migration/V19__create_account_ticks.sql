create table account_ticks (
    taken_at timestamp with time zone,
    total_value numeric(19,4) not null,
    net_deposits numeric(19,4) not null,
    constraint account_ticks_pkey primary key (taken_at)
);