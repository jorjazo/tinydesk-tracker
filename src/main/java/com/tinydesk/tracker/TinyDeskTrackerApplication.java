package com.tinydesk.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for YouTube Tiny Desk Concert Tracker.
 * 
 * This application tracks view counts of NPR Tiny Desk concerts over time,
 * providing analytics, historical data, and ranking information through a web interface.
 */
@SpringBootApplication
@EnableScheduling
public class TinyDeskTrackerApplication {

    public static void main(String[] args) {
        printBanner();
        SpringApplication.run(TinyDeskTrackerApplication.class, args);
    }
    
    private static void printBanner() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  YouTube Tiny Desk Concert Tracker");
        System.out.println("  Spring Boot Edition");
        System.out.println("=".repeat(50) + "\n");
    }
}
