package com.tinydesk.tracker.repository;

import com.tinydesk.tracker.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Find videos by playlist ID with pagination.
     */
    Page<Video> findByPlaylistId(String playlistId, Pageable pageable);
    
    /**
     * Find videos where playlist ID is in the given list.
     */
    Page<Video> findByPlaylistIdIn(List<String> playlistIds, Pageable pageable);
    
    /**
     * Get distinct playlist IDs.
     */
    @Query("SELECT DISTINCT v.playlistId FROM Video v")
    List<String> findDistinctPlaylistIds();
    
    /**
     * Get total video count.
     */
    long count();
}
