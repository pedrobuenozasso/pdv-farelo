package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static com.farelo.api.fiscal.SefazSpNfceEnvironment.Mode.HOMOLOGATION;
import static com.farelo.api.fiscal.SefazSpNfceEnvironment.Mode.PRODUCTION;
import static com.farelo.api.fiscal.SefazSpNfceEnvironment.Service.AUTHORIZE;
import static com.farelo.api.fiscal.SefazSpNfceEnvironment.Service.CONSULT_PROTOCOL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SefazSpNfceEnvironmentTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SefazSpNfceEnvironment.class);

    @Test
    void springDefaultsToHomologationAndRejectsAccidentalProduction() {
        contextRunner.run(context -> {
            assertEquals(HOMOLOGATION, context.getBean(SefazSpNfceEnvironment.class).mode());
        });
        contextRunner.withPropertyValues("fiscal.nfce.mode=PRODUCTION")
                .run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues(
                        "fiscal.nfce.mode=PRODUCTION", "fiscal.nfce.allow-production=true")
                .run(context -> assertEquals(PRODUCTION,
                        context.getBean(SefazSpNfceEnvironment.class).mode()));
    }

    @Test
    void homologationUsesOnlyTheSefazSpTestHostAndTpAmbTwo() {
        SefazSpNfceEnvironment environment = new SefazSpNfceEnvironment(HOMOLOGATION, false);

        assertEquals(HOMOLOGATION, environment.mode());
        assertEquals("2", environment.tpAmb());
        assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeAutorizacao4.asmx",
                environment.endpoint(AUTHORIZE).toString());
        assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeConsultaProtocolo4.asmx",
                environment.endpoint(CONSULT_PROTOCOL).toString());
    }

    @Test
    void productionCannotBeSelectedWithoutSeparateExplicitSwitch() {
        assertThrows(IllegalStateException.class,
                () -> new SefazSpNfceEnvironment(PRODUCTION, false));
        SefazSpNfceEnvironment environment = new SefazSpNfceEnvironment(PRODUCTION, true);
        assertEquals("1", environment.tpAmb());
        assertEquals("https://nfce.fazenda.sp.gov.br/ws/NFeAutorizacao4.asmx",
                environment.endpoint(AUTHORIZE).toString());
    }

    @Test
    void nullModeAndServiceFailClosed() {
        assertThrows(NullPointerException.class, () -> new SefazSpNfceEnvironment(null, false));
        SefazSpNfceEnvironment environment = new SefazSpNfceEnvironment(HOMOLOGATION, false);
        assertThrows(NullPointerException.class, () -> environment.endpoint(null));
    }
}
