package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FiscalProviderContractTests {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String ACCESS_KEY = "12345678901234567890123456789012345678901234";

    @Test
    void requestRejectsMissingIdentityAndEmptyPayload() {
        assertThrows(NullPointerException.class,
                () -> new FiscalAuthorizationRequest(null, ACCESS_KEY, "<signed/>"));
        assertThrows(IllegalArgumentException.class,
                () -> new FiscalAuthorizationRequest(DOCUMENT_ID, "123", "<signed/>"));
        assertThrows(IllegalArgumentException.class,
                () -> new FiscalAuthorizationRequest(DOCUMENT_ID, ACCESS_KEY, " "));
    }

    @Test
    void responsePreservesRawAuthorizerReplyForLaterValidation() {
        String reply = "<retEnviNFe><cStat>100</cStat></retEnviNFe>";
        FiscalAuthorizationResponse response = new FiscalAuthorizationResponse(reply);
        assertEquals(reply, response.rawXml());
        assertFalse(response.toString().contains(reply));
        assertThrows(IllegalArgumentException.class, () -> new FiscalAuthorizationResponse(" "));
    }

    @Test
    void diagnosticStringDoesNotRevealFiscalXmlOrAccessKey() {
        FiscalAuthorizationRequest request =
                new FiscalAuthorizationRequest(DOCUMENT_ID, ACCESS_KEY, "<signed>customer</signed>");
        assertFalse(request.toString().contains(ACCESS_KEY));
        assertFalse(request.toString().contains("customer"));
    }
}
