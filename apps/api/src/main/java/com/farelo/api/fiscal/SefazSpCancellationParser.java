package com.farelo.api.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/** Accepts only a confirmed, linked cancellation event for this access key. */
final class SefazSpCancellationParser {
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final int MAX_XML_BYTES = 5_000_000;

    private SefazSpCancellationParser() { }

    static Cancelled parse(FiscalCancellationRequest request, FiscalCancellationResponse response) {
        if (request.signedEventXml().getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES
                || response.rawXml().getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) {
            throw new IllegalArgumentException("oversized cancellation XML");
        }
        try {
            Document event = SefazSpAuthorizationParser.parseXml(request.signedEventXml());
            Element eventBatch = event.getDocumentElement();
            require(eventBatch, NFE_NS, "envEvento");
            String lotId = text(eventBatch, "idLote");
            Element eventRoot = SefazSpAuthorizationParser.child(eventBatch, NFE_NS, "evento");
            if (!"1.00".equals(eventBatch.getAttribute("versao"))
                    || !lotId.matches("[0-9]{1,15}")
                    || !"1.00".equals(eventRoot.getAttribute("versao"))
                    || event.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").getLength() != 1) {
                throw new IllegalArgumentException("unsigned or invalid cancellation event");
            }
            Element sent = SefazSpAuthorizationParser.child(eventRoot, NFE_NS, "infEvento");
            String id = "ID110111" + request.accessKey() + "01";
            if (!id.equals(sent.getAttribute("Id"))
                    || !"35".equals(text(sent, "cOrgao"))
                    || !"2".equals(text(sent, "tpAmb"))
                    || !request.accessKey().substring(6, 20).equals(text(sent, "CNPJ"))
                    || !request.accessKey().equals(text(sent, "chNFe"))
                    || !"110111".equals(text(sent, "tpEvento"))
                    || !"1".equals(text(sent, "nSeqEvento"))
                    || !"1.00".equals(text(sent, "verEvento"))) {
                throw new IllegalArgumentException("cancellation event does not match NFC-e");
            }
            OffsetDateTime.parse(text(sent, "dhEvento"));
            Element detail = SefazSpAuthorizationParser.child(sent, NFE_NS, "detEvento");
            String originalProtocol = text(detail, "nProt");
            String justification = text(detail, "xJust");
            if (!"1.00".equals(detail.getAttribute("versao"))
                    || !"Cancelamento".equals(text(detail, "descEvento"))
                    || !originalProtocol.matches("[0-9]{15}|[0-9]{17}")
                    || justification.length() < 15 || justification.length() > 255) {
                throw new IllegalArgumentException("invalid cancellation detail");
            }

            Document soap = SefazSpAuthorizationParser.parseXml(response.rawXml());
            Element envelope = soap.getDocumentElement();
            require(envelope, SOAP_NS, "Envelope");
            Element body = SefazSpAuthorizationParser.child(envelope, SOAP_NS, "Body");
            Element result = SefazSpAuthorizationParser.child(body, SERVICE_NS, "nfeResultMsg");
            Element batch = SefazSpAuthorizationParser.child(result, NFE_NS, "retEnvEvento");
            if (!"1.00".equals(batch.getAttribute("versao"))
                    || !lotId.equals(text(batch, "idLote"))
                    || !"2".equals(text(batch, "tpAmb"))
                    || !"35".equals(text(batch, "cOrgao"))
                    || !"128".equals(text(batch, "cStat"))
                    || text(batch, "verAplic").isBlank()
                    || text(batch, "xMotivo").isBlank()) {
                throw new IllegalArgumentException("SEFAZ cancellation batch is not final");
            }
            Element returned = SefazSpAuthorizationParser.child(batch, NFE_NS, "retEvento");
            Element receipt = SefazSpAuthorizationParser.child(returned, NFE_NS, "infEvento");
            if (!"1.00".equals(returned.getAttribute("versao"))
                    || !"2".equals(text(receipt, "tpAmb"))
                    || !"35".equals(text(receipt, "cOrgao"))
                    || !"135".equals(text(receipt, "cStat"))
                    || !request.accessKey().equals(text(receipt, "chNFe"))
                    || !"110111".equals(text(receipt, "tpEvento"))
                    || !"1".equals(text(receipt, "nSeqEvento"))
                    || text(receipt, "verAplic").isBlank()
                    || text(receipt, "xMotivo").isBlank()
                    || text(receipt, "xEvento").isBlank()) {
                throw new IllegalArgumentException("SEFAZ did not confirm this cancellation");
            }
            String eventProtocol = text(receipt, "nProt");
            if (!eventProtocol.matches("[0-9]{15}|[0-9]{17}")) {
                throw new IllegalArgumentException("invalid cancellation protocol");
            }
            OffsetDateTime cancelledAt = OffsetDateTime.parse(text(receipt, "dhRegEvento"));

            Document processed = SefazSpAuthorizationParser.parseXml("<procEventoNFe xmlns=\"" + NFE_NS
                    + "\" versao=\"1.00\"/>");
            processed.getDocumentElement().appendChild(processed.importNode(eventRoot, true));
            processed.getDocumentElement().appendChild(processed.importNode(returned, true));
            return new Cancelled(originalProtocol, eventProtocol, cancelledAt, serialize(processed));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid SEFAZ cancellation response");
        }
    }

    private static String text(Element parent, String name) {
        return SefazSpAuthorizationParser.text(parent, name);
    }

    private static void require(Element element, String namespace, String name) {
        if (element == null || !namespace.equals(element.getNamespaceURI())
                || !name.equals(element.getLocalName())) {
            throw new IllegalArgumentException("unexpected fiscal XML element");
        }
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
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

    record Cancelled(String originalProtocol, String eventProtocol, OffsetDateTime cancelledAt,
                     String processedEventXml) {
        @Override public String toString() { return "Cancelled[REDACTED]"; }
    }
}
