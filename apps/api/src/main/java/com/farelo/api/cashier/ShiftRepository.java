package com.farelo.api.cashier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    // Backs ShiftService#create's uniqueness pre-check (uk_shift_name is the
    // real source of truth under concurrency — same "clean 409 for the
    // common case" reasoning as UserRepository#findByEmail).
    Optional<Shift> findByName(String name);

}
