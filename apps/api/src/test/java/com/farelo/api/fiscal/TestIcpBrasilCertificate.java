package com.farelo.api.fiscal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Minimal in-memory X.509 fixture with ICP-Brasil's CNPJ otherName. */
final class TestIcpBrasilCertificate {
    private TestIcpBrasilCertificate() { }

    static X509Certificate issue(KeyPair pair, String cnpj) throws Exception {
        return issue(pair, cnpj, 0x13);
    }

    static X509Certificate issue(KeyPair pair, String cnpj, int cnpjTag) throws Exception {
        byte[] algorithm = seq(oid(1, 2, 840, 113549, 1, 1, 11), tag(0x05, new byte[0]));
        byte[] name = seq(tag(0x31, seq(oid(2, 5, 4, 3),
                tag(0x0c, "Farelo Test Only".getBytes(StandardCharsets.UTF_8)))));
        byte[] dates = seq(tag(0x17, "260101000000Z".getBytes(StandardCharsets.US_ASCII)),
                tag(0x17, "300101000000Z".getBytes(StandardCharsets.US_ASCII)));
        byte[] otherName = tag(0xa0, concat(oid(2, 16, 76, 1, 3, 3),
                tag(0xa0, tag(cnpjTag, cnpj.getBytes(StandardCharsets.US_ASCII)))));
        byte[] san = seq(otherName);
        byte[] extensions = tag(0xa3, seq(seq(oid(2, 5, 29, 17), tag(0x04, san))));
        byte[] tbs = seq(tag(0xa0, tag(0x02, BigInteger.valueOf(2).toByteArray())),
                tag(0x02, BigInteger.ONE.toByteArray()), algorithm, name, dates, name,
                pair.getPublic().getEncoded(), extensions);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(tbs);
        byte[] encoded = seq(tbs, algorithm, tag(0x03, concat(new byte[]{0}, signer.sign())));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] oid(int... arcs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int value = arcs[i];
            byte[] buffer = new byte[5];
            int at = buffer.length;
            buffer[--at] = (byte) (value & 0x7f);
            value >>>= 7;
            while (value > 0) {
                buffer[--at] = (byte) ((value & 0x7f) | 0x80);
                value >>>= 7;
            }
            out.write(buffer, at, buffer.length - at);
        }
        return tag(0x06, out.toByteArray());
    }

    private static byte[] seq(byte[]... values) { return tag(0x30, concat(values)); }

    private static byte[] tag(int tag, byte[] body) {
        byte[] length = body.length < 128 ? new byte[]{(byte) body.length}
                : body.length < 256 ? new byte[]{(byte) 0x81, (byte) body.length}
                : new byte[]{(byte) 0x82, (byte) (body.length >>> 8), (byte) body.length};
        return concat(new byte[]{(byte) tag}, length, body);
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) out.writeBytes(array);
        return out.toByteArray();
    }
}
