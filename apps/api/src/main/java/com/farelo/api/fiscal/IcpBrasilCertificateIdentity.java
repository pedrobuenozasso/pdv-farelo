package com.farelo.api.fiscal;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Reads the ICP-Brasil legal-entity CNPJ from SAN otherName OID 2.16.76.1.3.3. */
final class IcpBrasilCertificateIdentity {
    private static final byte[] CNPJ_OID = {0x60, 0x4c, 0x01, 0x03, 0x03};

    private IcpBrasilCertificateIdentity() { }

    static void requireIssuerCnpj(X509Certificate certificate, String expectedCnpj) {
        if (certificate == null || expectedCnpj == null || !expectedCnpj.matches("[0-9]{14}")) {
            throw new IllegalArgumentException("valid certificate and issuer CNPJ are required");
        }
        try {
            Collection<List<?>> alternatives = certificate.getSubjectAlternativeNames();
            String found = null;
            if (alternatives != null) for (List<?> entry : alternatives) {
                if (entry == null || entry.size() < 2 || !Integer.valueOf(0).equals(entry.get(0))
                        || !(entry.get(1) instanceof byte[] encoded)) continue;
                String candidate = cnpjOtherName(encoded);
                if (candidate == null) continue;
                if (found != null) throw new IllegalArgumentException("duplicate certificate CNPJ identity");
                found = candidate;
            }
            if (!expectedCnpj.equals(found)) {
                throw new IllegalArgumentException("certificate CNPJ differs from issuer");
            }
        } catch (CertificateParsingException ex) {
            throw new IllegalArgumentException("certificate CNPJ identity is invalid");
        }
    }

    /** Parses only the strict OtherName DER shape; unrelated OIDs are ignored. */
    static String cnpjOtherName(byte[] encoded) {
        if (encoded == null || encoded.length > 256) {
            throw new IllegalArgumentException("certificate otherName is invalid");
        }
        Der sequence = Der.read(encoded, 0, 0x30);
        if (sequence.end != encoded.length) throw invalid();
        Der oid = Der.read(encoded, sequence.start, 0x06);
        if (!Arrays.equals(Arrays.copyOfRange(encoded, oid.start, oid.end), CNPJ_OID)) return null;
        Der explicit = Der.read(encoded, oid.end, 0xa0);
        if (explicit.end != sequence.end) throw invalid();
        if (explicit.start >= explicit.end) throw invalid();
        int tag = encoded[explicit.start] & 0xff;
        // DOC-ICP-04 permits OCTET STRING or PrintableString here.
        if (tag != 0x04 && tag != 0x13) throw invalid();
        Der value = Der.read(encoded, explicit.start, tag);
        if (value.end != explicit.end || value.end - value.start != 14) throw invalid();
        String cnpj = new String(encoded, value.start, 14, StandardCharsets.US_ASCII);
        if (!cnpj.matches("[0-9]{14}")) throw invalid();
        return cnpj;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("certificate CNPJ identity is invalid");
    }

    private record Der(int start, int end) {
        static Der read(byte[] bytes, int offset, int expectedTag) {
            if (offset < 0 || offset + 2 > bytes.length || (bytes[offset] & 0xff) != expectedTag) {
                throw invalid();
            }
            int lengthByte = bytes[offset + 1] & 0xff;
            int length;
            int start;
            if (lengthByte < 128) {
                length = lengthByte;
                start = offset + 2;
            } else if (lengthByte == 0x81 && offset + 3 <= bytes.length) {
                length = bytes[offset + 2] & 0xff;
                start = offset + 3;
                if (length < 128) throw invalid();
            } else if (lengthByte == 0x82 && offset + 4 <= bytes.length) {
                length = (bytes[offset + 2] & 0xff) << 8 | (bytes[offset + 3] & 0xff);
                start = offset + 4;
                if (length <= 255) throw invalid();
            } else {
                throw invalid();
            }
            if (length < 0 || start + length > bytes.length) throw invalid();
            return new Der(start, start + length);
        }
    }
}
