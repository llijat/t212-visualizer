create table budgets (
    category varchar(100),
    monthly_limit numeric(19,2) not null,
    constraint budgets_pkey primary key(category),
    constraint budgets_limit_check check (monthly_limit > 0)
);