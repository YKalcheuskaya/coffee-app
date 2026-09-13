package com.ykalcheu.coffee.app.order.persistence;

import com.ykalcheu.coffee.app.order.domain.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "insert into idempotency_record (idempotency_key, request_fingerprint, created_at) "
            + "values (:key, :fingerprint, :createdAt) on conflict do nothing", nativeQuery = true)
    int claimIfAbsent(@Param("key") String key, @Param("fingerprint") String fingerprint,
                      @Param("createdAt") java.time.Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from IdempotencyRecord record where record.key = :key")
    Optional<IdempotencyRecord> findByKeyForUpdate(String key);
}
