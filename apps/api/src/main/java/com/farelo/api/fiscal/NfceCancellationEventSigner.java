package com.farelo.api.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds and signs the ordinary cancellation event (110111) for a São Paulo
 * NFC-e in homologation. The caller must verify the original authorization
 * and SEFAZ response before changing fiscal state.
 * This class never loads credentials or transmits the event.
 */
public final class NfceCancellationEventSigner {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String VERSION = "1.00";
    private static final String EVENT_TYPE = "110111";
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public record Draft(String accessKey, String issuerCnpj, String authorizationProtocol,
                        String justification, OffsetDateTime eventTime,
                        OffsetDateTime authorizedAt, Boolean merchandiseLeft, long batchId) { }

    public String signHomologation(Draft draft, PrivateKey privateKey, X509Certificate certificate) {
        validate(draft, privateKey, certificate);
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            builderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            builderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = builderFactory.newDocumentBuilder().newDocument();
            Element batch = element(document, null, "envEvento");
            batch.setAttribute("versao", VERSION);
            document.appendChild(batch);
            add(document, batch, "idLote", Long.toString(draft.batchId()));
            Element event = element(document, batch, "evento");
            event.setAttribute("versao", VERSION);
            Element info = element(document, event, "infEvento");
            String id = "ID" + EVENT_TYPE + draft.accessKey() + "01";
            info.setAttribute("Id", id);
            info.setIdAttribute("Id", true);
            add(document, info, "cOrgao", "35");
            add(document, info, "tpAmb", "2");
            add(document, info, "CNPJ", draft.issuerCnpj());
            add(document, info, "chNFe", draft.accessKey());
            add(document, info, "dhEvento", EVENT_TIME.format(draft.eventTime()));
            add(document, info, "tpEvento", EVENT_TYPE);
            add(document, info, "nSeqEvento", "1");
            add(document, info, "verEvento", VERSION);
            Element detail = element(document, info, "detEvento");
            detail.setAttribute("versao", VERSION);
            add(document, detail, "descEvento", "Cancelamento");
            add(document, detail, "nProt", draft.authorizationProtocol());
            add(document, detail, "xJust", draft.justification());

            XMLSignatureFactory signatures = XMLSignatureFactory.getInstance("DOM");
            var reference = signatures.newReference("#" + id,
                    signatures.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(signatures.newTransform(javax.xml.crypto.dsig.Transform.ENVELOPED,
                                    (TransformParameterSpec) null),
                            signatures.newTransform(CanonicalizationMethod.INCLUSIVE,
                                    (TransformParameterSpec) null)), null, null);
            var signedInfo = signatures.newSignedInfo(
                    signatures.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE,
                            (C14NMethodParameterSpec) null),
                    signatures.newSignatureMethod(SignatureMethod.RSA_SHA1, null), List.of(reference));
            KeyInfoFactory keyInfoFactory = signatures.getKeyInfoFactory();
            var keyInfo = keyInfoFactory.newKeyInfo(List.of(
                    keyInfoFactory.newX509Data(List.of(certificate))));
            signatures.newXMLSignature(signedInfo, keyInfo).sign(new DOMSignContext(privateKey, event));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("could not sign NFC-e cancellation event", ex);
        }
    }

    private static void validate(Draft draft, PrivateKey privateKey, X509Certificate certificate) {
        if (draft == null || draft.accessKey() == null || !draft.accessKey().matches("35[0-9]{18}65[0-9]{22}")
                || draft.issuerCnpj() == null || !draft.issuerCnpj().matches("[0-9]{14}")
                || !draft.issuerCnpj().equals(draft.accessKey().substring(6, 20))
                || draft.authorizationProtocol() == null || !draft.authorizationProtocol().matches("[0-9]{15}|[0-9]{17}")
                || draft.justification() == null || draft.justification().length() < 15
                || draft.justification().length() > 255 || draft.justification().isBlank()
                || draft.eventTime() == null || draft.batchId() < 1 || draft.batchId() > 999_999_999_999_999L) {
            throw new IllegalArgumentException("invalid São Paulo homologation cancellation draft");
        }
        if (draft.authorizedAt() == null || !Boolean.FALSE.equals(draft.merchandiseLeft())) {
            throw new IllegalArgumentException("authorization time and confirmation of no merchandise departure are required");
        }
        Duration sinceAuthorization = Duration.between(draft.authorizedAt().toInstant(), draft.eventTime().toInstant());
        if (sinceAuthorization.isNegative() || sinceAuthorization.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("ordinary São Paulo NFC-e cancellation is outside the 30-minute window");
        }
        if (privateKey == null || certificate == null || !"RSA".equals(privateKey.getAlgorithm())
                || !"RSA".equals(certificate.getPublicKey().getAlgorithm())) {
            throw new IllegalArgumentException("RSA private key and X.509 certificate are required");
        }
        try {
            certificate.checkValidity();
            boolean[] usage = certificate.getKeyUsage();
            if (certificate.getBasicConstraints() >= 0 || usage != null && (usage.length == 0 || !usage[0])) {
                throw new IllegalArgumentException("an end-entity signing certificate is required");
            }
            Signature verifier = Signature.getInstance("SHA256withRSA");
            byte[] challenge = "Farelo cancellation key match".getBytes(StandardCharsets.UTF_8);
            verifier.initSign(privateKey);
            verifier.update(challenge);
            byte[] proof = verifier.sign();
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(challenge);
            if (!verifier.verify(proof)) throw new IllegalArgumentException("private key and certificate differ");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cancellation signing certificate", ex);
        }
    }

    private static Element element(Document document, Element parent, String name) {
        Element element = document.createElementNS(NFE_NS, name);
        if (parent != null) parent.appendChild(element);
        return element;
    }

    private static void add(Document document, Element parent, String name, String text) {
        element(document, parent, name).setTextContent(text);
    }
}
