package com.farelo.api.fiscal;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Validates a signed NFC-e against an explicitly installed, versioned XSD.
 * The application must supply the trusted official schema package; there is
 * no network fetch, bundled fallback or automatic choice of schema version.
 */
public final class NfceSchemaValidator {
    private static final int MAX_XML_BYTES = 5_000_000;
    private static final long MAX_MAIN_XSD_BYTES = 5_000_000;
    private final Schema schema;

    public NfceSchemaValidator(Path mainXsd) {
        if (mainXsd == null || !Files.isRegularFile(mainXsd) || !Files.isReadable(mainXsd)) {
            throw new IllegalArgumentException("NFC-e XSD is unavailable");
        }
        try {
            if (Files.size(mainXsd) == 0 || Files.size(mainXsd) > MAX_MAIN_XSD_BYTES) {
                throw new IllegalArgumentException("NFC-e XSD is unavailable");
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            // The official package contains relative local XSD includes.
            // HTTP(S) dependencies and network schema downloads stay disabled.
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            schema = factory.newSchema(mainXsd.toFile());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            // Provider errors can include filesystem paths; keep them private.
            throw new IllegalArgumentException("Could not load NFC-e XSD");
        }
    }

    public void validate(String signedXml) {
        if (signedXml == null || signedXml.isBlank()
                || signedXml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) {
            throw new IllegalArgumentException("NFC-e XML is missing or oversized");
        }
        String upper = signedXml.toUpperCase(Locale.ROOT);
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) {
            throw new IllegalArgumentException("NFC-e XML cannot contain a DTD");
        }
        try {
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new DefaultHandler() {
                @Override public void warning(SAXParseException ex) throws SAXException { throw ex; }
                @Override public void error(SAXParseException ex) throws SAXException { throw ex; }
                @Override public void fatalError(SAXParseException ex) throws SAXException { throw ex; }
            });
            validator.validate(new StreamSource(new StringReader(signedXml)));
        } catch (SAXException | IOException ex) {
            throw new IllegalArgumentException("NFC-e XSD validation failed");
        }
    }
}
