alter table news_items drop constraint news_items_feed_check;
alter table news_items add constraint news_items_feed_check check (feed in ('PORTFOLIO', 'MARKET', 'CRYPTO'));