package com.farelo.api.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/** Interprets only a synchronous, authorized SEFAZ-SP homologation reply. */
final class SefazSpAuthorizationParser {
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4";
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final int MAX_RESPONSE_BYTES = 5_000_000;

    private SefazSpAuthorizationParser() { }

    static Authorized parse(FiscalAuthorizationRequest request, FiscalAuthorizationResponse response) {
        if (response.rawXml().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("oversized SEFAZ response");
        }
        try {
            Document soap = parseXml(response.rawXml());
            if (!SOAP_NS.equals(soap.getDocumentElement().getNamespaceURI())
                    || !"Envelope".equals(soap.getDocumentElement().getLocalName())) {
                throw new IllegalArgumentException("invalid SEFAZ SOAP envelope");
            }
            Element body = child(soap.getDocumentElement(), SOAP_NS, "Body");
            Element result = child(body, SERVICE_NS, "nfeResultMsg");
            Element batch = child(result, NFE_NS, "retEnviNFe");
            if (!"4.00".equals(batch.getAttribute("versao"))
                    || !"2".equals(text(batch, "tpAmb"))
                    || !"104".equals(text(batch, "cStat"))
                    || !"35".equals(text(batch, "cUF"))
                    || text(batch, "verAplic").isBlank()
                    || text(batch, "xMotivo").isBlank()) {
                throw new IllegalArgumentException("SEFAZ batch is not authorized");
            }
            OffsetDateTime.parse(text(batch, "dhRecbto"));
            Element protocol = child(batch, NFE_NS, "protNFe");
            return fromProtocol(request, protocol);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            // Never expose raw XML, network details or customer data in errors.
            throw new IllegalArgumentException("invalid SEFAZ authorization response");
        }
    }

    static Authorized fromProtocol(FiscalAuthorizationRequest request, Element protocol) throws Exception {
        Element details = child(protocol, NFE_NS, "infProt");
        if (!"4.00".equals(protocol.getAttribute("versao"))
                || !"2".equals(text(details, "tpAmb"))
                || !"100".equals(text(details, "cStat"))
                || !request.accessKey().equals(text(details, "chNFe"))
                || text(details, "verAplic").isBlank()
                || text(details, "xMotivo").isBlank()) {
            throw new IllegalArgumentException("SEFAZ protocol is not authorization for this NFC-e");
        }
        String number = text(details, "nProt");
        if (!number.matches("[0-9]{15}|[0-9]{17}")) {
            throw new IllegalArgumentException("invalid SEFAZ protocol number");
        }
        OffsetDateTime receivedAt = OffsetDateTime.parse(text(details, "dhRecbto"));

        // This validates the local key, model, QR and reference before
        // the signed XML is stored alongside the SEFAZ protocol.
        SefazSpNfceProvider.envelope(request);
        Document signed = parseXml(request.signedXml());
        if (!NFE_NS.equals(signed.getDocumentElement().getNamespaceURI())
                || !"NFe".equals(signed.getDocumentElement().getLocalName())) {
            throw new IllegalArgumentException("invalid signed NFC-e");
        }
        Document processed = factory().newDocumentBuilder().newDocument();
        Element root = processed.createElementNS(NFE_NS, "nfeProc");
        root.setAttribute("versao", "4.00");
        processed.appendChild(root);
        root.appendChild(processed.importNode(signed.getDocumentElement(), true));
        root.appendChild(processed.importNode(protocol, true));
        return new Authorized(number, receivedAt, serialize(processed));
    }

    static Document parseXml(String xml) throws Exception {
        var builder = factory().newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException error)
                    throws org.xml.sax.SAXParseException { throw error; }
        });
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static DocumentBuilderFactory factory() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    static Element child(Element parent, String namespace, String name) {
        Element found = null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && namespace.equals(node.getNamespaceURI()) && name.equals(node.getLocalName())) {
                if (found != null) throw new IllegalArgumentException("duplicate SEFAZ element");
                found = (Element) node;
            }
        }
        if (found == null) throw new IllegalArgumentException("missing SEFAZ element");
        return found;
    }

    static String text(Element parent, String name) {
        return child(parent, NFE_NS, name).getTextContent().trim();
    }

    private static String serialize(Document document) throws Exception {
        var factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter output = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    record Authorized(String protocolNumber, OffsetDateTime authorizedAt, String processedXml) {
        @Override public String toString() { return "Authorized[REDACTED]"; }
    }
}
