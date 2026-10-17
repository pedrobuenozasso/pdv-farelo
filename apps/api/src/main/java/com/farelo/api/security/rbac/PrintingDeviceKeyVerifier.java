package com.farelo.api.security.rbac;

import com.farelo.api.security.auth.InvalidTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Separate machine credential; never grants a human role or JWT principal. */
@Component
public class PrintingDeviceKeyVerifier {

    private final byte[] expectedKey;

    public PrintingDeviceKeyVerifier(@Value("${security.printing.device-key:}") String configuredKey) {
        this.expectedKey = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String presentedKey) {
        // An unconfigured deployment must fail closed, including if the caller
        // submits an empty header. Use a 32-byte minimum for generated secrets.
        if (expectedKey.length < 32 || presentedKey == null ||
                !MessageDigest.isEqual(expectedKey, presentedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidTokenException("Invalid printing device credential", null);
        }
    }
}
