package com.farelo.api.fiscal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FiscalNumberSequenceRepository extends JpaRepository<FiscalNumberSequence, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM FiscalNumberSequence s WHERE s.issuerCnpj = :issuerCnpj "
            + "AND s.environment = :environment AND s.series = :series")
    Optional<FiscalNumberSequence> findScopeForUpdate(@Param("issuerCnpj") String issuerCnpj,
            @Param("environment") FiscalNumberEnvironment environment, @Param("series") int series);
}
