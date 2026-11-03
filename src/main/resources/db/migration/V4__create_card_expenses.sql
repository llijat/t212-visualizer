CREATE TABLE card_expenses (
    reference        VARCHAR(255),
    action   VARCHAR(255) NOT NULL,
    date_time TIMESTAMP(6) with time zone not null,
    amount    NUMERIC(19,4) not null,
    currency VARCHAR(255),
    merchant_name varchar(255),
    merchant_category varchar(255),
    CONSTRAINT card_expenses_pkey PRIMARY KEY (reference)
);

