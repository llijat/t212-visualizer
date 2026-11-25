alter table company_profiles add column symbol varchar(100);
alter table company_profiles add column exchange varchar(100);
update company_profiles set fetched_at = fetched_at - interval '30 days';