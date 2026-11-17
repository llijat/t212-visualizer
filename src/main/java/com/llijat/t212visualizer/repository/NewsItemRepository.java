package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.NewsFeed;
import com.llijat.t212visualizer.model.NewsItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {
    List<NewsItem> findByFeedAndPublishedAtAfterOrderByPublishedAtDesc(NewsFeed feed, Instant after);
    List<NewsItem> findTop20ByFeedOrderByPublishedAtDesc(NewsFeed feed);
    List<NewsItem> findByStarredTrueOrderByPublishedAtDesc();

    @Transactional
    long deleteByStarredFalseAndPublishedAtBefore(Instant before);
}
