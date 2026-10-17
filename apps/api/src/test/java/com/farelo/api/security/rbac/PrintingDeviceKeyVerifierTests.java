package com.farelo.api.security.rbac;

import com.farelo.api.security.auth.InvalidTokenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintingDeviceKeyVerifierTests {

    private static final String KEY = "test-print-device-key-32-bytes-minimum";

    @Test
    void acceptsOnlyTheConfiguredDeviceKey() {
        PrintingDeviceKeyVerifier verifier = new PrintingDeviceKeyVerifier(KEY);

        assertThatCode(() -> verifier.verify(KEY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify("wrong-key")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAllRequestsWhenServerKeyIsAbsentOrTooShort() {
        assertThatThrownBy(() -> new PrintingDeviceKeyVerifier("").verify(""))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> new PrintingDeviceKeyVerifier("short").verify("short"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
