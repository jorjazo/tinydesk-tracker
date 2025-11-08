package com.tinydesk.tracker.repository;

import com.tinydesk.tracker.entity.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for History entities.
 */
@Repository
public interface HistoryRepository extends JpaRepository<History, Long> {
    
    /**
     * Find history entries for a specific video ordered by timestamp ascending.
     */
    List<History> findByVideoIdOrderByTimestampAsc(String videoId);
    
    /**
     * Find all distinct timestamps in history table.
     */
    @Query("SELECT DISTINCT h.timestamp FROM History h ORDER BY h.timestamp ASC")
    List<Long> findDistinctTimestamps();
    
    /**
     * Find history entries at a specific timestamp.
     */
    List<History> findByTimestamp(Long timestamp);
    
    /**
     * Delete old history entries for a video, keeping only the most recent N entries.
     */
    @Modifying
    @Query(value = "DELETE FROM history WHERE id IN (" +
           "  SELECT id FROM (" +
           "    SELECT id, ROW_NUMBER() OVER (PARTITION BY video_id ORDER BY timestamp DESC) as rn " +
           "    FROM history WHERE video_id = :videoId" +
           "  ) WHERE rn > :keepCount" +
           ")", nativeQuery = true)
    void deleteOldHistoryEntries(@Param("videoId") String videoId, @Param("keepCount") int keepCount);
    
    /**
     * Count total history entries.
     */
    long count();
}
