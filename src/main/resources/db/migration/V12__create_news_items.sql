create table news_items (
    id bigint,
    feed varchar(20) not null,
    related varchar(50),
    headline text not null,
    summary text,
    url text not null,
    source varchar(255),
    published_at timestamp(6) with time zone not null,
    constraint news_items_pkey primary key(id),
    constraint news_items_feed_check check (feed in ('PORTFOLIO', 'MARKET'))
);

create index news_items_feed_published_idx on news_items(feed, published_at);