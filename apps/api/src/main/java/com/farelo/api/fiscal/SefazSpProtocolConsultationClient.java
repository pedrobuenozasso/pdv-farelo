package com.farelo.api.fiscal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Queries one NFC-e key in SEFAZ-SP homologation without resubmitting XML. */
public final class SefazSpProtocolConsultationClient {
    private static final String SERVICE_NS = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4";
    private static final String ACTION = SERVICE_NS + "/nfeConsultaNF";
    private static final int MAX_RESPONSE_BYTES = 5_000_000;
    private final URI endpoint;
    private final Transport transport;

    public SefazSpProtocolConsultationClient(SefazSpNfceEnvironment environment,
                                               HttpClient mutualTlsClient) {
        this(environment, new HttpTransport(Objects.requireNonNull(mutualTlsClient, "mutualTlsClient")));
    }

    SefazSpProtocolConsultationClient(SefazSpNfceEnvironment environment, Transport transport) {
        if (environment == null || environment.mode() != SefazSpNfceEnvironment.Mode.HOMOLOGATION) {
            throw new IllegalArgumentException("only SEFAZ-SP homologation is supported");
        }
        this.endpoint = environment.endpoint(SefazSpNfceEnvironment.Service.CONSULT_PROTOCOL);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public FiscalConsultationResponse consult(String accessKey) {
        if (accessKey == null || !accessKey.matches("[0-9]{44}")
                || !accessKey.startsWith("35") || !"65".equals(accessKey.substring(20, 22))) {
            throw new IllegalArgumentException("valid SP NFC-e access key is required");
        }
        String request = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Envelope xmlns="http://www.w3.org/2003/05/soap-envelope"><Body>
                <nfeDadosMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4">
                <consSitNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
                <tpAmb>2</tpAmb><xServ>CONSULTAR</xServ><chNFe>%s</chNFe>
                </consSitNFe></nfeDadosMsg></Body></Envelope>
                """.formatted(accessKey);
        try {
            Transport.Reply reply = transport.send(endpoint, ACTION, request);
            if (reply == null || reply.status() != 200 || reply.body() == null || reply.body().isBlank()
                    || reply.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new FiscalConsultationUnavailableException();
            }
            return new FiscalConsultationResponse(reply.body());
        } catch (FiscalConsultationUnavailableException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FiscalConsultationUnavailableException();
        } catch (Exception ex) {
            throw new FiscalConsultationUnavailableException();
        }
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
