create table custom_categories(
    name varchar(100),
    created_at timestamp with time zone not null,
    constraint custom_categories_pkey primary key (name)
);