package com.farelo.api.security.web;

import com.farelo.api.security.AuthenticationService;
import com.farelo.api.security.auth.IssuedToken;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/auth} (FARELO-121) — login only. This is the entire
 * authentication surface this ticket adds: prove who you are once, get a
 * token back. No endpoint in this codebase (not even the ones added by this
 * same ticket) requires that token yet — see
 * {@code com.farelo.api.security.auth.JwtTokenService}'s javadoc for the
 * token design, and {@code com.farelo.api.security.AuthenticationService}'s
 * javadoc for the credential-checking logic this controller delegates to.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken issuedToken = authenticationService.login(request.email(), request.password());
        return LoginResponse.from(issuedToken);
    }

}
