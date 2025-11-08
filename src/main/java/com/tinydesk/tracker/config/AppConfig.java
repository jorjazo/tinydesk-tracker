package com.tinydesk.tracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
        private String playlistId;
        private int maxResultsPerRequest = 50;
        private int totalVideosToFetch = 1000;
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
