package com.tinydesk.tracker.service;

import com.tinydesk.tracker.entity.History;
import com.tinydesk.tracker.entity.Video;
import com.tinydesk.tracker.repository.HistoryRepository;
import com.tinydesk.tracker.repository.LockRepository;
import com.tinydesk.tracker.repository.MetadataRepository;
import com.tinydesk.tracker.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DatabaseService.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DatabaseServiceTest {
    
    @Autowired
    private DatabaseService databaseService;
    
    @Autowired
    private VideoRepository videoRepository;
    
    @Autowired
    private HistoryRepository historyRepository;
    
    @Autowired
    private MetadataRepository metadataRepository;
    
    @Autowired
    private LockRepository lockRepository;
    
    @BeforeEach
    public void setUp() {
        // Clean up database before each test
        historyRepository.deleteAll();
        videoRepository.deleteAll();
        metadataRepository.deleteAll();
        lockRepository.deleteAll();
    }
    
    @Test
    public void testSaveVideoAndGetTop() {
        long timestamp = System.currentTimeMillis() / 1000;
        
        databaseService.saveVideo("v1", "Video 1", 100L, timestamp, "2020-01-01T00:00:00Z", "PL1B627337ED6F55F0");
        databaseService.saveVideo("v2", "Video 2", 200L, timestamp, "2020-01-02T00:00:00Z", "PL1B627337ED6F55F0");
        
        List<Video> topVideos = databaseService.getTopVideos(10);
        
        assertThat(topVideos).hasSize(2);
        assertThat(topVideos.get(0).getVideoId()).isEqualTo("v2");
        assertThat(topVideos.get(0).getCurrentViews()).isEqualTo(200L);
        assertThat(topVideos.get(1).getVideoId()).isEqualTo("v1");
        assertThat(topVideos.get(1).getCurrentViews()).isEqualTo(100L);
        
        List<History> history = databaseService.getVideoHistory("v1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getViewCount()).isEqualTo(100L);
    }
    
    @Test
    public void testHistoryIsCappedToLatest100Entries() {
        long baseTs = System.currentTimeMillis() / 1000;
        
        // Add 120 history entries
        for (int offset = 0; offset < 120; offset++) {
            databaseService.saveVideo("vid", "Video", (long) offset, baseTs + offset, "2020-01-01T00:00:00Z", "PL1B627337ED6F55F0");
        }
        
        List<History> history = databaseService.getVideoHistory("vid");
        
        assertThat(history).hasSize(100);
        // Oldest entry retained should correspond to offset 20 (120 - 100)
        assertThat(history.get(0).getViewCount()).isEqualTo(20L);
        assertThat(history.get(history.size() - 1).getViewCount()).isEqualTo(119L);
    }
    
    @Test
    public void testMetadataRoundtrip() {
        databaseService.setMetadata("lastUpdate", "123");
        databaseService.setMetadata("totalVideos", "10");
        
        assertThat(databaseService.getMetadata("lastUpdate")).hasValue("123");
        assertThat(databaseService.getMetadata("totalVideos")).hasValue("10");
        
        Map<String, String> metadata = databaseService.getAllMetadata();
        assertThat(metadata).containsEntry("lastUpdate", "123");
        assertThat(metadata).containsEntry("totalVideos", "10");
    }
    
    @Test
    public void testGetStats() {
        long timestamp = System.currentTimeMillis() / 1000;
        
        databaseService.saveVideo("v1", "Video 1", 100L, timestamp, "2020-01-01T00:00:00Z", "PL1B627337ED6F55F0");
        databaseService.saveVideo("v2", "Video 2", 200L, timestamp, "2020-01-02T00:00:00Z", "PL1B627337ED6F55F0");
        
        Map<String, Long> stats = databaseService.getStats();
        
        assertThat(stats.get("total_videos")).isEqualTo(2);
        assertThat(stats.get("total_history_entries")).isEqualTo(2);
    }
}

