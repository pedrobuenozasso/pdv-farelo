package com.farelo.api.fiscal;

import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;

/** Classifies a key consultation; only a matching protocol proves authorization. */
final class SefazSpConsultationParser {
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4";
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final int MAX_RESPONSE_BYTES = 5_000_000;

    private SefazSpConsultationParser() { }

    static Outcome parse(FiscalAuthorizationRequest request, FiscalConsultationResponse response) {
        if (response.rawXml().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("oversized SEFAZ consultation response");
        }
        try {
            Element root = SefazSpAuthorizationParser.parseXml(response.rawXml()).getDocumentElement();
            if (!SOAP_NS.equals(root.getNamespaceURI()) || !"Envelope".equals(root.getLocalName())) {
                throw new IllegalArgumentException("invalid SEFAZ SOAP envelope");
            }
            Element result = SefazSpAuthorizationParser.child(
                    SefazSpAuthorizationParser.child(root, SOAP_NS, "Body"), SERVICE_NS, "nfeResultMsg");
            Element consultation = SefazSpAuthorizationParser.child(result, NFE_NS, "retConsSitNFe");
            if (!"4.00".equals(consultation.getAttribute("versao"))
                    || !"2".equals(SefazSpAuthorizationParser.text(consultation, "tpAmb"))
                    || !"35".equals(SefazSpAuthorizationParser.text(consultation, "cUF"))
                    || !request.accessKey().equals(SefazSpAuthorizationParser.text(consultation, "chNFe"))
                    || SefazSpAuthorizationParser.text(consultation, "verAplic").isBlank()
                    || SefazSpAuthorizationParser.text(consultation, "xMotivo").isBlank()) {
                throw new IllegalArgumentException("SEFAZ consultation does not match this NFC-e");
            }
            String status = SefazSpAuthorizationParser.text(consultation, "cStat");
            if ("100".equals(status)) {
                Element protocol = SefazSpAuthorizationParser.child(consultation, NFE_NS, "protNFe");
                return new Outcome(Kind.AUTHORIZED,
                        SefazSpAuthorizationParser.fromProtocol(request, protocol));
            }
            if ("217".equals(status)) return new Outcome(Kind.NOT_FOUND, null);
            return new Outcome(Kind.UNRESOLVED, null);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid SEFAZ consultation response");
        }
    }

    enum Kind { AUTHORIZED, NOT_FOUND, UNRESOLVED }

    record Outcome(Kind kind, SefazSpAuthorizationParser.Authorized authorization) {
        @Override public String toString() { return "ConsultationOutcome[REDACTED]"; }
    }
}
