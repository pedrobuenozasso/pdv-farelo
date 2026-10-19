package com.farelo.api.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Adds the CSC-free online QR Code v3 to a signed SP homologation NFC-e. */
public final class NfceQrCodeV3 {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String QR_BASE = "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode";
    private static final String CONSULTATION = "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta";
    private static final int MAX_XML_BYTES = 5_000_000;

    public String addToSignedHomologation(String signedXml) {
        if (signedXml == null || signedXml.isBlank()
                || signedXml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES
                || signedXml.contains("<!DOCTYPE") || signedXml.contains("<!ENTITY")) {
            throw new IllegalArgumentException("invalid or oversized signed XML");
        }
        try {
            Document document = parse(signedXml);
            Element root = document.getDocumentElement();
            requireTag(root, NFE_NS, "NFe");
            Element inf = null;
            Element signature = null;
            int elementCount = 0;
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                elementCount++;
                Element element = (Element) child;
                if (NFE_NS.equals(element.getNamespaceURI()) && "infNFe".equals(element.getLocalName())) {
                    inf = element;
                } else if (DSIG_NS.equals(element.getNamespaceURI()) && "Signature".equals(element.getLocalName())) {
                    signature = element;
                } else {
                    throw new IllegalArgumentException("unexpected child of NFe");
                }
            }
            if (elementCount != 2 || inf == null || signature == null
                    || inf.getNextSibling() != signature) {
                throw new IllegalArgumentException("expected infNFe followed by XML signature");
            }
            String id = inf.getAttribute("Id");
            if (!"4.00".equals(inf.getAttribute("versao")) || !id.matches("NFe35[0-9]{42}")) {
                throw new IllegalArgumentException("invalid SP NFC-e access key");
            }
            Element ide = onlyChild(inf, "ide");
            if (!"65".equals(value(ide, "mod")) || !"2".equals(value(ide, "tpAmb"))
                    || !"1".equals(value(ide, "tpEmis")) || !"35".equals(value(ide, "cUF"))) {
                throw new IllegalArgumentException("only online SP homologation NFC-e is supported");
            }
            String reference = signature.getElementsByTagNameNS(DSIG_NS, "Reference").item(0) instanceof Element ref
                    ? ref.getAttribute("URI") : "";
            if (!reference.equals("#" + id)) {
                throw new IllegalArgumentException("signature does not reference the NFC-e access key");
            }
            Element supplemental = document.createElementNS(NFE_NS, "infNFeSupl");
            Element qrCode = document.createElementNS(NFE_NS, "qrCode");
            qrCode.setTextContent(QR_BASE + "?p=" + id.substring(3) + "|3|2");
            supplemental.appendChild(qrCode);
            Element urlChave = document.createElementNS(NFE_NS, "urlChave");
            urlChave.setTextContent(CONSULTATION);
            supplemental.appendChild(urlChave);
            root.insertBefore(supplemental, signature);
            return serialize(document);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("could not add NFC-e QR Code", ex);
        }
    }

    private static Document parse(String xml) throws Exception {
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
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter output = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    private static String value(Element parent, String name) {
        return onlyChild(parent, name).getTextContent();
    }

    private static Element onlyChild(Element parent, String name) {
        Element found = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && NFE_NS.equals(child.getNamespaceURI())
                    && name.equals(child.getLocalName())) {
                if (found != null) throw new IllegalArgumentException("duplicate " + name);
                found = (Element) child;
            }
        }
        if (found == null) throw new IllegalArgumentException("missing " + name);
        return found;
    }

    private static void requireTag(Element element, String namespace, String name) {
        if (element == null || !namespace.equals(element.getNamespaceURI())
                || !name.equals(element.getLocalName())) {
            throw new IllegalArgumentException("expected " + name);
        }
    }
}
