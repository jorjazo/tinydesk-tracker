package com.tinydesk.tracker.service;

import com.tinydesk.tracker.config.AppConfig;
import com.tinydesk.tracker.dto.YouTubePlaylistResponse;
import com.tinydesk.tracker.dto.YouTubeVideoResponse;
import com.tinydesk.tracker.entity.Lock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Main service for tracking Tiny Desk concerts.
 */
@Service
@Slf4j
public class TinyDeskTrackerService {
    
    private final DatabaseService databaseService;
    private final YouTubeService youtubeService;
    private final AppConfig appConfig;
    
    public TinyDeskTrackerService(DatabaseService databaseService,
                                  YouTubeService youtubeService,
                                  AppConfig appConfig) {
        this.databaseService = databaseService;
        this.youtubeService = youtubeService;
        this.appConfig = appConfig;
    }
    
    /**
     * Fetch all videos from the Tiny Desk playlist.
     */
    public List<Map<String, Object>> fetchAllVideos() {
        List<Map<String, Object>> allVideos = new ArrayList<>();
        String pageToken = null;
        int pageNum = 1;
        
        log.info("\n" + "=".repeat(50));
        log.info("Fetching Tiny Desk concerts from official playlist...");
        log.info("Playlist ID: {}", appConfig.getYoutube().getPlaylistId());
        log.info("=".repeat(50));
        
        while (true) {
            log.info("\nFetching page {}...", pageNum);
            
            YouTubePlaylistResponse playlistResponse = youtubeService.getPlaylistVideos(
                    appConfig.getYoutube().getPlaylistId(), pageToken);
            
            if (playlistResponse == null || playlistResponse.getItems() == null || playlistResponse.getItems().isEmpty()) {
                log.info("No more results available");
                break;
            }
            
            List<String> videoIds = playlistResponse.getItems().stream()
                    .filter(item -> item.getSnippet() != null && item.getSnippet().getResourceId() != null)
                    .map(item -> item.getSnippet().getResourceId().getVideoId())
                    .filter(Objects::nonNull)
                    .toList();
            
            log.info("Found {} videos on this page", videoIds.size());
            
            if (!videoIds.isEmpty()) {
                YouTubeVideoResponse statsResponse = youtubeService.getVideoStatistics(videoIds);
                
                if (statsResponse != null && statsResponse.getItems() != null) {
                    for (YouTubeVideoResponse.VideoItem item : statsResponse.getItems()) {
                        Map<String, Object> video = new HashMap<>();
                        video.put("videoId", item.getId());
                        video.put("title", item.getSnippet() != null ? item.getSnippet().getTitle() : "");
                        video.put("viewCount", item.getStatistics() != null ? 
                                Long.parseLong(item.getStatistics().getViewCount()) : 0L);
                        video.put("publishedAt", item.getSnippet() != null ? item.getSnippet().getPublishedAt() : "");
                        allVideos.add(video);
                    }
                    log.info("✓ Fetched stats for {} videos", statsResponse.getItems().size());
                }
            }
            
            log.info("  Total Tiny Desk videos collected so far: {}", allVideos.size());
            
            pageToken = playlistResponse.getNextPageToken();
            if (pageToken == null || pageToken.isEmpty()) {
                log.info("No more pages available");
                break;
            }
            
            pageNum++;
            
            // Sleep to respect API rate limits
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("\n" + "=".repeat(50));
        log.info("Total Tiny Desk playlist videos fetched: {}", allVideos.size());
        log.info("=".repeat(50) + "\n");
        
        return allVideos;
    }
    
    /**
     * Update historical data by fetching and saving videos.
     */
    public void updateHistoricalData(List<Map<String, Object>> videos) {
        long timestamp = System.currentTimeMillis() / 1000;
        
        log.info("Saving to database...");
        int count = 0;
        for (Map<String, Object> video : videos) {
            String videoId = (String) video.get("videoId");
            String title = (String) video.get("title");
            Long viewCount = (Long) video.get("viewCount");
            String publishedAt = (String) video.get("publishedAt");
            
            databaseService.saveVideo(videoId, title, viewCount, timestamp, publishedAt);
            count++;
            
            if (count % 10 == 0) {
                log.info("  Saved {}/{} videos...", count, videos.size());
            }
        }
        
        databaseService.setMetadata("lastUpdate", String.valueOf(timestamp));
        databaseService.setMetadata("totalVideos", String.valueOf(videos.size()));
        
        log.info("✓ All {} videos saved to database", videos.size());
    }
    
    /**
     * Perform a complete update cycle.
     */
    public void update() {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            String startTime = formatter.format(Instant.now());
            
            log.info("\n[{}] Starting update...", startTime);
            
            Optional<Lock> lockOpt = databaseService.acquireUpdateLock(
                    appConfig.getScheduler().getLockTtlSeconds());
            
            if (lockOpt.isEmpty()) {
                log.info("↷ Skipping update: lock not acquired (another instance is updating)");
                return;
            }
            
            Lock lock = lockOpt.get();
            try {
                List<Map<String, Object>> videos = fetchAllVideos();
                updateHistoricalData(videos);
            } finally {
                databaseService.releaseUpdateLock(lock);
            }
            
            String endTime = formatter.format(Instant.now());
            log.info("✓ Update completed successfully at {}", endTime);
            
        } catch (Exception e) {
            log.error("✗ Error during update", e);
        }
    }
    
    /**
     * Get metadata as a map.
     */
    public Map<String, Object> getMetadata() {
        Map<String, String> metadata = databaseService.getAllMetadata();
        Map<String, Object> result = new HashMap<>();
        result.put("lastUpdate", Long.parseLong(metadata.getOrDefault("lastUpdate", "0")));
        result.put("totalVideos", Integer.parseInt(metadata.getOrDefault("totalVideos", "0")));
        return result;
    }
}
