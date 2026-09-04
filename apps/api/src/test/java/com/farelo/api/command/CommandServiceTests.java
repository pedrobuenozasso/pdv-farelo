package com.farelo.api.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic unit tests for {@code CommandService}'s FARELO-190/191
 * phone-normalization and write-through rules — no Spring context, no
 * database, since {@link CommandService#normalizePhone} is a plain string
 * transform and {@link CommandService#applyCustomerInfoIfProvided} only
 * mutates the given {@link Command} in memory (no repository call). The
 * status-transition/HTTP-facing behavior of {@code updateCustomer} itself
 * is covered by {@code CommandControllerIntegrationTests} instead, the same
 * split every other service in this codebase uses between logic tests and
 * full-stack integration tests.
 */
class CommandServiceTests {

    private final CommandService commandService = new CommandService(null);

    @Test
    void normalizePhoneStripsPunctuationAndPrependsCountryCode() {
        assertThat(CommandService.normalizePhone("(31) 99876-5432")).isEqualTo("5531998765432");
    }

    @Test
    void normalizePhoneKeepsExistingCountryCode() {
        assertThat(CommandService.normalizePhone("+55 31 99876-5432")).isEqualTo("5531998765432");
    }

    @Test
    void normalizePhoneReturnsNullForBlankInput() {
        assertThat(CommandService.normalizePhone(null)).isNull();
        assertThat(CommandService.normalizePhone("")).isNull();
        assertThat(CommandService.normalizePhone("   ")).isNull();
    }

    // A number that's neither 10 nor 11 digits after stripping punctuation
    // (too short to be a real Brazilian DDD+number) is left as-is rather
    // than guessing — "validação básica" per the ticket, not a full
    // phone-number authority (see CommandService#normalizePhone's javadoc).
    @Test
    void normalizePhoneDoesNotGuessForImplausibleLength() {
        assertThat(CommandService.normalizePhone("12345")).isEqualTo("12345");
    }

    @Test
    void applyCustomerInfoIfProvidedUpdatesCommandWhenNameGiven() {
        Command command = new Command(1);

        commandService.applyCustomerInfoIfProvided(command, "Maria Souza", null);

        assertThat(command.getCustomerName()).isEqualTo("Maria Souza");
        assertThat(command.getCustomerPhone()).isNull();
    }

    @Test
    void applyCustomerInfoIfProvidedUpdatesCommandWhenPhoneGiven() {
        Command command = new Command(1);

        commandService.applyCustomerInfoIfProvided(command, null, "31988887777");

        assertThat(command.getCustomerName()).isNull();
        assertThat(command.getCustomerPhone()).isEqualTo("5531988887777");
    }

    // FARELO-182's PDV manual item entry collects no customer info at all
    // — this must be a no-op, not a blank-out of whatever the comanda's
    // central record already held (see this method's own javadoc).
    @Test
    void applyCustomerInfoIfProvidedDoesNothingWhenBothBlank() {
        Command command = new Command(1);
        command.setCustomerName("Maria Souza");
        command.setCustomerPhone("5531998765432");

        commandService.applyCustomerInfoIfProvided(command, null, "  ");

        assertThat(command.getCustomerName()).isEqualTo("Maria Souza");
        assertThat(command.getCustomerPhone()).isEqualTo("5531998765432");
    }

}
