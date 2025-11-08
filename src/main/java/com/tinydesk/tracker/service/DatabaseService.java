package com.tinydesk.tracker.service;

import com.tinydesk.tracker.entity.History;
import com.tinydesk.tracker.entity.Lock;
import com.tinydesk.tracker.entity.Metadata;
import com.tinydesk.tracker.entity.Video;
import com.tinydesk.tracker.repository.HistoryRepository;
import com.tinydesk.tracker.repository.LockRepository;
import com.tinydesk.tracker.repository.MetadataRepository;
import com.tinydesk.tracker.repository.VideoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for database operations.
 */
@Service
@Slf4j
public class DatabaseService {
    
    private static final String LOCK_NAME = "tinydesk_update_lock";
    
    private final VideoRepository videoRepository;
    private final HistoryRepository historyRepository;
    private final MetadataRepository metadataRepository;
    private final LockRepository lockRepository;
    
    public DatabaseService(VideoRepository videoRepository,
                          HistoryRepository historyRepository,
                          MetadataRepository metadataRepository,
                          LockRepository lockRepository) {
        this.videoRepository = videoRepository;
        this.historyRepository = historyRepository;
        this.metadataRepository = metadataRepository;
        this.lockRepository = lockRepository;
    }
    
    /**
     * Save or update video with view count history.
     */
    @Transactional
    public void saveVideo(String videoId, String title, Long viewCount, Long timestamp, String publishedAt) {
        Video video = videoRepository.findById(videoId).orElse(new Video());
        video.setVideoId(videoId);
        video.setTitle(title);
        video.setCurrentViews(viewCount);
        video.setLastUpdated(timestamp);
        if (publishedAt != null && (video.getPublishedAt() == null || video.getPublishedAt().isEmpty())) {
            video.setPublishedAt(publishedAt);
        }
        videoRepository.save(video);
        
        // Add history entry
        History history = new History();
        history.setVideoId(videoId);
        history.setTimestamp(timestamp);
        history.setViewCount(viewCount);
        historyRepository.save(history);
        
        // Keep only last 100 history entries per video
        try {
            historyRepository.deleteOldHistoryEntries(videoId, 100);
        } catch (Exception e) {
            log.warn("Could not delete old history entries for {}: {}", videoId, e.getMessage());
        }
    }
    
    /**
     * Get top N videos by view count.
     */
    public List<Video> getTopVideos(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return videoRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Order.desc("currentViews"))))
                .getContent();
    }
    
    /**
     * Get view history for a specific video.
     */
    public List<History> getVideoHistory(String videoId) {
        return historyRepository.findByVideoIdOrderByTimestampAsc(videoId);
    }
    
    /**
     * Set metadata key-value pair.
     */
    @Transactional
    public void setMetadata(String key, String value) {
        Metadata metadata = metadataRepository.findByKey(key)
                .orElse(new Metadata(key, value));
        metadata.setValue(value);
        metadataRepository.save(metadata);
    }
    
    /**
     * Get metadata value by key.
     */
    public Optional<String> getMetadata(String key) {
        return metadataRepository.findByKey(key).map(Metadata::getValue);
    }
    
    /**
     * Get all metadata as a map.
     */
    public Map<String, String> getAllMetadata() {
        Map<String, String> result = new HashMap<>();
        metadataRepository.findAll().forEach(m -> result.put(m.getKey(), m.getValue()));
        return result;
    }
    
    /**
     * Get database statistics.
     */
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total_videos", videoRepository.count());
        stats.put("total_history_entries", historyRepository.count());
        return stats;
    }
    
    /**
     * Try to acquire update lock.
     */
    @Transactional
    public Optional<Lock> acquireUpdateLock(int ttlSeconds) {
        String owner = getHostname();
        long now = System.currentTimeMillis() / 1000;
        long expiresAt = now + ttlSeconds;
        
        // Clean expired locks first
        lockRepository.deleteExpiredLocks(now);
        
        Optional<Lock> existingLock = lockRepository.findByKey(LOCK_NAME);
        
        if (existingLock.isEmpty()) {
            // Create new lock
            Lock lock = new Lock(LOCK_NAME, owner, expiresAt);
            lockRepository.save(lock);
            log.info("Acquired update lock for owner: {}", owner);
            return Optional.of(lock);
        } else {
            Lock lock = existingLock.get();
            if (lock.getExpiresAt() < now) {
                // Lock expired, take over
                lock.setOwner(owner);
                lock.setExpiresAt(expiresAt);
                lockRepository.save(lock);
                log.info("Acquired expired lock for owner: {}", owner);
                return Optional.of(lock);
            } else {
                log.debug("Lock already held by: {}", lock.getOwner());
                return Optional.empty();
            }
        }
    }
    
    /**
     * Release update lock.
     */
    @Transactional
    public void releaseUpdateLock(Lock lock) {
        if (lock != null) {
            lockRepository.deleteByKeyAndOwner(lock.getKey(), lock.getOwner());
            log.info("Released update lock for owner: {}", lock.getOwner());
        }
    }
    
    /**
     * Get all distinct timestamps from history.
     */
    public List<Long> getDistinctTimestamps() {
        return historyRepository.findDistinctTimestamps();
    }
    
    /**
     * Get history entries at a specific timestamp.
     */
    public List<History> getHistoryAtTimestamp(Long timestamp) {
        return historyRepository.findByTimestamp(timestamp);
    }
    
    /**
     * Get hostname for lock ownership.
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
