package com.tinydesk.tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing system metadata key-value pairs.
 */
@Entity
@Table(name = "metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Metadata {
    
    @Id
    @Column(name = "key", nullable = false)
    private String key;
    
    @Column(name = "value", nullable = false)
    private String value;
}
