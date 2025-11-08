package com.tinydesk.tracker.repository;

import com.tinydesk.tracker.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Video entities.
 */
@Repository
public interface VideoRepository extends JpaRepository<Video, String> {
    
    /**
     * Find top N videos ordered by current views descending.
     */
    List<Video> findTopByOrderByCurrentViewsDesc();
    
    /**
     * Get total video count.
     */
    long count();
}
