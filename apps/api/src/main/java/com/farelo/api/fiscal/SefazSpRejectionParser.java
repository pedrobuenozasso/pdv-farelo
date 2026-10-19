package com.farelo.api.fiscal;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;

/** Accepts only a definite per-note rejection, never an uncertain batch reply. */
final class SefazSpRejectionParser {
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4";
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final int MAX_RESPONSE_BYTES = 5_000_000;
    // These statuses may refer to an existing fiscal record; consult by key.
    private static final Set<String> RECONCILE_FIRST = Set.of("204", "205", "206", "218", "539");

    private SefazSpRejectionParser() { }

    static Rejected parse(FiscalAuthorizationRequest request, FiscalAuthorizationResponse response) {
        if (response.rawXml().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("oversized SEFAZ response");
        }
        try {
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
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException error)
                        throws org.xml.sax.SAXParseException { throw error; }
            });
            var soap = builder.parse(new InputSource(new StringReader(response.rawXml())));
            Element root = soap.getDocumentElement();
            if (!SOAP_NS.equals(root.getNamespaceURI()) || !"Envelope".equals(root.getLocalName())) {
                throw new IllegalArgumentException("invalid SEFAZ SOAP envelope");
            }
            Element batch = child(child(child(root, SOAP_NS, "Body"),
                    SERVICE_NS, "nfeResultMsg"), NFE_NS, "retEnviNFe");
            if (!"4.00".equals(batch.getAttribute("versao"))
                    || !"2".equals(text(batch, "tpAmb"))
                    || !"104".equals(text(batch, "cStat"))
                    || !"35".equals(text(batch, "cUF"))
                    || text(batch, "verAplic").isBlank()
                    || text(batch, "xMotivo").isBlank()
                    || hasChild(batch, NFE_NS, "infRec")) {
                throw new IllegalArgumentException("SEFAZ batch is not a final per-note result");
            }
            OffsetDateTime.parse(text(batch, "dhRecbto"));
            Element protocol = child(batch, NFE_NS, "protNFe");
            Element details = child(protocol, NFE_NS, "infProt");
            String code = text(details, "cStat");
            String reason = text(details, "xMotivo");
            if (!"4.00".equals(protocol.getAttribute("versao"))
                    || !"2".equals(text(details, "tpAmb"))
                    || !request.accessKey().equals(text(details, "chNFe"))
                    || text(details, "verAplic").isBlank()
                    || !code.matches("[0-9]{3}")
                    || Integer.parseInt(code) < 200
                    || RECONCILE_FIRST.contains(code)
                    || reason.length() > 255
                    || !reason.toLowerCase(java.util.Locale.ROOT).startsWith("rejeição:")
                        && !reason.toLowerCase(java.util.Locale.ROOT).startsWith("rejeicao:")) {
                throw new IllegalArgumentException("SEFAZ result requires reconciliation");
            }
            OffsetDateTime.parse(text(details, "dhRecbto"));
            SefazSpNfceProvider.envelope(request);
            return new Rejected(code, reason, response.rawXml(), request.signedXml());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid SEFAZ rejection response");
        }
    }

    private static Element child(Element parent, String namespace, String name) {
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

    private static boolean hasChild(Element parent, String namespace, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && namespace.equals(node.getNamespaceURI()) && name.equals(node.getLocalName())) return true;
        }
        return false;
    }

    private static String text(Element parent, String name) {
        return child(parent, NFE_NS, name).getTextContent().trim();
    }

    record Rejected(String code, String reason, String responseXml, String submittedXml) {
        @Override public String toString() { return "Rejected[REDACTED]"; }
    }
}
