package com.farelo.api.security;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for {@link User} (FARELO-120). Owns hashing — no
 * caller outside this class (in particular, no controller/DTO) ever sees or
 * persists a raw password directly; every raw password reaching this class
 * from the {@code web} layer is hashed via {@link PasswordEncoder#encode}
 * before it touches {@link User}/{@link UserRepository}, and never logged.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a user. 409 {@link UserEmailAlreadyExistsException} if
     * {@code email} is already taken. The uniqueness pre-check here mirrors
     * {@code RecipeService#create}'s reasoning: the real source of truth
     * under concurrency is the {@code uk_app_user_email} constraint at the
     * DB level ({@code V19__create_user_table.sql}) — this check exists to
     * turn the common case into a clean {@code 409} instead of a generic
     * constraint-violation error, not to fully close the race.
     */
    @Transactional
    public User create(String name, String email, String rawPassword, UserRole role) {
        requireEmailNotTaken(email, null);

        User user = new User(name, email, passwordEncoder.encode(rawPassword), role);
        return userRepository.save(user);
    }

    // No active-only filter yet, same YAGNI reasoning as
    // IngredientService/CategoryService/ProductService's listAll() — no
    // consumer (Admin) asking for it yet.
    public List<User> listAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Reused by update()/updatePassword() below.
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Full profile update (name/email/role/active) — deliberately excludes
     * the password. See {@link #updatePassword(UUID, String)}'s javadoc for
     * why changing a password is a separate endpoint/method.
     */
    @Transactional
    public User update(UUID id, String name, String email, UserRole role, boolean active) {
        User user = getById(id);
        requireEmailNotTaken(email, id);

        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);

        return userRepository.save(user);
    }

    /**
     * Changes a user's password. A separate method/endpoint from
     * {@link #update(UUID, String, String, UserRole, boolean)} on purpose —
     * changing a password carries distinct security implications from
     * editing a name/email (e.g. it's the kind of action a real system
     * would want to audit or notify differently), so folding it into a
     * general "edit profile" call would blur that line for no benefit.
     *
     * <p><b>No current-password confirmation here</b>: with no login
     * mechanism yet (FARELO-121), there is no authenticated caller identity
     * to compare a "current password" against in the first place — asking
     * for one today would just be a second raw password this endpoint has
     * to also never log, with nothing real to check it against. Once
     * FARELO-121 exists, revisit this: a self-service "change my password"
     * flow will likely want to require the current password (or a fresh
     * re-auth), while an admin-initiated reset for someone else's account
     * usually doesn't. That distinction doesn't exist yet either — there is
     * exactly one caller shape today, an authenticated-nothing HTTP client
     * hitting the Admin API — so it isn't guessed at here.
     */
    @Transactional
    public User updatePassword(UUID id, String rawPassword) {
        User user = getById(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    private void requireEmailNotTaken(String email, UUID exceptUserId) {
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(exceptUserId))
                .ifPresent(existing -> {
                    throw new UserEmailAlreadyExistsException(email);
                });
    }

}
