package com.farelo.api.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Natural key of the entity (see User's javadoc — email is the future
    // login identifier) — same reasoning already used for
    // RecipeRepository#findByProductIdAndActiveTrue. Used by UserService to
    // enforce the uniqueness pre-check on create/update, and will be reused
    // by FARELO-121 to look a user up by the credential they log in with.
    Optional<User> findByEmail(String email);

}
