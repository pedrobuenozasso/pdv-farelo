package com.farelo.api.customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic unit test for {@code CustomerService#normalizePhone} — no
 * Spring context, no database, same split {@code CommandServiceTests}
 * already uses between logic tests and full-stack integration tests (see
 * that class's javadoc). {@code create}/{@code search}'s behavior against
 * a real repository is covered by {@code CustomerControllerIntegrationTests}
 * instead.
 */
class CustomerServiceTests {

    @Test
    void normalizePhoneStripsPunctuationAndPrependsCountryCode() {
        assertThat(CustomerService.normalizePhone("(31) 99876-5432")).isEqualTo("5531998765432");
    }

    @Test
    void normalizePhoneKeepsExistingCountryCode() {
        assertThat(CustomerService.normalizePhone("+55 31 99876-5432")).isEqualTo("5531998765432");
    }

    @Test
    void normalizePhoneReturnsNullForBlankInput() {
        assertThat(CustomerService.normalizePhone(null)).isNull();
        assertThat(CustomerService.normalizePhone("")).isNull();
        assertThat(CustomerService.normalizePhone("   ")).isNull();
    }

    @Test
    void normalizePhoneDoesNotGuessForImplausibleLength() {
        assertThat(CustomerService.normalizePhone("12345")).isEqualTo("12345");
    }

}
