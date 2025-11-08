package com.tinydesk.tracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for YouTube Video API response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouTubeVideoResponse {
    
    private List<VideoItem> items;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoItem {
        private String id;
        private Snippet snippet;
        private Statistics statistics;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Snippet {
            private String title;
            private String publishedAt;
        }
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Statistics {
            @JsonProperty("viewCount")
            private String viewCount;
        }
    }
}
