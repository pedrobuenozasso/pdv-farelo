package com.farelo.api.printing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {
}
