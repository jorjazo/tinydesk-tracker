package com.tinydesk.tracker.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.tinydesk.tracker.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Service for scheduling automatic updates.
 */
@Service
@Slf4j
public class SchedulerService {
    
    private final TinyDeskTrackerService trackerService;
    private final DatabaseService databaseService;
    private final AppConfig appConfig;
    
    public SchedulerService(TinyDeskTrackerService trackerService,
                           DatabaseService databaseService,
                           AppConfig appConfig) {
        this.trackerService = trackerService;
        this.databaseService = databaseService;
        this.appConfig = appConfig;
    }
    
    /**
     * Initialize on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("✓ Tracker initialized");
        
        Map<String, Long> stats = databaseService.getStats();
        log.info("  Videos in database: {}", stats.get("total_videos"));
        log.info("  History entries: {}", stats.get("total_history_entries"));
        
        Map<String, Object> metadata = trackerService.getMetadata();
        long lastUpdate = (long) metadata.get("lastUpdate");
        
        if (lastUpdate == 0) {
            log.info("No previous data found. Performing initial update...");
            trackerService.update();
        } else {
            java.time.Instant instant = java.time.Instant.ofEpochSecond(lastUpdate);
            java.time.LocalDateTime lastUpdateTime = java.time.LocalDateTime.ofInstant(
                    instant, java.time.ZoneId.systemDefault());
            log.info("Last update: {}", lastUpdateTime.format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        
        if (appConfig.getScheduler().isEnabled()) {
            log.info("✓ Scheduled updates enabled with cron: '{}'", appConfig.getScheduler().getUpdateCron());
        } else {
            log.info("⚠ Scheduled updates are disabled");
        }
        
        log.info("✓ Starting web server on port 5000\n");
        log.info("=".repeat(50));
        log.info("  System Ready!");
        log.info("=".repeat(50));
        log.info("Web Interface: http://localhost:5000/");
        log.info("Press Ctrl+C to exit");
        log.info("=".repeat(50) + "\n");
    }
    
    /**
     * Scheduled update based on cron expression from configuration.
     */
    @Scheduled(cron = "${tinydesk.scheduler.update-cron}")
    public void scheduledUpdate() {
        if (!appConfig.getScheduler().isEnabled()) {
            return;
        }
        trackerService.update();
    }
    
    /**
     * Compute next update timestamp based on cron expression.
     */
    public long computeNextUpdateTimestamp(long lastUpdateTs) {
        String cronExpression = appConfig.getScheduler().getUpdateCron();
        
        try {
            CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
            Cron cron = parser.parse(cronExpression);
            
            ZonedDateTime now = ZonedDateTime.now();
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(now);
            
            if (nextExecution.isPresent()) {
                return nextExecution.get().toEpochSecond();
            }
        } catch (Exception e) {
            log.warn("Could not parse cron expression '{}': {}", cronExpression, e.getMessage());
        }
        
        // Fallback to interval-based calculation
        double intervalHours = appConfig.getScheduler().getUpdateIntervalHours();
        long intervalSeconds = (long) (intervalHours * 3600);
        
        if (lastUpdateTs > 0) {
            return lastUpdateTs + intervalSeconds;
        }
        
        return System.currentTimeMillis() / 1000 + intervalSeconds;
    }
}
