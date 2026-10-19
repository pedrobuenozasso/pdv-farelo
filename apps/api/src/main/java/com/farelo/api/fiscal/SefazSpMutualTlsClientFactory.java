package com.farelo.api.fiscal;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;

/** Creates an mTLS client using an external A1 and the official ICP-Brasil SSL root. */
public final class SefazSpMutualTlsClientFactory {
    // Fingerprint published for the ICP-Brasil v10 SSL root by ITI.
    private static final String ROOT_SHA256 =
            "6e0bff069a26994c15de2c4888cc54af84882e5495b7fbf66be9ccffec7489f6";
    private static final long MAX_ROOT_BYTES = 100_000;

    private SefazSpMutualTlsClientFactory() { }

    public static HttpClient create(Path a1File, char[] password, Path officialRootCertificate) {
        if (a1File == null || password == null || password.length == 0 || officialRootCertificate == null) {
            throw new IllegalArgumentException("A1, password and ICP-Brasil root are required");
        }
        char[] workingPassword = password.clone();
        try {
            // Reuse the signing checks before the A1 is installed as TLS identity.
            Pkcs12SigningCredentials.load(a1File, workingPassword);
            if (!Files.isRegularFile(officialRootCertificate) || !Files.isReadable(officialRootCertificate)
                    || Files.size(officialRootCertificate) > MAX_ROOT_BYTES) {
                throw new IllegalArgumentException("official ICP-Brasil root is unavailable");
            }
            X509Certificate root;
            try (InputStream input = Files.newInputStream(officialRootCertificate)) {
                root = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(input);
            }
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(root.getEncoded()));
            if (!ROOT_SHA256.equals(fingerprint) || root.getBasicConstraints() < 0) {
                throw new IllegalArgumentException("ICP-Brasil root fingerprint differs from official v10");
            }
            root.checkValidity();
            root.verify(root.getPublicKey());

            KeyStore identity = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(a1File)) {
                identity.load(input, workingPassword);
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(identity, workingPassword);

            KeyStore trust = KeyStore.getInstance("PKCS12");
            trust.load(null, null);
            trust.setCertificateEntry("icp-brasil-v10-ssl", root);
            TrustManagerFactory trustedRoots = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustedRoots.init(trust);

            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(keys.getKeyManagers(), trustedRoots.getTrustManagers(), null);
            return HttpClient.newBuilder().sslContext(tls)
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10)).build();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            // Provider messages may contain paths or crypto provider details.
            throw new IllegalArgumentException("Could not configure SEFAZ-SP mutual TLS");
        } finally {
            Arrays.fill(workingPassword, '\0');
        }
    }
}
