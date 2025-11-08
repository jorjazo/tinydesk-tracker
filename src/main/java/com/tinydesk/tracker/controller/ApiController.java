package com.tinydesk.tracker.controller;

import com.tinydesk.tracker.entity.History;
import com.tinydesk.tracker.entity.Video;
import com.tinydesk.tracker.service.DatabaseService;
import com.tinydesk.tracker.service.SchedulerService;
import com.tinydesk.tracker.service.TinyDeskTrackerService;
import com.tinydesk.tracker.service.YouTubeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for TinyDesk tracker.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {
    
    private final TinyDeskTrackerService trackerService;
    private final DatabaseService databaseService;
    private final SchedulerService schedulerService;
    private final YouTubeService youtubeService;
    
    public ApiController(TinyDeskTrackerService trackerService,
                        DatabaseService databaseService,
                        SchedulerService schedulerService,
                        YouTubeService youtubeService) {
        this.trackerService = trackerService;
        this.databaseService = databaseService;
        this.schedulerService = schedulerService;
        this.youtubeService = youtubeService;
    }
    
    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> getTop(
            @RequestParam(required = false) String playlist,
            @RequestParam(required = false) List<String> playlists) {
        Map<String, Object> metadata = trackerService.getMetadata();
        long lastUpdate = (long) metadata.get("lastUpdate");
        long nextUpdate = schedulerService.computeNextUpdateTimestamp(lastUpdate);
        
        List<Video> videos;
        if (playlists != null && !playlists.isEmpty()) {
            videos = databaseService.getTopVideos(100, playlists);
        } else if (playlist != null && !playlist.isEmpty()) {
            videos = databaseService.getTopVideos(100, playlist);
        } else {
            videos = databaseService.getTopVideos(100);
        }
        
        List<Map<String, Object>> videoList = new ArrayList<>(videos.size());
        for (int i = 0; i < videos.size(); i++) {
            videoList.add(videoToMap(videos.get(i), i + 1));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("videos", videoList);
        response.put("lastUpdate", lastUpdate);
        response.put("nextUpdate", nextUpdate);
        response.put("total", videoList.size());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getData() {
        List<Video> videos = databaseService.getTopVideos(100);
        Map<String, Object> data = new HashMap<>();
        
        for (Video video : videos) {
            List<History> history = databaseService.getVideoHistory(video.getVideoId());
            List<Map<String, Object>> historyList = history.stream()
                    .map(h -> {
                        Map<String, Object> hMap = new HashMap<>();
                        hMap.put("timestamp", h.getTimestamp());
                        hMap.put("viewCount", h.getViewCount());
                        return hMap;
                    })
                    .toList();
            
            Map<String, Object> videoData = new HashMap<>();
            videoData.put("title", video.getTitle());
            videoData.put("currentViews", video.getCurrentViews());
            videoData.put("history", historyList);
            
            data.put(video.getVideoId(), videoData);
        }
        
        data.put("_metadata", trackerService.getMetadata());
        return ResponseEntity.ok(data);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> metadata = trackerService.getMetadata();
        long lastUpdate = (long) metadata.get("lastUpdate");
        long nextUpdate = schedulerService.computeNextUpdateTimestamp(lastUpdate);
        Map<String, Long> stats = databaseService.getStats();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "online");
        response.put("lastUpdate", lastUpdate);
        response.put("nextUpdate", nextUpdate);
        response.put("totalVideos", metadata.get("totalVideos"));
        response.put("dbStats", stats);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/history/{videoId}")
    public ResponseEntity<Map<String, Object>> getVideoHistory(@PathVariable String videoId) {
        List<History> history = databaseService.getVideoHistory(videoId);
        List<Map<String, Object>> historyList = history.stream()
                .map(h -> {
                    Map<String, Object> hMap = new HashMap<>();
                    hMap.put("timestamp", h.getTimestamp());
                    hMap.put("viewCount", h.getViewCount());
                    return hMap;
                })
                .toList();
        
        Map<String, Object> response = new HashMap<>();
        response.put("videoId", videoId);
        response.put("history", historyList);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/ranking-history")
    public ResponseEntity<Map<String, Object>> getRankingHistory() {
        List<Long> timestamps = databaseService.getDistinctTimestamps();
        Map<String, Video> videoLookup = databaseService.getTopVideos(1000).stream()
                .collect(Collectors.toMap(Video::getVideoId, v -> v, (existing, replacement) -> existing));
        
        Map<String, Map<String, Object>> rankingEvolution = new HashMap<>();
        
        for (Long ts : timestamps) {
            List<History> historyAtTimestamp = databaseService.getHistoryAtTimestamp(ts);
            
            // Sort by view count descending
            List<History> sorted = historyAtTimestamp.stream()
                    .sorted((a, b) -> Long.compare(b.getViewCount(), a.getViewCount()))
                    .toList();
            
            int rank = 1;
            for (History h : sorted) {
                String videoId = h.getVideoId();
                
                if (!rankingEvolution.containsKey(videoId)) {
                    Video video = videoLookup.get(videoId);
                    if (video != null) {
                        Map<String, Object> videoData = new HashMap<>();
                        videoData.put("videoId", videoId);
                        videoData.put("title", video.getTitle());
                        videoData.put("publishedAt", video.getPublishedAt());
                        videoData.put("history", new ArrayList<Map<String, Object>>());
                        rankingEvolution.put(videoId, videoData);
                    }
                }
                
                if (rankingEvolution.containsKey(videoId)) {
                    Map<String, Object> historyEntry = new HashMap<>();
                    historyEntry.put("timestamp", ts);
                    historyEntry.put("rank", rank);
                    historyEntry.put("views", h.getViewCount());
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> historyList = 
                            (List<Map<String, Object>>) rankingEvolution.get(videoId).get("history");
                    historyList.add(historyEntry);
                }
                
                rank++;
            }
        }
        
        // Calculate rank changes
        for (Map<String, Object> videoData : rankingEvolution.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> history = (List<Map<String, Object>>) videoData.get("history");
            
            if (history.size() >= 2) {
                int latestRank = (int) history.get(history.size() - 1).get("rank");
                int previousRank = (int) history.get(history.size() - 2).get("rank");
                videoData.put("rankChange", previousRank - latestRank);
                videoData.put("currentRank", latestRank);
                videoData.put("previousRank", previousRank);
            } else if (!history.isEmpty()) {
                int currentRank = (int) history.get(0).get("rank");
                videoData.put("rankChange", 0);
                videoData.put("currentRank", currentRank);
                videoData.put("previousRank", currentRank);
            } else {
                videoData.put("rankChange", 0);
                videoData.put("currentRank", 0);
                videoData.put("previousRank", 0);
            }
        }
        
        List<Map<String, Object>> videosList = new ArrayList<>(rankingEvolution.values());
        List<Map<String, Object>> topMovers = videosList.stream()
                .sorted((a, b) -> Integer.compare((int) b.get("rankChange"), (int) a.get("rankChange")))
                .limit(10)
                .toList();
        
        List<Map<String, Object>> biggestFallers = videosList.stream()
                .sorted((a, b) -> Integer.compare((int) a.get("rankChange"), (int) b.get("rankChange")))
                .limit(10)
                .toList();
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamps", timestamps);
        response.put("evolution", rankingEvolution);
        response.put("topMovers", topMovers);
        response.put("biggestFallers", biggestFallers);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> manualUpdate() {
        new Thread(() -> trackerService.update()).start();
        Map<String, String> response = new HashMap<>();
        response.put("status", "Update started in background");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/add-video/{videoId}")
    public ResponseEntity<Map<String, Object>> addSpecificVideo(
            @PathVariable String videoId,
            @RequestParam(required = false) String playlistId) {
        try {
            var stats = youtubeService.getVideoStatistics(List.of(videoId));
            
            if (stats.getItems() == null || stats.getItems().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Video " + videoId + " not found");
                return ResponseEntity.status(404).body(error);
            }
            
            var item = stats.getItems().get(0);
            String title = item.getSnippet().getTitle();
            long viewCount = Long.parseLong(item.getStatistics().getViewCount());
            String publishedAt = item.getSnippet().getPublishedAt();
            long timestamp = System.currentTimeMillis() / 1000;
            
            // Use provided playlistId or default to first configured playlist
            String finalPlaylistId = playlistId;
            if (finalPlaylistId == null || finalPlaylistId.isEmpty()) {
                finalPlaylistId = databaseService.getDistinctPlaylistIds().stream().findFirst().orElse("unknown");
            }
            
            databaseService.saveVideo(videoId, title, viewCount, timestamp, publishedAt, finalPlaylistId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Video added successfully");
            response.put("videoId", videoId);
            response.put("title", title);
            response.put("views", viewCount);
            response.put("playlistId", finalPlaylistId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adding video", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        List<Video> allVideos = databaseService.getTopVideos(1000);
        
        List<Map<String, Object>> videosWithMetrics = new ArrayList<>();
        long totalViews = 0;
        
        for (int i = 0; i < allVideos.size(); i++) {
            Video video = allVideos.get(i);
            int rank = i + 1;
            long currentViews = video.getCurrentViews();
            totalViews += currentViews;
            
            List<History> history = databaseService.getVideoHistory(video.getVideoId());
            
            double viewsPerHour = 0.0;
            long viewsChange = 0;
            double hoursSinceLastUpdate = 0;
            
            if (history.size() >= 2) {
                History latest = history.get(history.size() - 1);
                History previous = history.get(history.size() - 2);
                long timeDiff = latest.getTimestamp() - previous.getTimestamp();
                hoursSinceLastUpdate = timeDiff / 3600.0;
                if (hoursSinceLastUpdate > 0) {
                    viewsChange = latest.getViewCount() - previous.getViewCount();
                    viewsPerHour = viewsChange / hoursSinceLastUpdate;
                }
            }
            
            double lifetimeViewsPerHour = 0.0;
            if (video.getPublishedAt() != null && !video.getPublishedAt().isEmpty()) {
                try {
                    Instant published = Instant.parse(video.getPublishedAt());
                    Instant now = Instant.now();
                    double ageHours = ChronoUnit.HOURS.between(published, now);
                    if (ageHours > 0) {
                        lifetimeViewsPerHour = currentViews / ageHours;
                    }
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
            
            Map<String, Object> videoMetrics = new HashMap<>();
            videoMetrics.put("videoId", video.getVideoId());
            videoMetrics.put("title", video.getTitle());
            videoMetrics.put("currentViews", currentViews);
            videoMetrics.put("currentRank", rank);
            videoMetrics.put("publishedAt", video.getPublishedAt());
            videoMetrics.put("viewsPerHour", Math.round(viewsPerHour * 100.0) / 100.0);
            videoMetrics.put("viewsChange", viewsChange);
            videoMetrics.put("hoursSinceLastUpdate", Math.round(hoursSinceLastUpdate * 100.0) / 100.0);
            videoMetrics.put("lifetimeViewsPerHour", Math.round(lifetimeViewsPerHour * 100.0) / 100.0);
            
            videosWithMetrics.add(videoMetrics);
        }
        
        List<Map<String, Object>> trending = videosWithMetrics.stream()
                .sorted((a, b) -> Double.compare((double) b.get("viewsPerHour"), (double) a.get("viewsPerHour")))
                .limit(10)
                .toList();
        
        List<Map<String, Object>> topPerformers = videosWithMetrics.stream()
                .sorted((a, b) -> Double.compare((double) b.get("lifetimeViewsPerHour"), (double) a.get("lifetimeViewsPerHour")))
                .limit(10)
                .toList();
        
        double averageViews = allVideos.isEmpty() ? 0 : (double) totalViews / allVideos.size();
        double averageGrowth = videosWithMetrics.isEmpty() ? 0 :
                videosWithMetrics.stream().mapToDouble(v -> (double) v.get("viewsPerHour")).average().orElse(0);
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalVideos", allVideos.size());
        statistics.put("totalViews", totalViews);
        statistics.put("averageViews", Math.round(averageViews * 100.0) / 100.0);
        statistics.put("averageViewsPerHour", Math.round(averageGrowth * 100.0) / 100.0);
        statistics.put("lastUpdate", trackerService.getMetadata().get("lastUpdate"));
        
        Map<String, Object> response = new HashMap<>();
        response.put("trending", trending);
        response.put("topPerformers", topPerformers);
        response.put("statistics", statistics);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/playlists")
    public ResponseEntity<Map<String, Object>> getPlaylists() {
        List<String> playlistIds = databaseService.getDistinctPlaylistIds();
        
        Map<String, Object> response = new HashMap<>();
        response.put("playlists", playlistIds);
        
        return ResponseEntity.ok(response);
    }
    
    private Map<String, Object> videoToMap(Video video, int rank) {
        Map<String, Object> map = new HashMap<>();
        map.put("rank", rank);
        map.put("videoId", video.getVideoId());
        map.put("title", video.getTitle());
        map.put("views", video.getCurrentViews());
        map.put("publishedAt", video.getPublishedAt());
        map.put("playlistId", video.getPlaylistId());
        map.put("url", "https://www.youtube.com/watch?v=" + video.getVideoId());
        return map;
    }
}
