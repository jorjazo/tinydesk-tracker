package com.tinydesk.tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a YouTube video in the Tiny Desk playlist.
 */
@Entity
@Table(name = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    
    @Id
    @Column(name = "video_id", nullable = false)
    private String videoId;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "current_views", nullable = false)
    private Long currentViews = 0L;
    
    @Column(name = "last_updated", nullable = false)
    private Long lastUpdated;
    
    @Column(name = "published_at")
    private String publishedAt;
    
    @Column(name = "playlist_id", nullable = false)
    private String playlistId;
    
    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<History> historyEntries = new ArrayList<>();
}
