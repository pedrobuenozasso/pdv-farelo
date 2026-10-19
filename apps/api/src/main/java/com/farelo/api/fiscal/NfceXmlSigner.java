package com.farelo.api.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * FARELO-173: signs only a structurally checked, unsigned NFC-e homologation
 * draft. This does not validate current XSD/tax rules, add CSC/QR code, or
 * authorize a document. No key or certificate is loaded or persisted here.
 */
public final class NfceXmlSigner {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final int MAX_XML_BYTES = 5_000_000;

    public String signHomologation(String unsignedXml, PrivateKey privateKey, X509Certificate certificate) {
        if (privateKey == null || certificate == null || !"RSA".equals(privateKey.getAlgorithm())
                || !"RSA".equals(certificate.getPublicKey().getAlgorithm())) {
            throw new IllegalArgumentException("RSA private key and X.509 certificate are required");
        }
        try {
            certificate.checkValidity();
            boolean[] keyUsage = certificate.getKeyUsage();
            if (certificate.getBasicConstraints() >= 0
                    || keyUsage != null && (keyUsage.length == 0 || !keyUsage[0])) {
                throw new IllegalArgumentException("an end-entity signing certificate is required");
            }
            Signature keyCheck = Signature.getInstance("SHA256withRSA");
            byte[] challenge = "Farelo NFC-e key match".getBytes(StandardCharsets.UTF_8);
            keyCheck.initSign(privateKey);
            keyCheck.update(challenge);
            byte[] proof = keyCheck.sign();
            keyCheck.initVerify(certificate.getPublicKey());
            keyCheck.update(challenge);
            if (!keyCheck.verify(proof)) throw new IllegalArgumentException("private key and certificate differ");
            Document document = parse(unsignedXml);
            Element root = document.getDocumentElement();
            requireTag(root, "NFe");
            Element infNFe = onlyDirectChild(root, "infNFe");
            if (document.getElementsByTagNameNS(DSIG_NS, "Signature").getLength() != 0) {
                throw new IllegalArgumentException("XML already contains a signature");
            }
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeType() == Node.ELEMENT_NODE && node != infNFe) {
                    throw new IllegalArgumentException("unexpected element beside infNFe");
                }
            }
            String id = infNFe.getAttribute("Id");
            if (!id.matches("NFe[0-9]{44}") || !"4.00".equals(infNFe.getAttribute("versao"))) {
                throw new IllegalArgumentException("invalid NFC-e infNFe Id or version");
            }
            checkKeyFields(infNFe, id.substring(3));
            for (Element element : allElements(document)) {
                if (element != infNFe && element.hasAttribute("Id")) {
                    throw new IllegalArgumentException("duplicate or unexpected XML Id attribute");
                }
            }
            infNFe.setIdAttribute("Id", true);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            Reference reference = factory.newReference("#" + id,
                    factory.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)), null, null);
            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null), List.of(reference));
            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(
                    keyInfoFactory.newX509Data(List.of(certificate))));
            DOMSignContext context = new DOMSignContext(privateKey, root);
            context.setDefaultNamespacePrefix("");
            factory.newXMLSignature(signedInfo, keyInfo).sign(context);
            return serialize(document);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("could not sign NFC-e draft", ex);
        }
    }

    private static Document parse(String xml) throws Exception {
        if (xml == null || xml.isBlank() || xml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES
                || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            throw new IllegalArgumentException("invalid or oversized XML");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void fatalError(SAXParseException exception) throws SAXParseException {
                throw exception;
            }
        });
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter result = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(result));
        return result.toString();
    }

    private static void checkKeyFields(Element infNFe, String key) {
        Element ide = onlyDirectChild(infNFe, "ide");
        Element emit = onlyDirectChild(infNFe, "emit");
        String issued = value(ide, "dhEmi");
        String yearMonth;
        try {
            OffsetDateTime date = OffsetDateTime.parse(issued);
            yearMonth = String.format(Locale.ROOT, "%02d%02d", date.getYear() % 100, date.getMonthValue());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid dhEmi", ex);
        }
        String expected = value(ide, "cUF") + yearMonth + value(emit, "CNPJ")
                + value(ide, "mod") + pad(value(ide, "serie"), 3)
                + pad(value(ide, "nNF"), 9) + value(ide, "tpEmis") + value(ide, "cNF");
        if (!expected.matches("[0-9]{43}") || !key.startsWith(expected)
                || !key.substring(43).equals(value(ide, "cDV"))
                || !"35".equals(value(ide, "cUF")) || !"65".equals(value(ide, "mod"))
                || !"2".equals(value(ide, "tpAmb")) || !"1".equals(value(ide, "tpEmis"))
                || !key.equals(expected + checkDigit(expected))) {
            throw new IllegalArgumentException("infNFe Id does not match homologation NFC-e fields");
        }
    }

    private static int checkDigit(String digits) {
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int value = 11 - sum % 11;
        return value >= 10 ? 0 : value;
    }

    private static String pad(String numeric, int width) {
        if (!numeric.matches("[0-9]{1," + width + "}")) {
            throw new IllegalArgumentException("invalid numeric key field");
        }
        return "0".repeat(width - numeric.length()) + numeric;
    }

    private static String value(Element parent, String localName) {
        Element child = onlyDirectChild(parent, localName);
        return child.getTextContent();
    }

    private static Element onlyDirectChild(Element parent, String localName) {
        Element found = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && NFE_NS.equals(child.getNamespaceURI())
                    && localName.equals(child.getLocalName())) {
                if (found != null) throw new IllegalArgumentException("duplicate " + localName);
                found = (Element) child;
            }
        }
        if (found == null) throw new IllegalArgumentException("missing " + localName);
        return found;
    }

    private static void requireTag(Element element, String localName) {
        if (element == null || !NFE_NS.equals(element.getNamespaceURI())
                || !localName.equals(element.getLocalName())) {
            throw new IllegalArgumentException("expected " + localName + " in NFC-e namespace");
        }
    }

    private static List<Element> allElements(Document document) {
        NodeList nodes = document.getElementsByTagName("*");
        java.util.ArrayList<Element> result = new java.util.ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        return result;
    }
}
