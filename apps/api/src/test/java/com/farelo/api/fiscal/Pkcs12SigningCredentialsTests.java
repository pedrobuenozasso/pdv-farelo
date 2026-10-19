package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pkcs12SigningCredentialsTests {
    @TempDir Path temp;

    @Test
    void loadsA1KeyAndCertificateWithoutChangingCallersPassword() throws Exception {
        KeyPair pair = testKeyPair();
        X509Certificate certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
        Path file = writeStore(pair, certificate, "example-password".toCharArray());
        char[] password = "example-password".toCharArray();

        Pkcs12SigningCredentials loaded = Pkcs12SigningCredentials.load(file, password);

        assertArrayEquals("example-password".toCharArray(), password);
        assertEquals(certificate, loaded.certificate());
        assertArrayEquals(pair.getPrivate().getEncoded(), loaded.privateKey().getEncoded());
        assertEquals("Pkcs12SigningCredentials[REDACTED]", loaded.toString());
    }

    @Test
    void rejectsWrongPasswordAndMissingPrivateKeyWithoutLeakingPath() throws Exception {
        KeyPair pair = testKeyPair();
        X509Certificate certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
        Path file = writeStore(pair, certificate, "correct-password".toCharArray());

        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Pkcs12SigningCredentials.load(file, "wrong-password".toCharArray()));
        assertEquals("Could not read A1 PKCS#12 file", wrong.getMessage());

        KeyStore withoutKey = KeyStore.getInstance("PKCS12");
        withoutKey.load(null, null);
        withoutKey.setCertificateEntry("certificate", certificate);
        try (var output = Files.newOutputStream(file)) {
            withoutKey.store(output, "correct-password".toCharArray());
        }
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> Pkcs12SigningCredentials.load(file, "correct-password".toCharArray()));
        assertEquals("A1 file contains no private key", missing.getMessage());
    }

    private Path writeStore(KeyPair pair, X509Certificate certificate, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("a1", pair.getPrivate(), password, new java.security.cert.Certificate[]{certificate});
        Path file = temp.resolve("test-a1.p12");
        try (var output = Files.newOutputStream(file)) {
            store.store(output, password);
        }
        return file;
    }

    private static KeyPair testKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
