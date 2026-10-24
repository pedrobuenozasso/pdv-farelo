package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfceOfficialSchemaPackageTests {
    @TempDir Path mount;

    @Test
    void loadsOnlyThePinnedLocalPackage() throws Exception {
        Path packageDirectory = installFixture();
        assertThatCode(() -> NfceOfficialSchemaPackage.load(mount)).doesNotThrowAnyException();

        Files.writeString(packageDirectory.resolve("tiposBasico_v4.00.xsd"), "tampered");
        assertThatThrownBy(() -> NfceOfficialSchemaPackage.load(mount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NFC-e XSD package is unavailable or invalid");
    }

    @Test
    void missingPackageFailsBeforePreparationCanBeConstructed() {
        assertThatThrownBy(() -> NfceOfficialSchemaPackage.load(mount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NFC-e XSD package is unavailable or invalid");
    }

    @Test
    void incompletePackageFailsClosed() throws Exception {
        Path packageDirectory = installFixture();
        Files.delete(packageDirectory.resolve("DFeTiposBasicos_v1.00.xsd"));
        assertThatThrownBy(() -> NfceOfficialSchemaPackage.load(mount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NFC-e XSD package is unavailable or invalid");
    }

    private Path installFixture() throws Exception {
        Path source = Path.of(getClass().getResource("/nfe-xsd/PL_010f_v1.04/nfe_v4.00.xsd").toURI())
                .getParent();
        Path target = Files.createDirectory(mount.resolve("PL_010f_v1.04"));
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
        return target;
    }
}
