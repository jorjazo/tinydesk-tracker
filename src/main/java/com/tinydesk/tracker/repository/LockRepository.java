package com.tinydesk.tracker.repository;

import com.tinydesk.tracker.entity.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Lock entities.
 */
@Repository
public interface LockRepository extends JpaRepository<Lock, String> {
    
    /**
     * Find lock by key.
     */
    Optional<Lock> findByKey(String key);
    
    /**
     * Delete expired locks.
     */
    @Modifying
    @Query("DELETE FROM Lock l WHERE l.expiresAt < :currentTime")
    int deleteExpiredLocks(@Param("currentTime") Long currentTime);
    
    /**
     * Delete lock by key and owner.
     */
    @Modifying
    @Query("DELETE FROM Lock l WHERE l.key = :key AND l.owner = :owner")
    int deleteByKeyAndOwner(@Param("key") String key, @Param("owner") String owner);
}
