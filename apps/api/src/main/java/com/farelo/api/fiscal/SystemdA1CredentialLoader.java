package com.farelo.api.fiscal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;

/** Explicit adapter for a short-lived systemd unit; never registered in the PDV application. */
public final class SystemdA1CredentialLoader {
    private static final int MAX_PASSWORD_BYTES = 1024;

    private SystemdA1CredentialLoader() { }

    public static Pkcs12SigningCredentials loadFromEnvironment() {
        String directory = System.getenv("CREDENTIALS_DIRECTORY");
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("systemd credential directory is unavailable");
        }
        return load(Path.of(directory));
    }

    public static Pkcs12SigningCredentials load(Path directory) {
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("systemd credential directory is unavailable");
        }
        Path a1 = directory.resolve("a1.p12.cred");
        Path passwordFile = directory.resolve("a1-password.cred");
        byte[] encoded = null;
        char[] password = null;
        CharBuffer decoded = null;
        try {
            if (!Files.isRegularFile(passwordFile, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(a1, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(passwordFile) == 0
                    || Files.size(passwordFile) > MAX_PASSWORD_BYTES) {
                throw new IllegalArgumentException("A1 credentials are unavailable");
            }
            encoded = Files.readAllBytes(passwordFile);
            if (encoded.length == 0 || encoded.length > MAX_PASSWORD_BYTES) {
                throw new IllegalArgumentException("A1 credentials are unavailable");
            }
            decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(encoded));
            password = new char[decoded.remaining()];
            decoded.get(password);
            if (password.length == 0) {
                throw new IllegalArgumentException("A1 credentials are unavailable");
            }
            for (char value : password) {
                if (value == '\0' || value == '\n' || value == '\r') {
                    throw new IllegalArgumentException("A1 credentials are unavailable");
                }
            }
            return Pkcs12SigningCredentials.load(a1, password);
        } catch (IOException ex) {
            throw new IllegalArgumentException("A1 credentials are unavailable");
        } finally {
            if (encoded != null) Arrays.fill(encoded, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
            if (decoded != null) {
                decoded.clear();
                while (decoded.hasRemaining()) decoded.put('\0');
            }
        }
    }
}
