package com.farelo.api.security;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link User} maps correctly onto the table created by
 * {@code V20__create_user_table.sql}, against a real PostgreSQL instance —
 * including the {@code uk_app_user_email} unique constraint, the DB-level
 * source of truth backing {@link UserService}'s email uniqueness check (see
 * that class's javadoc).
 */
@SpringBootTest
class UserRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUser() {
        User user = new User("Ana Souza", "ana+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.MANAGER);

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Ana Souza");
        assertThat(found.get().getRole()).isEqualTo(UserRole.MANAGER);
        assertThat(found.get().isActive()).isTrue();
        // Proof the stored value is a hash, not the raw password handed to
        // the constructor in this test — see User's javadoc.
        assertThat(found.get().getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void findsUserByEmail() {
        String email = "carlos+" + UUID.randomUUID() + "@farelo.dev";
        userRepository.saveAndFlush(new User("Carlos Lima", email, "bcrypt-hash", UserRole.CASHIER));

        Optional<User> found = userRepository.findByEmail(email);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(email);
    }

    @Test
    void returnsEmptyWhenEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("nobody-" + UUID.randomUUID() + "@farelo.dev");

        assertThat(found).isEmpty();
    }

    @Test
    void rejectsDuplicateEmailAtTheDatabaseLevel() {
        String email = "duplicate+" + UUID.randomUUID() + "@farelo.dev";
        userRepository.saveAndFlush(new User("Primeiro", email, "bcrypt-hash", UserRole.ATTENDANT));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new User("Segundo", email, "bcrypt-hash", UserRole.ATTENDANT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
