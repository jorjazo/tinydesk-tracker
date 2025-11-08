package com.tinydesk.tracker.service;

import com.tinydesk.tracker.config.AppConfig;
import com.tinydesk.tracker.dto.YouTubePlaylistResponse;
import com.tinydesk.tracker.dto.YouTubeVideoResponse;
import com.tinydesk.tracker.entity.Video;
import com.tinydesk.tracker.repository.HistoryRepository;
import com.tinydesk.tracker.repository.LockRepository;
import com.tinydesk.tracker.repository.MetadataRepository;
import com.tinydesk.tracker.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for TinyDeskTrackerService.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TinyDeskTrackerServiceTest {
    
    @Autowired
    private TinyDeskTrackerService trackerService;
    
    @Autowired
    private DatabaseService databaseService;
    
    @MockBean
    private YouTubeService youtubeService;
    
    @Autowired
    private VideoRepository videoRepository;
    
    @Autowired
    private HistoryRepository historyRepository;
    
    @Autowired
    private MetadataRepository metadataRepository;
    
    @Autowired
    private LockRepository lockRepository;
    
    @Autowired
    private AppConfig appConfig;
    
    @BeforeEach
    public void setUp() {
        // Clean up database before each test
        historyRepository.deleteAll();
        videoRepository.deleteAll();
        metadataRepository.deleteAll();
        lockRepository.deleteAll();
    }
    
    @Test
    public void testUpdateHistoricalData() {
        List<Map<String, Object>> videos = new ArrayList<>();
        
        Map<String, Object> video1 = new HashMap<>();
        video1.put("videoId", "v1");
        video1.put("title", "Video 1");
        video1.put("viewCount", 100L);
        video1.put("publishedAt", "2020-01-01T00:00:00Z");
        videos.add(video1);
        
        Map<String, Object> video2 = new HashMap<>();
        video2.put("videoId", "v2");
        video2.put("title", "Video 2");
        video2.put("viewCount", 200L);
        video2.put("publishedAt", "2020-01-02T00:00:00Z");
        videos.add(video2);
        
        trackerService.updateHistoricalData(videos);
        
        List<Video> topVideos = databaseService.getTopVideos(10);
        assertThat(topVideos).hasSize(2);
        assertThat(topVideos.get(0).getVideoId()).isEqualTo("v2");
        
        Map<String, Object> metadata = trackerService.getMetadata();
        assertThat(metadata.get("totalVideos")).isEqualTo(2);
        assertThat((Long) metadata.get("lastUpdate")).isGreaterThan(0L);
    }
    
    @Test
    public void testFetchAllVideos() {
        // Mock playlist response
        YouTubePlaylistResponse playlistResponse = new YouTubePlaylistResponse();
        List<YouTubePlaylistResponse.PlaylistItem> items = new ArrayList<>();
        
        YouTubePlaylistResponse.PlaylistItem item1 = new YouTubePlaylistResponse.PlaylistItem();
        YouTubePlaylistResponse.PlaylistItem.Snippet snippet1 = new YouTubePlaylistResponse.PlaylistItem.Snippet();
        YouTubePlaylistResponse.PlaylistItem.Snippet.ResourceId resourceId1 = new YouTubePlaylistResponse.PlaylistItem.Snippet.ResourceId();
        resourceId1.setVideoId("abc");
        snippet1.setResourceId(resourceId1);
        item1.setSnippet(snippet1);
        items.add(item1);
        
        YouTubePlaylistResponse.PlaylistItem item2 = new YouTubePlaylistResponse.PlaylistItem();
        YouTubePlaylistResponse.PlaylistItem.Snippet snippet2 = new YouTubePlaylistResponse.PlaylistItem.Snippet();
        YouTubePlaylistResponse.PlaylistItem.Snippet.ResourceId resourceId2 = new YouTubePlaylistResponse.PlaylistItem.Snippet.ResourceId();
        resourceId2.setVideoId("def");
        snippet2.setResourceId(resourceId2);
        item2.setSnippet(snippet2);
        items.add(item2);
        
        playlistResponse.setItems(items);
        playlistResponse.setNextPageToken(null);
        
        // Mock video statistics response
        YouTubeVideoResponse statsResponse = new YouTubeVideoResponse();
        List<YouTubeVideoResponse.VideoItem> videoItems = new ArrayList<>();
        
        for (String videoId : List.of("abc", "def")) {
            YouTubeVideoResponse.VideoItem videoItem = new YouTubeVideoResponse.VideoItem();
            videoItem.setId(videoId);
            
            YouTubeVideoResponse.VideoItem.Snippet videoSnippet = new YouTubeVideoResponse.VideoItem.Snippet();
            videoSnippet.setTitle("Title " + videoId);
            videoSnippet.setPublishedAt("2020-01-01T00:00:00Z");
            videoItem.setSnippet(videoSnippet);
            
            YouTubeVideoResponse.VideoItem.Statistics statistics = new YouTubeVideoResponse.VideoItem.Statistics();
            statistics.setViewCount("123");
            videoItem.setStatistics(statistics);
            
            videoItems.add(videoItem);
        }
        
        statsResponse.setItems(videoItems);
        
        when(youtubeService.getPlaylistVideos(anyString(), any())).thenReturn(playlistResponse);
        when(youtubeService.getVideoStatistics(any())).thenReturn(statsResponse);
        
        List<Map<String, Object>> videos = trackerService.fetchAllVideos();
        
        assertThat(videos).hasSize(2);
        assertThat(videos.stream().map(v -> v.get("videoId"))).containsExactlyInAnyOrder("abc", "def");
        assertThat(videos.get(0).get("title").toString()).startsWith("Title");
    }
    
    @Test
    public void testGetMetadata() {
        databaseService.setMetadata("lastUpdate", "123456789");
        databaseService.setMetadata("totalVideos", "42");
        
        Map<String, Object> metadata = trackerService.getMetadata();
        
        assertThat(metadata.get("lastUpdate")).isEqualTo(123456789L);
        assertThat(metadata.get("totalVideos")).isEqualTo(42);
    }
}

