package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies V67 and the JPA mapping on real PostgreSQL when Docker is available. */
@SpringBootTest
@Transactional
class FiscalSubmissionAttemptRepositoryIntegrationTests extends AbstractIntegrationTest {
    @Autowired private FiscalDocumentRepository documents;
    @Autowired private FiscalSubmissionAttemptRepository attempts;
    @Autowired private CommandRepository commands;

    @Test
    void persistsOneAttemptAndFindsItByDocumentId() {
        FiscalDocument document = new FiscalDocument(commands.findByNumber(21).orElseThrow());
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey(String.format("%044d", new BigInteger(
                UUID.randomUUID().toString().replace("-", ""), 16)));
        document = documents.saveAndFlush(document);
        FiscalSubmissionAttempt attempt = attempts.saveAndFlush(new FiscalSubmissionAttempt(
                document, document.getAccessKey(), "a".repeat(64)));
        assertThat(attempt.getId()).isNotNull();
        assertThat(attempt.getCreatedAt()).isNotNull();
        assertThat(attempts.findByDocumentId(document.getId()))
                .isPresent().get()
                .extracting(FiscalSubmissionAttempt::getStatus)
                .isEqualTo(FiscalSubmissionAttemptStatus.RESERVED);
        assertThat(attempts.findByIdForUpdate(attempt.getId()))
                .isPresent().get()
                .extracting(FiscalSubmissionAttempt::getAccessKey)
                .isEqualTo(document.getAccessKey());
    }
}
