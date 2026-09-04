package com.farelo.api.fiscal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Ordinary {@link JpaRepository} — the {@code company_fiscal_configuration}
 * table has no database-level constraint enforcing "at most one row" (see
 * {@link CompanyFiscalConfiguration}'s javadoc for the singleton-shape
 * reasoning). {@link #findFirstByOrderByCreatedAtAsc()} is the one custom
 * query this repository needs: {@link CompanyFiscalConfigurationService}
 * uses it to locate "the" row (if any) without addressing it by id, since
 * the API never exposes an {@code {id}} to callers.
 */
public interface CompanyFiscalConfigurationRepository extends JpaRepository<CompanyFiscalConfiguration, UUID> {

    Optional<CompanyFiscalConfiguration> findFirstByOrderByCreatedAtAsc();

}
