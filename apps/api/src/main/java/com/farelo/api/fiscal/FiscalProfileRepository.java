package com.farelo.api.fiscal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FiscalProfileRepository extends JpaRepository<FiscalProfile, UUID> {
}
