package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FiscalDocumentStartIntegrationTests extends AbstractIntegrationTest {
    @Autowired private CommandRepository commands;
    @Autowired private FiscalDocumentRepository documents;
    @Autowired private FiscalDocumentStartService starter;
    @Autowired private PlatformTransactionManager manager;

    @Test
    void concurrentStartsReturnOneUnusedDocumentAndRefuseImplicitReissue() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager);
        UUID commandId = tx.execute(status -> {
            var command = new Command(100, ThreadLocalRandom.current().nextInt(100_000, 900_000));
            command.setStatus(CommandStatus.CLOSED);
            return commands.saveAndFlush(command).getId();
        });
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    return starter.start(commandId).getId();
                });
                var second = workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    return starter.start(commandId).getId();
                });
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS))
                        .isEqualTo(second.get(20, TimeUnit.SECONDS));
            }
            assertThat(starter.start(commandId).getStatus()).isEqualTo(FiscalDocumentStatus.PENDING);
            tx.executeWithoutResult(status -> {
                var command = commands.findById(commandId).orElseThrow();
                assertThat(documents.findByCommandOrderByCreatedAtAsc(command)).hasSize(1);
                var document = documents.findByCommandOrderByCreatedAtAsc(command).get(0);
                document.setDocumentNumber(123);
                documents.saveAndFlush(document);
            });
            assertThatThrownBy(() -> starter.start(commandId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already has a fiscal document");
        } finally {
            tx.executeWithoutResult(status -> {
                var command = commands.findById(commandId).orElseThrow();
                documents.deleteAll(documents.findByCommandOrderByCreatedAtAsc(command));
                documents.flush();
                commands.delete(command);
            });
        }
    }
}
