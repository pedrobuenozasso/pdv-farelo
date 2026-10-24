package com.farelo.api.fiscal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Explicitly pins the reviewed 010f package installed outside the application image. */
public final class NfceOfficialSchemaPackage {
    public static final Path MOUNT_ROOT = Path.of("/opt/farelo-os/xsd");
    private static final String VERSION = "PL_010f_v1.04";
    private static final Map<String, String> FILE_HASHES = Map.of(
            "nfe_v4.00.xsd", "adce3646c13ceb54922ec3142fc1dc45bd4fb839ac35ad583e86c733c07d27df",
            "leiauteNFe_v4.00.xsd", "2bace939973916d54184ff3e2740041a932de5d79772f3363504504160f22542",
            "tiposBasico_v4.00.xsd", "772619c85723e598840667ca66e7298a250442df47eeb94b397d2a333ce62047",
            "DFeTiposBasicos_v1.00.xsd", "173577a4e3a9dc1d0deced85b89b955b6064bb5a4ae5a96f6727d2da8a694d09",
            "xmldsig-core-schema_v1.01.xsd", "f56744a5f51c03f027de13f39f869307091781a9ef1d91b1ebe14719ce28e1ac");

    private NfceOfficialSchemaPackage() { }

    public static NfceSchemaValidator loadMounted() {
        return load(MOUNT_ROOT);
    }

    static NfceSchemaValidator load(Path mountRoot) {
        if (mountRoot == null) {
            throw new IllegalArgumentException("NFC-e XSD package is unavailable or invalid");
        }
        Path directory = mountRoot.resolve(VERSION);
        try {
            for (var entry : FILE_HASHES.entrySet()) {
                Path file = directory.resolve(entry.getKey());
                if (!Files.isRegularFile(file) || !Files.isReadable(file) || Files.isSymbolicLink(file)) {
                    throw new IllegalArgumentException("NFC-e XSD package is unavailable or invalid");
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }
                if (!entry.getValue().equals(HexFormat.of().formatHex(digest.digest()))) {
                    throw new IllegalArgumentException("NFC-e XSD package is unavailable or invalid");
                }
            }
            return new NfceSchemaValidator(directory.resolve("nfe_v4.00.xsd"));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("NFC-e XSD package is unavailable or invalid");
        }
    }
}
