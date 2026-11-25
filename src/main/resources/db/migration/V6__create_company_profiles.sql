create table company_profiles(
    ticker varchar(255),
    name varchar(255),
    logo bytea,
    logo_content_type varchar(100),
    fetched_at timestamp(6) with time zone not null ,
    constraint company_profiles_pkey primary key(ticker),
    constraint company_profiles_logo_check check ((logo is null) = (logo_content_type is null))
);