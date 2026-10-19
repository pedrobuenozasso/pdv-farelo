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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * FARELO-174: sends one already signed NFC-e to SEFAZ-SP homologation.
 * Construction requires an externally configured mTLS client. The adapter
 * is intentionally not a Spring bean; no fiscal sale can trigger it yet.
 */
public final class SefazSpNfceProvider implements FiscalProvider {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4";
    private static final String ACTION = SERVICE_NS + "/nfeAutorizacaoLote";
    private static final int MAX_XML_BYTES = 5_000_000;
    private static final int MAX_RESPONSE_BYTES = 5_000_000;
    private final URI endpoint;
    private final Transport transport;

    public SefazSpNfceProvider(SefazSpNfceEnvironment environment, HttpClient mutualTlsClient) {
        this(environment, new HttpTransport(Objects.requireNonNull(mutualTlsClient, "mutualTlsClient")));
    }

    SefazSpNfceProvider(SefazSpNfceEnvironment environment, Transport transport) {
        if (environment == null || environment.mode() != SefazSpNfceEnvironment.Mode.HOMOLOGATION) {
            throw new IllegalArgumentException("only SEFAZ-SP homologation is supported");
        }
        this.endpoint = environment.endpoint(SefazSpNfceEnvironment.Service.AUTHORIZE);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public FiscalAuthorizationResponse authorize(FiscalAuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        String envelope = envelope(request);
        try {
            Transport.Reply reply = transport.send(endpoint, ACTION, envelope);
            if (reply == null || reply.status() != 200 || reply.body() == null
                    || reply.body().isBlank() || reply.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new FiscalSubmissionUncertainException();
            }
            return new FiscalAuthorizationResponse(reply.body());
        } catch (FiscalSubmissionUncertainException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FiscalSubmissionUncertainException();
        } catch (Exception ex) {
            // An I/O failure may have happened after SEFAZ received the note.
            // Never log XML or automatically resend; consult the access key.
            throw new FiscalSubmissionUncertainException();
        }
    }

    static String envelope(FiscalAuthorizationRequest request) {
        String xml = request.signedXml();
        if (xml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES
                || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            throw new IllegalArgumentException("invalid or oversized NFC-e XML");
        }
        try {
            Document nfe = parse(xml);
            Element root = nfe.getDocumentElement();
            require(root, NFE_NS, "NFe");
            Element inf = onlyDirectChild(root, NFE_NS, "infNFe");
            String key = request.accessKey();
            if (!key.startsWith("35") || !key.equals(inf.getAttribute("Id").replaceFirst("^NFe", ""))
                    || !"4.00".equals(inf.getAttribute("versao"))) {
                throw new IllegalArgumentException("access key does not match signed XML");
            }
            Element ide = onlyDirectChild(inf, NFE_NS, "ide");
            if (!"65".equals(value(ide, "mod")) || !"2".equals(value(ide, "tpAmb"))
                    || !"1".equals(value(ide, "tpEmis")) || !"35".equals(value(ide, "cUF"))) {
                throw new IllegalArgumentException("only online SP homologation NFC-e is supported");
            }
            Element supplemental = onlyDirectChild(root, NFE_NS, "infNFeSupl");
            String expectedQr = "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p="
                    + key + "|3|2";
            if (!expectedQr.equals(value(supplemental, "qrCode"))
                    || !"https://www.homologacao.nfce.fazenda.sp.gov.br/consulta"
                            .equals(value(supplemental, "urlChave"))) {
                throw new IllegalArgumentException("QR Code does not match access key");
            }
            Element signature = onlyDirectChild(root, "http://www.w3.org/2000/09/xmldsig#", "Signature");
            var references = signature.getElementsByTagNameNS(
                    "http://www.w3.org/2000/09/xmldsig#", "Reference");
            if (references.getLength() != 1 || !(references.item(0) instanceof Element reference)
                    || !("#NFe" + key).equals(reference.getAttribute("URI"))) {
                throw new IllegalArgumentException("signature reference does not match access key");
            }
            int rootChildren = 0;
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE) rootChildren++;
            }
            if (rootChildren != 3) throw new IllegalArgumentException("unexpected NFe child");
            Document soap = newDocument();
            // Default namespaces are rebound at each SOAP layer. A SOAP prefix
            // inherited by infNFe would change inclusive XML canonicalization.
            Element envelope = soap.createElementNS(SOAP_NS, "Envelope");
            soap.appendChild(envelope);
            Element body = soap.createElementNS(SOAP_NS, "Body");
            envelope.appendChild(body);
            Element data = soap.createElementNS(SERVICE_NS, "nfeDadosMsg");
            body.appendChild(data);
            Element batch = soap.createElementNS(NFE_NS, "enviNFe");
            batch.setAttribute("versao", "4.00");
            data.appendChild(batch);
            Element lot = soap.createElementNS(NFE_NS, "idLote");
            lot.setTextContent(key.substring(29));
            batch.appendChild(lot);
            Element synchronous = soap.createElementNS(NFE_NS, "indSinc");
            synchronous.setTextContent("1");
            batch.appendChild(synchronous);
            batch.appendChild(soap.importNode(root, true));
            return serialize(soap);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid NFC-e transmission XML");
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = secureFactory();
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void fatalError(SAXParseException exception) throws SAXParseException {
                throw exception;
            }
        });
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Document newDocument() throws Exception {
        return secureFactory().newDocumentBuilder().newDocument();
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
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
        return factory;
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter output = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    private static String value(Element parent, String name) {
        return onlyDirectChild(parent, NFE_NS, name).getTextContent();
    }

    private static Element onlyDirectChild(Element parent, String namespace, String name) {
        Element found = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && namespace.equals(child.getNamespaceURI())
                    && name.equals(child.getLocalName())) {
                if (found != null) throw new IllegalArgumentException("duplicate " + name);
                found = (Element) child;
            }
        }
        if (found == null) throw new IllegalArgumentException("missing " + name);
        return found;
    }

    private static void require(Element element, String namespace, String name) {
        if (element == null || !namespace.equals(element.getNamespaceURI()) || !name.equals(element.getLocalName())) {
            throw new IllegalArgumentException("expected " + name);
        }
    }

    interface Transport {
        Reply send(URI endpoint, String action, String envelope) throws IOException, InterruptedException;
        record Reply(int status, String body) { }
    }

    private record HttpTransport(HttpClient client) implements Transport {
        @Override
        public Reply send(URI endpoint, String action, String envelope) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + action + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Reply(response.statusCode(), response.body());
        }
    }
}
