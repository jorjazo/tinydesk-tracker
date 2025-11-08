package com.tinydesk.tracker.service;

import com.tinydesk.tracker.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for SchedulerService.
 */
@SpringBootTest
@ActiveProfiles("test")
public class SchedulerServiceTest {
    
    @Autowired
    private SchedulerService schedulerService;
    
    @Autowired
    private AppConfig appConfig;
    
    @Test
    public void testComputeNextUpdateTimestampWithCron() {
        // Set cron expression: every day at 6:00 AM
        appConfig.getScheduler().setUpdateCron("0 0 6 * * *");
        
        // If last update was at 5:30 AM, next should be 6:00 AM same day
        LocalDateTime now = LocalDateTime.of(2024, 1, 1, 5, 30, 0);
        long nowTs = now.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        long nextTs = schedulerService.computeNextUpdateTimestamp(nowTs);
        LocalDateTime nextUpdate = LocalDateTime.ofEpochSecond(nextTs, 0, ZoneId.systemDefault().getRules().getOffset(now));
        
        assertThat(nextUpdate.getHour()).isEqualTo(6);
        assertThat(nextUpdate.getMinute()).isEqualTo(0);
        // Should be within the same day or next day
        assertThat(nextUpdate).isAfterOrEqualTo(now);
    }
    
    @Test
    public void testComputeNextUpdateTimestampWithInterval() {
        // Disable cron by using an invalid expression or rely on fallback
        appConfig.getScheduler().setUpdateCron("");
        appConfig.getScheduler().setUpdateIntervalHours(3.0);
        
        LocalDateTime lastUpdate = LocalDateTime.of(2024, 1, 1, 1, 0, 0);
        long lastUpdateTs = lastUpdate.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        long nextTs = schedulerService.computeNextUpdateTimestamp(lastUpdateTs);
        LocalDateTime nextUpdate = LocalDateTime.ofEpochSecond(nextTs, 0, 
                ZoneId.systemDefault().getRules().getOffset(lastUpdate));
        
        // Next update should be 3 hours after last update
        LocalDateTime expected = lastUpdate.plusHours(3);
        assertThat(nextUpdate).isEqualTo(expected);
    }
    
    @Test
    public void testComputeNextUpdateTimestampWithInvalidCron() {
        // Use invalid cron expression to trigger fallback to interval
        appConfig.getScheduler().setUpdateCron("invalid-cron");
        appConfig.getScheduler().setUpdateIntervalHours(6.0);
        
        LocalDateTime lastUpdate = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        long lastUpdateTs = lastUpdate.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        long nextTs = schedulerService.computeNextUpdateTimestamp(lastUpdateTs);
        
        // Should fall back to interval-based calculation
        long expectedTs = lastUpdateTs + (long)(6.0 * 3600);
        assertThat(nextTs).isEqualTo(expectedTs);
    }
}

