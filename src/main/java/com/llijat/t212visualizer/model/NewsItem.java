package com.llijat.t212visualizer.model;


import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "news_items")
public class NewsItem {
    @Id
    private Long id;
    @Enumerated(EnumType.STRING)
    private NewsFeed feed;
    private String related;
    private String headline;
    private String summary;
    private String url;
    private String source;
    private Instant publishedAt;
    private boolean starred;

    protected NewsItem() {

    }

    public NewsItem(FinnhubNewsDto news, NewsFeed feed) {
        this.id = news.id();
        this.feed = feed;
        this.related = news.related();
        this.headline = news.headline();
        this.summary = news.summary();
        this.url = news.url();
        this.source = news.source();
        this.publishedAt = Instant.ofEpochSecond(news.datetime());
    }

    public FinnhubNewsDto toDto() {
        return new FinnhubNewsDto("",publishedAt.getEpochSecond(),headline,url,id,related,"",summary,source);
    }

    public void changeStarred(boolean starred) {
        this.starred = starred;
    }

    public Long getId() {return id;}
    public NewsFeed getFeed() {return feed;}
    public Instant getPublishedAt() {return publishedAt;}
    public boolean isStarred() {return starred;}
}
