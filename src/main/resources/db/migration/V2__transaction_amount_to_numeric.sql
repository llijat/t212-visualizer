alter table account_transactions
alter column amount type numeric(19,4)
      using round(amount::numeric, 4);