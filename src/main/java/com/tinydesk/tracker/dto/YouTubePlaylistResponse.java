package com.tinydesk.tracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for YouTube Playlist API response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouTubePlaylistResponse {
    
    private List<PlaylistItem> items;
    
    @JsonProperty("nextPageToken")
    private String nextPageToken;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaylistItem {
        private Snippet snippet;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Snippet {
            private ResourceId resourceId;
            
            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ResourceId {
                private String videoId;
            }
        }
    }
}
