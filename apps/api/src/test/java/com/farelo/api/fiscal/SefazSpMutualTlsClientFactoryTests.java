package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SefazSpMutualTlsClientFactoryTests {
    @TempDir Path temp;

    @Test
    void configuresClientWithExternalFakeA1AndOfficialPublicTrustRoot() throws Exception {
        Path fakeA1 = fakeA1();
        Path officialRoot = Path.of(getClass().getResource("/icp-brasil-v10.crt").toURI());
        char[] password = "test-password".toCharArray();

        HttpClient client = SefazSpMutualTlsClientFactory.create(fakeA1, password, officialRoot);

        assertArrayEquals("test-password".toCharArray(), password);
        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
        assertTrue(client.sslContext() != null);
        assertTrue(client.connectTimeout().isPresent());
    }

    @Test
    void refusesWrongPasswordAndUntrustedRootWithoutLeakingPaths() throws Exception {
        Path fakeA1 = fakeA1();
        Path officialRoot = Path.of(getClass().getResource("/icp-brasil-v10.crt").toURI());
        Path fakeRoot = temp.resolve("fake-root.crt");
        Files.writeString(fakeRoot, "not a root certificate");

        IllegalArgumentException wrongPassword = assertThrows(IllegalArgumentException.class,
                () -> SefazSpMutualTlsClientFactory.create(fakeA1, "wrong".toCharArray(), officialRoot));
        assertTrue(!wrongPassword.getMessage().contains(fakeA1.toString()));
        IllegalArgumentException wrongRoot = assertThrows(IllegalArgumentException.class,
                () -> SefazSpMutualTlsClientFactory.create(fakeA1, "test-password".toCharArray(), fakeRoot));
        assertTrue(!wrongRoot.getMessage().contains(fakeRoot.toString()));
    }

    private Path fakeA1() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("test-a1", pair.getPrivate(), "test-password".toCharArray(),
                new Certificate[]{certificate});
        Path file = temp.resolve("fake-a1.p12");
        try (var output = Files.newOutputStream(file)) {
            store.store(output, "test-password".toCharArray());
        }
        return file;
    }
}
