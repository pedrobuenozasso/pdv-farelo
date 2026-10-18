package com.farelo.api.fiscal;

import java.net.URI;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FARELO-171: immutable SEFAZ-SP NFC-e endpoint selection. This component
 * contains no HTTP client, certificate, CSC, or transmission capability.
 * Later tickets must use this selection instead of constructing URLs from
 * request data or silently falling back between fiscal environments.
 */
@Component
public final class SefazSpNfceEnvironment {

    private static final String HOMOLOGATION_HOST = "homologacao.nfce.fazenda.sp.gov.br";
    private static final String PRODUCTION_HOST = "nfce.fazenda.sp.gov.br";

    public enum Mode {
        HOMOLOGATION("2", HOMOLOGATION_HOST),
        PRODUCTION("1", PRODUCTION_HOST);

        private final String tpAmb;
        private final String host;

        Mode(String tpAmb, String host) {
            this.tpAmb = tpAmb;
            this.host = host;
        }
    }

    public enum Service {
        AUTHORIZE("NFeAutorizacao4.asmx"),
        AUTHORIZATION_RECEIPT("NFeRetAutorizacao4.asmx"),
        CONSULT_PROTOCOL("NFeConsultaProtocolo4.asmx"),
        SERVICE_STATUS("NFeStatusServico4.asmx"),
        RECEIVE_EVENT("NFeRecepcaoEvento4.asmx"),
        VOID_NUMBER("NFeInutilizacao4.asmx");

        private final String path;

        Service(String path) {
            this.path = path;
        }
    }

    private final Mode mode;

    public SefazSpNfceEnvironment(
            @Value("${fiscal.nfce.mode:HOMOLOGATION}") Mode mode,
            @Value("${fiscal.nfce.allow-production:false}") boolean allowProduction) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (mode == Mode.PRODUCTION && !allowProduction) {
            throw new IllegalStateException(
                    "NFC-e production requires fiscal.nfce.allow-production=true");
        }
    }

    public Mode mode() {
        return mode;
    }

    /** The exact tpAmb value that a later XML builder must use. */
    public String tpAmb() {
        return mode.tpAmb;
    }

    /** A fixed HTTPS endpoint; never accepts an arbitrary host or URL. */
    public URI endpoint(Service service) {
        return URI.create("https://" + mode.host + "/ws/" +
                Objects.requireNonNull(service, "service").path);
    }
}
