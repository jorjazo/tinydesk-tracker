package com.tinydesk.tracker.controller;

import com.tinydesk.tracker.service.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ApiController endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ApiControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private DatabaseService databaseService;
    
    @BeforeEach
    public void setUp() {
        // Clean database before each test
        databaseService.getVideoHistory("vid").forEach(h -> {
            // History cleanup is handled by cascade
        });
    }
    
    @Test
    public void testApiTopReturnsTrackedVideos() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        
        databaseService.saveVideo("vid", "Title", 100L, timestamp, "2020-01-01T00:00:00Z");
        databaseService.setMetadata("lastUpdate", String.valueOf(timestamp));
        databaseService.setMetadata("totalVideos", "1");
        
        mockMvc.perform(get("/api/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.videos", hasSize(1)))
                .andExpect(jsonPath("$.videos[0].videoId", is("vid")))
                .andExpect(jsonPath("$.videos[0].title", is("Title")))
                .andExpect(jsonPath("$.videos[0].views", is(100)));
    }
    
    @Test
    public void testApiStatusReturnsMetadata() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        
        databaseService.setMetadata("lastUpdate", String.valueOf(timestamp));
        databaseService.setMetadata("totalVideos", "0");
        
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("online")))
                .andExpect(jsonPath("$.lastUpdate", is((int) timestamp)))
                .andExpect(jsonPath("$.totalVideos", is(0)));
    }
    
    @Test
    public void testApiTopWithMultipleVideos() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        
        databaseService.saveVideo("v1", "Video 1", 100L, timestamp, "2020-01-01T00:00:00Z");
        databaseService.saveVideo("v2", "Video 2", 200L, timestamp, "2020-01-02T00:00:00Z");
        databaseService.saveVideo("v3", "Video 3", 150L, timestamp, "2020-01-03T00:00:00Z");
        databaseService.setMetadata("lastUpdate", String.valueOf(timestamp));
        databaseService.setMetadata("totalVideos", "3");
        
        mockMvc.perform(get("/api/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.videos", hasSize(3)))
                .andExpect(jsonPath("$.videos[0].videoId", is("v2"))) // Highest views first
                .andExpect(jsonPath("$.videos[0].views", is(200)))
                .andExpect(jsonPath("$.videos[1].videoId", is("v3")))
                .andExpect(jsonPath("$.videos[1].views", is(150)))
                .andExpect(jsonPath("$.videos[2].videoId", is("v1")))
                .andExpect(jsonPath("$.videos[2].views", is(100)));
    }
    
    @Test
    public void testApiHistoryReturnsVideoHistory() throws Exception {
        long baseTimestamp = System.currentTimeMillis() / 1000;
        
        // Save video with multiple history entries
        databaseService.saveVideo("vid", "Video", 100L, baseTimestamp, "2020-01-01T00:00:00Z");
        databaseService.saveVideo("vid", "Video", 150L, baseTimestamp + 3600, "2020-01-01T00:00:00Z");
        databaseService.saveVideo("vid", "Video", 200L, baseTimestamp + 7200, "2020-01-01T00:00:00Z");
        
        mockMvc.perform(get("/api/history/vid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId", is("vid")))
                .andExpect(jsonPath("$.history", hasSize(3)))
                .andExpect(jsonPath("$.history[0].viewCount", is(100)))
                .andExpect(jsonPath("$.history[1].viewCount", is(150)))
                .andExpect(jsonPath("$.history[2].viewCount", is(200)));
    }
}
