create table card_cashback (
       reference varchar(255),
       date_time timestamp(6) with time zone not null,
       amount numeric(19,4) not null,
       currency varchar(255),
       constraint card_cashback_pkey primary key (reference),
       constraint card_cashback_amount_check check (amount > 0)
);