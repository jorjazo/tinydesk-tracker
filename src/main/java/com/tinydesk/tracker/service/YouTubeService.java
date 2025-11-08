package com.tinydesk.tracker.service;

import com.tinydesk.tracker.config.AppConfig;
import com.tinydesk.tracker.dto.YouTubePlaylistResponse;
import com.tinydesk.tracker.dto.YouTubeVideoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Service for interacting with the YouTube Data API v3.
 */
@Service
@Slf4j
public class YouTubeService {
    
    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";
    
    private final RestTemplate restTemplate;
    private final AppConfig appConfig;
    
    public YouTubeService(RestTemplate restTemplate, AppConfig appConfig) {
        this.restTemplate = restTemplate;
        this.appConfig = appConfig;
    }
    
    /**
     * Get playlist videos by playlist ID with pagination.
     */
    public YouTubePlaylistResponse getPlaylistVideos(String playlistId, String pageToken) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/playlistItems")
                .queryParam("part", "snippet")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", appConfig.getYoutube().getMaxResultsPerRequest())
                .queryParam("key", appConfig.getYoutube().getApiKey())
                .queryParamIfPresent("pageToken", pageToken != null ? java.util.Optional.of(pageToken) : java.util.Optional.empty())
                .build()
                .toUriString();
        
        log.debug("Fetching playlist videos from: {}", url.replaceAll("key=[^&]+", "key=***"));
        return restTemplate.getForObject(url, YouTubePlaylistResponse.class);
    }
    
    /**
     * Get video statistics for multiple video IDs.
     */
    public YouTubeVideoResponse getVideoStatistics(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            YouTubeVideoResponse response = new YouTubeVideoResponse();
            response.setItems(List.of());
            return response;
        }
        
        String ids = String.join(",", videoIds);
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/videos")
                .queryParam("part", "snippet,statistics")
                .queryParam("id", ids)
                .queryParam("key", appConfig.getYoutube().getApiKey())
                .build()
                .toUriString();
        
        log.debug("Fetching video statistics for {} videos", videoIds.size());
        return restTemplate.getForObject(url, YouTubeVideoResponse.class);
    }
}
