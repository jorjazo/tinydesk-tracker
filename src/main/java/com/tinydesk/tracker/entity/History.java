package com.tinydesk.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a historical view count snapshot for a video.
 */
@Entity
@Table(name = "history", indexes = {
    @Index(name = "idx_history_video_id", columnList = "video_id"),
    @Index(name = "idx_history_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
public class History {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "video_id", nullable = false)
    private String videoId;
    
    @Column(name = "timestamp", nullable = false)
    private Long timestamp;
    
    @Column(name = "view_count", nullable = false)
    private Long viewCount;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", insertable = false, updatable = false)
    private Video video;
}
