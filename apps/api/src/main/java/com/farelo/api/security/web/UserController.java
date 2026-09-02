package com.farelo.api.security.web;

import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/users} (FARELO-120) — CRUD for {@link User}, the account
 * record of a person who can operate the system. No authentication/RBAC
 * lives here (see {@link User}'s javadoc); this is only the registry of who
 * can exist in the system.
 *
 * <p>Every response method funnels through {@link UserResponse#from(User)},
 * which never includes {@code passwordHash} — see that record's javadoc.
 *
 * <p><b>{@code PUT} for the general profile update, a separate {@code
 * PATCH .../password} for the password</b>: same full-replace {@code PUT}
 * shape as {@code IngredientController}/{@code ProductController} for
 * name/email/role/active, but the password is deliberately carved out into
 * its own endpoint — see {@code UserService#updatePassword}'s javadoc for
 * the full reasoning (distinct security implications from an ordinary
 * profile edit, and no current-password confirmation yet since there is no
 * login/session to check one against).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserCreateRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        User user = userService.create(request.name(), request.email(), request.password(), request.role());

        URI location = uriComponentsBuilder
                .path("/api/v1/users/{id}")
                .buildAndExpand(user.getId())
                .toUri();

        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    // No active-only filter yet — YAGNI, same as CategoryController/
    // IngredientController's list(), no consumer (Admin) asking for it yet.
    @GetMapping
    public List<UserResponse> list() {
        return userService.listAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable UUID id) {
        return UserResponse.from(userService.getById(id));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        User user = userService.update(id, request.name(), request.email(), request.role(), request.active());
        return UserResponse.from(user);
    }

    @PatchMapping("/{id}/password")
    public UserResponse updatePassword(@PathVariable UUID id, @Valid @RequestBody UserPasswordUpdateRequest request) {
        User user = userService.updatePassword(id, request.newPassword());
        return UserResponse.from(user);
    }

}
