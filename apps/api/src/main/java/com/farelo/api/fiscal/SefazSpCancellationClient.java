package com.farelo.api.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Sends one signed cancellation event to the fixed SP homologation service. */
public final class SefazSpCancellationClient {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    // The authenticated SEFAZ-SP WSDL specifies the NF suffix for this endpoint.
    private static final String ACTION = SERVICE_NS + "/nfeRecepcaoEventoNF";
    private static final int MAX_XML_BYTES = 5_000_000;
    private final URI endpoint;
    private final Transport transport;

    public SefazSpCancellationClient(SefazSpNfceEnvironment environment, HttpClient mutualTlsClient) {
        this(environment, new HttpTransport(Objects.requireNonNull(mutualTlsClient, "mutualTlsClient")));
    }

    SefazSpCancellationClient(SefazSpNfceEnvironment environment, Transport transport) {
        if (environment == null || environment.mode() != SefazSpNfceEnvironment.Mode.HOMOLOGATION) {
            throw new IllegalArgumentException("only SEFAZ-SP homologation is supported");
        }
        this.endpoint = environment.endpoint(SefazSpNfceEnvironment.Service.RECEIVE_EVENT);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public FiscalCancellationResponse send(FiscalCancellationRequest request) {
        Objects.requireNonNull(request, "request");
        String body = envelope(request);
        try {
            Transport.Reply reply = transport.send(endpoint, ACTION, body);
            if (reply == null || reply.status() != 200 || reply.body() == null || reply.body().isBlank()
                    || reply.body().getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) {
                throw new FiscalSubmissionUncertainException();
            }
            return new FiscalCancellationResponse(reply.body());
        } catch (FiscalSubmissionUncertainException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FiscalSubmissionUncertainException();
        } catch (Exception ex) {
            // A network error can occur after SEFAZ received the event.
            // Never log the XML, expose transport details, or retry here.
            throw new FiscalSubmissionUncertainException();
        }
    }

    static String envelope(FiscalCancellationRequest request) {
        if (request.signedEventXml().getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) {
            throw new IllegalArgumentException("oversized cancellation event");
        }
        try {
            Document signed = SefazSpAuthorizationParser.parseXml(request.signedEventXml());
            Element batch = signed.getDocumentElement();
            if (!NFE_NS.equals(batch.getNamespaceURI()) || !"envEvento".equals(batch.getLocalName())
                    || !"1.00".equals(batch.getAttribute("versao"))
                    || !SefazSpAuthorizationParser.text(batch, "idLote").matches("[0-9]{1,15}")) {
                throw new IllegalArgumentException("invalid cancellation event batch");
            }
            Element event = SefazSpAuthorizationParser.child(batch, NFE_NS, "evento");
            Element info = SefazSpAuthorizationParser.child(event, NFE_NS, "infEvento");
            if (!"1.00".equals(event.getAttribute("versao"))
                    || !("ID110111" + request.accessKey() + "01").equals(info.getAttribute("Id"))
                    || !request.accessKey().equals(SefazSpAuthorizationParser.text(info, "chNFe"))
                    || !"110111".equals(SefazSpAuthorizationParser.text(info, "tpEvento"))
                    || !"2".equals(SefazSpAuthorizationParser.text(info, "tpAmb"))
                    || signed.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").getLength() != 1) {
                throw new IllegalArgumentException("signed cancellation event does not match NFC-e");
            }
            Document soap = SefazSpAuthorizationParser.parseXml("<Envelope xmlns=\"" + SOAP_NS
                    + "\"><Body><nfeDadosMsg xmlns=\"" + SERVICE_NS + "\"/></Body></Envelope>");
            Element wrapper = SefazSpAuthorizationParser.child(
                    SefazSpAuthorizationParser.child(soap.getDocumentElement(), SOAP_NS, "Body"),
                    SERVICE_NS, "nfeDadosMsg");
            wrapper.appendChild(soap.importNode(batch, true));
            return serialize(soap);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cancellation event XML");
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

    interface Transport {
        Reply send(URI endpoint, String action, String envelope) throws IOException, InterruptedException;
        record Reply(int status, String body) { }
    }

    private record HttpTransport(HttpClient client) implements Transport {
        @Override public Reply send(URI endpoint, String action, String envelope)
                throws IOException, InterruptedException {
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
