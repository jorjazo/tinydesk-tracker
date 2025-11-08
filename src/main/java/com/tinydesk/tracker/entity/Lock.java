package com.tinydesk.tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for distributed locking mechanism.
 */
@Entity
@Table(name = "locks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lock {
    
    @Id
    @Column(name = "`key`", nullable = false)
    private String key;
    
    @Column(name = "owner", nullable = false)
    private String owner;
    
    @Column(name = "expires_at", nullable = false)
    private Long expiresAt;
}
