package com.tinydesk.tracker.repository;

import com.tinydesk.tracker.entity.Metadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Metadata entities.
 */
@Repository
public interface MetadataRepository extends JpaRepository<Metadata, String> {
    
    /**
     * Find metadata by key.
     */
    Optional<Metadata> findByKey(String key);
}
