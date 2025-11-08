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
        List<TinyDeskTrackerService.VideoSnapshot> videos = List.of(
                new TinyDeskTrackerService.VideoSnapshot("v1", "Video 1", 100L, "2020-01-01T00:00:00Z", "PL1B627337ED6F55F0"),
                new TinyDeskTrackerService.VideoSnapshot("v2", "Video 2", 200L, "2020-01-02T00:00:00Z", "PL1B627337ED6F55F0")
        );
        
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
        
        List<TinyDeskTrackerService.VideoSnapshot> videos = trackerService.fetchAllVideos();
        
        // Now fetches from 2 playlists, so we get 4 videos total (2 from each)
        assertThat(videos).hasSize(4);
        assertThat(videos.stream().map(TinyDeskTrackerService.VideoSnapshot::videoId))
                .contains("abc", "def");
        assertThat(videos.get(0).title()).startsWith("Title");
        // Verify we have videos from both playlists
        assertThat(videos.stream().map(TinyDeskTrackerService.VideoSnapshot::playlistId).distinct())
                .hasSize(2)
                .contains("PL1B627337ED6F55F0", "PLy2PCKGkKRVYPm1tBwoX45ocAzuhVyvJX");
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

