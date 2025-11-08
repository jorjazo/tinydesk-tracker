package com.tinydesk.tracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application configuration properties for TinyDesk tracker.
 */
@Configuration
@ConfigurationProperties(prefix = "tinydesk")
@Data
public class AppConfig {
    
    private YoutubeConfig youtube = new YoutubeConfig();
    private SchedulerConfig scheduler = new SchedulerConfig();
    
    @Data
    public static class YoutubeConfig {
        private String apiKey;
        private String channelId;
        private String playlistId; // Deprecated: use playlists instead
        private Map<String, String> playlists = new LinkedHashMap<>();
        private int maxResultsPerRequest = 50;
        private int totalVideosToFetch = 1000;
        
        /**
         * Get all playlists to track. Falls back to legacy single playlistId if no playlists configured.
         */
        public Map<String, String> getAllPlaylists() {
            if (!playlists.isEmpty()) {
                return playlists;
            }
            // Fallback to legacy single playlist configuration
            if (playlistId != null && !playlistId.isEmpty()) {
                Map<String, String> legacy = new LinkedHashMap<>();
                legacy.put("Tiny Desk Concerts", playlistId);
                return legacy;
            }
            return new LinkedHashMap<>();
        }
    }
    
    @Data
    public static class SchedulerConfig {
        private double updateIntervalHours = 6.0;
        private String updateCron = "0 */30 * * * *";
        private String updateSchedule = "";
        private int lockTtlSeconds = 7200;
        private boolean enabled = true;
    }
}
