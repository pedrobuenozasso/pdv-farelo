package com.farelo.api.security.web;

import com.farelo.api.security.User;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.UserService;
import com.farelo.api.security.rbac.RequireRole;
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
 * record of a person who can operate the system. As of FARELO-123 (see the
 * dedicated section below) every method here requires a caller role via
 * {@link RequireRole}; before that ticket this controller carried no
 * authentication/RBAC at all (see {@link User}'s javadoc for FARELO-120's
 * original, deliberately unauthenticated scope).
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
 *
 * <h2>FARELO-123 — RBAC: {@code ADMIN} only, except read access also allows
 * {@code MANAGER}</h2>
 *
 * Class-level {@code @RequireRole(UserRole.ADMIN)} below is the default for
 * every method; {@link #list()} and {@link #getById} carry their own
 * method-level override widening that to {@code ADMIN}+{@code MANAGER} (see
 * {@link RequireRole}'s javadoc — method-level wins over class-level, never
 * unioned, so those two overrides fully replace the class list rather than
 * adding to it).
 *
 * <p><b>Why {@code ADMIN} only for the three write endpoints ({@link
 * #create}, {@link #update}, {@link #updatePassword}), unlike {@code
 * CategoryController}/{@code ProductController}'s writes which also allow
 * {@code MANAGER}</b>: those controllers can never grant a caller more
 * access than they already have — a {@code MANAGER} editing a product
 * cannot turn themselves into an {@code ADMIN}. This controller can:
 * {@link #create}/{@link #update} both accept an arbitrary {@code role}
 * value in the request body, and {@code @RequireRole} has no way to inspect
 * a request payload — it can only allow or deny the whole endpoint per
 * caller role. Allowing {@code MANAGER} to call {@code POST}/{@code PUT
 * /api/v1/users} would therefore let a {@code MANAGER} mint or promote an
 * {@code ADMIN} account (privilege escalation), and allowing it on
 * {@code PATCH .../password} would let a {@code MANAGER} silently take over
 * <i>any</i> account, including another {@code ADMIN}'s, since this
 * endpoint still has no current-password confirmation (see the class
 * javadoc above and {@code UserService#updatePassword}'s). Account
 * administration — as opposed to catalog/menu editing — is exactly the kind
 * of operation prompt mestre seção 21 keeps as its own separate Admin
 * modules ("Usuários", "Permissões"), so keeping it strictly {@code ADMIN}
 * is the conservative, defensible default; a future ticket could split this
 * further (e.g. a payload-aware check preventing a {@code MANAGER} from
 * assigning {@code ADMIN}) if the product ever needs {@code MANAGER} to
 * onboard ordinary staff without a full admin account, but that is a new
 * capability, not something to guess at here.
 *
 * <p><b>Why {@link #list()}/{@link #getById} additionally allow {@code
 * MANAGER}</b>: viewing the staff roster (e.g. to look up an id, confirm
 * who is active) is a much lower-stakes operation than creating/editing
 * accounts or resetting a password, and a shift manager plausibly needs it
 * day-to-day — unlike the write endpoints, granting read access here cannot
 * be used to escalate anyone's privileges. {@code UserResponse} never
 * includes {@code passwordHash} regardless of caller (see the class javadoc
 * above), so there is no secret-leakage concern in widening read access.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequireRole(UserRole.ADMIN)
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
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public List<UserResponse> list() {
        return userService.listAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
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
