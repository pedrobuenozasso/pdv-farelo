package com.farelo.api.payment.web;

import com.farelo.api.command.CommandService;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentService;
import com.farelo.api.security.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Persists a payment's response atomically with its ledger and audit rows. */
@Service
public class PaymentRecordIdempotencyService {
    private final PaymentService paymentService;
    private final CommandService commandService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public PaymentRecordIdempotencyService(PaymentService paymentService, CommandService commandService,
                                           JdbcTemplate jdbcTemplate,
                                           EntityManager entityManager, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.commandService = commandService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentRecordResponse record(int commandNumber, PaymentRequest request, UUID actorId,
                                        UserRole role, String keyHeader) {
        // Existing clients are accepted, but must opt into retry protection.
        if (keyHeader == null) {
            return create(commandNumber, request, actorId, role);
        }

        UUID key = parseKey(keyHeader);
        String fingerprint = fingerprint(commandNumber, request, actorId);
        int claimed = jdbcTemplate.update("""
                INSERT INTO payment_record_idempotency (idempotency_key, request_fingerprint)
                VALUES (?, ?) ON CONFLICT (idempotency_key) DO NOTHING
                """, key, fingerprint);

        if (claimed == 0) {
            return jdbcTemplate.queryForObject("""
                    SELECT i.request_fingerprint, i.response_json, p.command_id
                    FROM payment_record_idempotency i
                    LEFT JOIN payment p ON p.id = i.payment_id
                    WHERE i.idempotency_key = ?
                    """, (rs, rowNum) -> {
                        if (!fingerprint.equals(rs.getString("request_fingerprint"))) {
                            throw PaymentIdempotencyException.reused();
                        }
                        UUID currentVisitId = commandService.findByNumber(commandNumber).getId();
                        UUID recordedVisitId = rs.getObject("command_id", UUID.class);
                        if (!currentVisitId.equals(recordedVisitId)) {
                            throw PaymentIdempotencyException.reused();
                        }
                        return readResponse(rs.getString("response_json"));
                    }, key);
        }

        PaymentRecordResponse response = create(commandNumber, request, actorId, role);
        entityManager.flush();
        jdbcTemplate.update("""
                UPDATE payment_record_idempotency SET payment_id = ?, response_json = ?
                WHERE idempotency_key = ?
                """, response.id(), serialize(response), key);
        return response;
    }

    private PaymentRecordResponse create(int commandNumber, PaymentRequest request, UUID actorId, UserRole role) {
        Payment payment = paymentService.record(commandNumber, request.amount(), request.method(),
                request.cashSessionId(), actorId, role);
        BigDecimal changeGiven = request.amountReceived() != null
                ? request.amountReceived().subtract(request.amount()) : BigDecimal.ZERO;
        return PaymentRecordResponse.from(payment, changeGiven);
    }

    private UUID parseKey(String header) {
        try {
            UUID parsed = UUID.fromString(header);
            if (!parsed.toString().equalsIgnoreCase(header)) {
                throw PaymentIdempotencyException.invalidKey();
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw PaymentIdempotencyException.invalidKey();
        }
    }

    private String fingerprint(int commandNumber, PaymentRequest request, UUID actorId) {
        FingerprintPayload payload = new FingerprintPayload(commandNumber, actorId, money(request.amount()),
                request.method().name(), money(request.amountReceived()), request.cashSessionId());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(serialize(payload).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros().toPlainString();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar pagamento", ex);
        }
    }

    private PaymentRecordResponse readResponse(String json) {
        if (json == null) {
            throw new IllegalStateException("Chave idempotente concluída sem resposta");
        }
        try {
            return objectMapper.readValue(json, PaymentRecordResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao ler resposta idempotente", ex);
        }
    }

    private record FingerprintPayload(int commandNumber, UUID actorId, String amount, String method,
                                      String amountReceived, UUID cashSessionId) {
    }
}
