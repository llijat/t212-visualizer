create table account_snapshots(
    snapshot_date date,
    total_value numeric(19, 4) not null,
    net_deposits numeric(19,4) not null,
    taken_at timestamp with time zone not null,
    constraint account_snapshots_pkey primary key (snapshot_date)
);