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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
     * Snapshot of a Tiny Desk video fetched from the API.
     */
    public record VideoSnapshot(String videoId, String title, long viewCount, String publishedAt, String playlistId) { }
    
    /**
     * Fetch all videos from all configured playlists and collect them into a list.
     */
    public List<VideoSnapshot> fetchAllVideos() {
        List<VideoSnapshot> snapshots = new ArrayList<>();
        Map<String, String> playlists = appConfig.getYoutube().getAllPlaylists();
        for (Map.Entry<String, String> playlist : playlists.entrySet()) {
            streamPlaylistVideos(playlist.getValue(), playlist.getKey(), snapshots::add);
        }
        return snapshots;
    }
    
    /**
     * Stream videos from a specific playlist, invoking the provided consumer
     * for each video as soon as it is retrieved.
     *
     * @param playlistId the playlist ID to fetch from
     * @param playlistName the playlist name for logging
     * @param consumer consumer invoked for every fetched video
     * @return total number of videos processed
     */
    public long streamPlaylistVideos(String playlistId, String playlistName, Consumer<VideoSnapshot> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        
        String pageToken = null;
        int pageNum = 1;
        long processed = 0L;
        
        log.info("\n" + "=".repeat(50));
        log.info("Fetching videos from playlist: {}", playlistName);
        log.info("Playlist ID: {}", playlistId);
        log.info("=".repeat(50));
        
        while (true) {
            log.info("\nFetching page {}...", pageNum);
            
            YouTubePlaylistResponse playlistResponse = youtubeService.getPlaylistVideos(
                    playlistId, pageToken);
            
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
                        if (item == null || item.getId() == null) {
                            continue;
                        }
                        String title = "";
                        String publishedAt = "";
                        if (item.getSnippet() != null) {
                            title = Optional.ofNullable(item.getSnippet().getTitle()).orElse("");
                            publishedAt = Optional.ofNullable(item.getSnippet().getPublishedAt()).orElse("");
                        }
                        
                        long viewCount = 0L;
                        if (item.getStatistics() != null && item.getStatistics().getViewCount() != null) {
                            try {
                                viewCount = Long.parseLong(item.getStatistics().getViewCount());
                            } catch (NumberFormatException e) {
                                log.debug("Could not parse view count for video {}: {}", item.getId(), e.getMessage());
                            }
                        }
                        
                        consumer.accept(new VideoSnapshot(item.getId(), title, viewCount, publishedAt, playlistId));
                        processed++;
                    }
                    log.info("✓ Fetched stats for {} videos", statsResponse.getItems().size());
                }
            }
            
            log.info("  Total Tiny Desk videos processed so far: {}", processed);
            
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
        log.info("Total {} videos processed: {}", playlistName, processed);
        log.info("=".repeat(50) + "\n");
        
        return processed;
    }
    
    /**
     * Update historical data by fetching and saving videos.
     */
    public void updateHistoricalData(Collection<VideoSnapshot> videos) {
        long timestamp = System.currentTimeMillis() / 1000;
        
        log.info("Saving to database...");
        AtomicInteger count = new AtomicInteger();
        videos.forEach(video -> {
            databaseService.saveVideo(video.videoId(), video.title(), video.viewCount(), timestamp, video.publishedAt(), video.playlistId());
            int processed = count.incrementAndGet();
            if (processed % 10 == 0) {
                log.info("  Saved {}/{} videos...", processed, videos.size());
            }
        });
        
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
                AtomicInteger processed = new AtomicInteger();
                long timestamp = System.currentTimeMillis() / 1000;
                
                Map<String, String> playlists = appConfig.getYoutube().getAllPlaylists();
                long totalVideos = 0;
                for (Map.Entry<String, String> playlist : playlists.entrySet()) {
                    totalVideos += streamPlaylistVideos(playlist.getValue(), playlist.getKey(), video -> {
                        databaseService.saveVideo(video.videoId(), video.title(), video.viewCount(), timestamp, video.publishedAt(), video.playlistId());
                        int saved = processed.incrementAndGet();
                        if (saved % 10 == 0) {
                            log.info("  Saved {} videos so far...", saved);
                        }
                    });
                }
                
                databaseService.setMetadata("lastUpdate", String.valueOf(timestamp));
                databaseService.setMetadata("totalVideos", String.valueOf(totalVideos));
                
                log.info("✓ All {} videos saved to database", totalVideos);
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
