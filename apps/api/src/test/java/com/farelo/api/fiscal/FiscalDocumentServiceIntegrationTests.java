package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandNotFoundException;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link FiscalDocumentService#transition(UUID, FiscalDocumentStatus)}
 * and {@link FiscalDocumentService#transition(int, UUID,
 * FiscalDocumentStatus)} (FARELO-157), against a real PostgreSQL instance
 * (Testcontainers) — the state-machine validation named on {@link
 * FiscalDocumentStatus}'s own javadoc as a separate ticket from FARELO-156.
 *
 * <p>Every legal edge in {@code FiscalDocumentService#LEGAL_TRANSITIONS}
 * (see that class's javadoc for the full table and reasoning) is exercised
 * at least once below, plus a representative sample of illegal moves
 * (skipping a required step, moving backward, and acting on both terminal
 * states) — not an exhaustive sweep of all 30 ordered pairs, per the
 * ticket's own testing requirements, but enough to prove the table
 * genuinely restricts something rather than accepting everything.
 *
 * <p>Uses dedicated seeded command numbers 26-27 — free numbers per the
 * registry maintained across this suite (see {@code
 * FiscalDocumentRepositoryIntegrationTests}: 21, 23-25; {@code
 * FiscalDocumentControllerIntegrationTests}: 35-37). {@code COMMAND_NUMBER}
 * (26) is shared by every test method here: none of them assert an exact
 * row *count* for that command, only look up/act on a specific document by
 * its own id, so accumulation across methods (JUnit gives no ordering
 * guarantee between test methods in a class) never affects an assertion.
 * {@code OTHER_COMMAND_NUMBER} (27) is dedicated to the comanda-ownership
 * mismatch test below, so it never accumulates fiscal documents that would
 * matter to any assertion elsewhere.
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * FiscalDocumentRepositoryIntegrationTests}: every test scopes its
 * assertions to rows it creates itself, so leftover rows from other test
 * classes sharing the singleton Postgres container (see {@link
 * AbstractIntegrationTest}) never affect an assertion here.
 */
@SpringBootTest
class FiscalDocumentServiceIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 26;
    private static final int OTHER_COMMAND_NUMBER = 27;

    // Guaranteed to never collide with a real seeded command — seeded
    // commands only run 1-100 (V6__seed_commands_1_to_100.sql).
    private static final int UNKNOWN_COMMAND_NUMBER = 999_999;

    @Autowired
    private FiscalDocumentService fiscalDocumentService;

    @Autowired
    private FiscalDocumentRepository fiscalDocumentRepository;

    @Autowired
    private CommandRepository commandRepository;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    private FiscalDocument newDocument(int commandNumber) {
        return fiscalDocumentRepository.saveAndFlush(new FiscalDocument(command(commandNumber)));
    }

    private FiscalDocument newDocument(int commandNumber, FiscalDocumentStatus status) {
        FiscalDocument document = new FiscalDocument(command(commandNumber));
        document.setStatus(status);
        return fiscalDocumentRepository.saveAndFlush(document);
    }

    // --- Legal edges: every one of them succeeds -----------------------

    @Test
    void pendingTransitionsToProcessing() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.PROCESSING);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.PROCESSING);
        assertThat(fiscalDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(FiscalDocumentStatus.PROCESSING);
    }

    @Test
    void pendingTransitionsToContingency() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.CONTINGENCY);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.CONTINGENCY);
    }

    @Test
    void processingTransitionsToAuthorized() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.PROCESSING);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.AUTHORIZED);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.AUTHORIZED);
    }

    @Test
    void processingTransitionsToRejected() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.PROCESSING);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.REJECTED);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.REJECTED);
    }

    @Test
    void processingTransitionsToContingency() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.PROCESSING);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.CONTINGENCY);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.CONTINGENCY);
    }

    @Test
    void contingencyTransitionsToAuthorized() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.CONTINGENCY);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.AUTHORIZED);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.AUTHORIZED);
    }

    @Test
    void contingencyTransitionsToRejected() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.CONTINGENCY);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.REJECTED);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.REJECTED);
    }

    @Test
    void authorizedTransitionsToCancelled() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.AUTHORIZED);

        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.CANCELLED);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.CANCELLED);
    }

    @Test
    void fullHappyPathPendingToProcessingToAuthorized() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.PROCESSING);
        FiscalDocument result = fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.AUTHORIZED);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.AUTHORIZED);
    }

    // --- Illegal edges: a representative sample is rejected ------------

    @Test
    void pendingCannotSkipStraightToAuthorized() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.AUTHORIZED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class)
                .hasMessageContaining(document.getId().toString())
                .hasMessageContaining("PENDING")
                .hasMessageContaining("AUTHORIZED");
    }

    @Test
    void pendingCannotTransitionDirectlyToRejected() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.REJECTED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void pendingCannotTransitionDirectlyToCancelled() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.CANCELLED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void processingCannotRevertToPending() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.PROCESSING);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.PENDING))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void processingCannotJumpToCancelledWithoutAuthorization() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.PROCESSING);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.CANCELLED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void contingencyCannotMoveToProcessing() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.CONTINGENCY);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.PROCESSING))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void authorizedCannotRevertToRejected() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.AUTHORIZED);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.REJECTED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void rejectedIsTerminal() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.REJECTED);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.PENDING))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void cancelledIsTerminal() {
        FiscalDocument document = newDocument(COMMAND_NUMBER, FiscalDocumentStatus.CANCELLED);

        assertThatThrownBy(() -> fiscalDocumentService.transition(document.getId(), FiscalDocumentStatus.AUTHORIZED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

    @Test
    void transitionThrowsNotFoundForUnknownId() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> fiscalDocumentService.transition(unknownId, FiscalDocumentStatus.PROCESSING))
                .isInstanceOf(FiscalDocumentNotFoundException.class);
    }

    // --- Comanda-scoped overload ----------------------------------------

    @Test
    void commandScopedTransitionSucceedsWhenDocumentBelongsToCommand() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        FiscalDocument result =
                fiscalDocumentService.transition(COMMAND_NUMBER, document.getId(), FiscalDocumentStatus.PROCESSING);

        assertThat(result.getStatus()).isEqualTo(FiscalDocumentStatus.PROCESSING);
    }

    @Test
    void commandScopedTransitionThrowsNotFoundWhenDocumentBelongsToDifferentCommand() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        assertThatThrownBy(() -> fiscalDocumentService.transition(
                        OTHER_COMMAND_NUMBER, document.getId(), FiscalDocumentStatus.PROCESSING))
                .isInstanceOf(FiscalDocumentNotFoundException.class);

        // The document itself is untouched — the mismatch is rejected before
        // any mutation happens.
        assertThat(fiscalDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(FiscalDocumentStatus.PENDING);
    }

    @Test
    void commandScopedTransitionThrowsCommandNotFoundForUnknownCommandNumber() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        assertThatThrownBy(() -> fiscalDocumentService.transition(
                        UNKNOWN_COMMAND_NUMBER, document.getId(), FiscalDocumentStatus.PROCESSING))
                .isInstanceOf(CommandNotFoundException.class);
    }

    @Test
    void commandScopedTransitionAppliesSameLegalityRulesAsCoreMethod() {
        FiscalDocument document = newDocument(COMMAND_NUMBER);

        assertThatThrownBy(() -> fiscalDocumentService.transition(
                        COMMAND_NUMBER, document.getId(), FiscalDocumentStatus.AUTHORIZED))
                .isInstanceOf(FiscalDocumentInvalidTransitionException.class);
    }

}
