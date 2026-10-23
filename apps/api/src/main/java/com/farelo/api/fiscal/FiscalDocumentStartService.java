package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates only an unused fiscal placeholder for one closed visit. This does
 * not reserve a number, load an A1, or send anything to SEFAZ. Locking the
 * command row makes two callers of this service converge on the same ID.
 */
@Service
public class FiscalDocumentStartService {
    private final CommandRepository commands;
    private final FiscalDocumentRepository documents;

    public FiscalDocumentStartService(CommandRepository commands,
                                      FiscalDocumentRepository documents) {
        this.commands = commands;
        this.documents = documents;
    }

    @Transactional
    public FiscalDocument start(UUID commandId) {
        Objects.requireNonNull(commandId, "commandId");
        Command command = commands.findByIdForUpdate(commandId)
                .orElseThrow(() -> new IllegalArgumentException("command was not found"));
        if (command.getStatus() != CommandStatus.CLOSED) {
            throw new IllegalStateException("fiscal document requires a closed command");
        }
        List<FiscalDocument> existing = documents.findByCommandOrderByCreatedAtAsc(command);
        if (existing.size() == 1 && unused(existing.get(0))) {
            return existing.get(0);
        }
        if (!existing.isEmpty()) {
            // Reissue after rejection/cancellation requires a separately
            // reviewed policy; never make a second document implicitly.
            throw new IllegalStateException("command already has a fiscal document");
        }
        return documents.saveAndFlush(new FiscalDocument(command));
    }

    private static boolean unused(FiscalDocument document) {
        return document.getStatus() == FiscalDocumentStatus.PENDING
                && document.getDocumentNumber() == null
                && document.getSeries() == null
                && document.getIssuerCnpj() == null
                && document.getFiscalEnvironment() == null
                && document.getAccessKey() == null
                && document.getXmlContent() == null
                && document.getProtocolNumber() == null;
    }
}
