package com.farelo.api.fiscal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FiscalSubmissionAttemptRepository extends JpaRepository<FiscalSubmissionAttempt, UUID> {
    Optional<FiscalSubmissionAttempt> findByDocumentId(UUID documentId);

    @Query("SELECT a.document.id FROM FiscalSubmissionAttempt a WHERE a.id = :id")
    Optional<UUID> findDocumentIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FiscalSubmissionAttempt a WHERE a.id = :id")
    Optional<FiscalSubmissionAttempt> findByIdForUpdate(@Param("id") UUID id);
}
