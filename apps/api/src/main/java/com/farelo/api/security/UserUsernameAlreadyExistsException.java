package com.farelo.api.security;

public class UserUsernameAlreadyExistsException extends RuntimeException {
    public UserUsernameAlreadyExistsException(String username) {
        super("Nome de usuário já está em uso: " + username);
    }
}
