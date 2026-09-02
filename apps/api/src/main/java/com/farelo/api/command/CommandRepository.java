package com.farelo.api.command;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommandRepository extends JpaRepository<Command, UUID> {

    Optional<Command> findByNumber(int number);

}
