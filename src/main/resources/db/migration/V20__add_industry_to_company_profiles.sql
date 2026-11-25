alter table company_profiles  add column industry varchar(100);
update company_profiles set fetched_at = fetched_at - interval '30 days';