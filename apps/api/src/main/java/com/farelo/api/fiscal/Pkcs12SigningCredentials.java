package com.farelo.api.fiscal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Loads an A1 PKCS#12 file supplied outside Git. The caller owns the path and
 * password; neither is retained by this class. Loading does not emit a note.
 */
public record Pkcs12SigningCredentials(PrivateKey privateKey, X509Certificate certificate) {
    private static final long MAX_A1_BYTES = 10_000_000;

    public Pkcs12SigningCredentials {
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(certificate, "certificate");
    }

    public static Pkcs12SigningCredentials load(Path file, char[] password) {
        if (file == null || password == null || password.length == 0) {
            throw new IllegalArgumentException("A1 file and password are required");
        }
        char[] workingPassword = password.clone();
        try {
            if (!Files.isRegularFile(file) || !Files.isReadable(file)
                    || Files.size(file) > MAX_A1_BYTES) {
                throw new IllegalArgumentException("A1 file is unavailable");
            }
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(file)) {
                store.load(input, workingPassword);
            }
            String selectedAlias = null;
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.isKeyEntry(alias)) {
                    if (selectedAlias != null) {
                        throw new IllegalArgumentException("A1 file contains more than one private key");
                    }
                    selectedAlias = alias;
                }
            }
            if (selectedAlias == null) {
                throw new IllegalArgumentException("A1 file contains no private key");
            }
            Key key = store.getKey(selectedAlias, workingPassword);
            if (!(key instanceof PrivateKey privateKey)
                    || !(store.getCertificate(selectedAlias) instanceof X509Certificate certificate)
                    || !"RSA".equals(privateKey.getAlgorithm())
                    || !"RSA".equals(certificate.getPublicKey().getAlgorithm())) {
                throw new IllegalArgumentException("A1 requires an RSA private key and X.509 certificate");
            }
            certificate.checkValidity();
            boolean[] usage = certificate.getKeyUsage();
            if (certificate.getBasicConstraints() >= 0
                    || usage != null && (usage.length == 0 || !usage[0])) {
                throw new IllegalArgumentException("A1 certificate cannot sign NFC-e");
            }
            byte[] challenge = "Farelo A1 key match".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Signature proof = Signature.getInstance("SHA256withRSA");
            proof.initSign(privateKey);
            proof.update(challenge);
            byte[] signed = proof.sign();
            proof.initVerify(certificate.getPublicKey());
            proof.update(challenge);
            if (!proof.verify(signed)) {
                throw new IllegalArgumentException("A1 key does not match certificate");
            }
            return new Pkcs12SigningCredentials(privateKey, certificate);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            // Do not propagate provider errors: they may include the file path.
            throw new IllegalArgumentException("Could not read A1 PKCS#12 file");
        } finally {
            Arrays.fill(workingPassword, '\0');
        }
    }

    @Override
    public String toString() {
        return "Pkcs12SigningCredentials[REDACTED]";
    }
}
