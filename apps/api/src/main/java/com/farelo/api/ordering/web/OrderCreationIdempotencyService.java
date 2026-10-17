package com.farelo.api.ordering.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.NewOrderOption;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderWithItems;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.InvalidTokenException;
import com.farelo.api.security.auth.JwtTokenService;
import com.farelo.api.security.rbac.InsufficientRoleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Serializes retries at the database unique key, including across API instances. */
@Service
public class OrderCreationIdempotencyService {
    private final OrderService orderService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final CommandService commandService;
    private final JwtTokenService jwtTokenService;

    public OrderCreationIdempotencyService(OrderService orderService, JdbcTemplate jdbcTemplate,
                                           EntityManager entityManager, ObjectMapper objectMapper,
                                           CommandService commandService, JwtTokenService jwtTokenService) {
        this.orderService = orderService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.commandService = commandService;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String keyHeader,
                                String qrTokenHeader, String authorizationHeader) {
        Command visit = commandService.lockCurrentForOrder(request.commandNumber());
        authorize(visit, qrTokenHeader, authorizationHeader);
        return execute("COMANDA", request.commandNumber(), request.items(), request.customerName(),
                request.customerPhone(), request.note(), request.printOnConfirmOrDefault(),
                visit.getId(), keyHeader, () -> {
            List<NewOrderItem> items = request.items().stream()
                    .map(this::toNewOrderItem)
                    .toList();
            return orderService.create(request.commandNumber(), items, request.customerName(),
                    request.customerPhone(), request.note(), request.printOnConfirmOrDefault());
        });
    }

    @Transactional
    public OrderResponse createBalcao(CreateBalcaoOrderRequest request, String keyHeader) {
        return execute("BALCAO", null, request.items(), request.customerName(),
                request.customerPhone(), request.note(), request.printOnConfirmOrDefault(),
                null, keyHeader, () -> {
            List<NewOrderItem> items = request.items().stream()
                    .map(this::toNewOrderItem)
                    .toList();
            return orderService.createBalcao(items, request.customerName(), request.customerPhone(),
                    request.note(), request.printOnConfirmOrDefault());
        });
    }

    private OrderResponse execute(String type, Integer commandNumber, List<OrderItemRequest> items,
                                  String customerName, String customerPhone, String note,
                                  boolean printOnConfirm, UUID visitId,
                                  String keyHeader, OrderCreation create) {
        // Legacy clients remain accepted. New QR/PDV clients must generate one
        // UUID per intent and reuse it for retries to receive this guarantee.
        if (keyHeader == null) {
            return OrderResponse.from(create.run());
        }

        UUID key = parseKey(keyHeader);
        String fingerprint = fingerprint(type, commandNumber, visitId, items, customerName, customerPhone,
                note, printOnConfirm);
        int claimed = jdbcTemplate.update("""
                INSERT INTO order_creation_idempotency (idempotency_key, request_fingerprint)
                VALUES (?, ?) ON CONFLICT (idempotency_key) DO NOTHING
                """, key, fingerprint);

        if (claimed == 0) {
            return jdbcTemplate.queryForObject("""
                    SELECT request_fingerprint, response_json
                    FROM order_creation_idempotency WHERE idempotency_key = ?
                    """, (rs, rowNum) -> {
                        if (!fingerprint.equals(rs.getString("request_fingerprint"))) {
                            throw OrderIdempotencyException.reused();
                        }
                        return readResponse(rs.getString("response_json"));
                    }, key);
        }

        OrderResponse response = OrderResponse.from(create.run());
        // The JDBC FK update must see Hibernate's pending inserts in this transaction.
        entityManager.flush();
        jdbcTemplate.update("""
                UPDATE order_creation_idempotency SET order_id = ?, response_json = ?
                WHERE idempotency_key = ?
                """, response.id(), serialize(response), key);
        return response;
    }

    private NewOrderItem toNewOrderItem(OrderItemRequest item) {
        return new NewOrderItem(item.productId(), item.quantity(),
                item.options() == null ? List.of() : item.options().stream()
                        .map(option -> new NewOrderOption(option.stepId(), option.optionId(),
                                option.quantity()))
                        .toList());
    }

    private UUID parseKey(String header) {
        try {
            UUID parsed = UUID.fromString(header);
            if (!parsed.toString().equalsIgnoreCase(header)) {
                throw OrderIdempotencyException.invalidKey();
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw OrderIdempotencyException.invalidKey();
        }
    }

    private String fingerprint(String type, Integer commandNumber, UUID visitId, List<OrderItemRequest> items,
                               String customerName, String customerPhone, String note, boolean printOnConfirm) {
        byte[] payload = serialize(new FingerprintPayload(type, commandNumber, visitId, items, customerName,
                customerPhone, note, printOnConfirm)).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar pedido", ex);
        }
    }

    private OrderResponse readResponse(String json) {
        if (json == null) {
            throw new IllegalStateException("Chave idempotente concluída sem resposta");
        }
        try {
            return objectMapper.readValue(json, OrderResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao ler resposta idempotente", ex);
        }
    }

    @FunctionalInterface
    private interface OrderCreation {
        OrderWithItems run();
    }

    private record FingerprintPayload(String type, Integer commandNumber, UUID visitId,
                                      List<OrderItemRequest> items,
                                      String customerName, String customerPhone, String note,
                                      boolean printOnConfirm) {
    }

    private void authorize(Command visit, String qrTokenHeader, String authorizationHeader) {
        if (authorizationHeader != null) {
            if (!authorizationHeader.startsWith("Bearer ") || authorizationHeader.length() <= 7) {
                throw new InvalidTokenException("Invalid staff token", null);
            }
            UserRole role = jwtTokenService.parse(authorizationHeader.substring(7)).role();
            if (role != UserRole.ADMIN && role != UserRole.MANAGER
                    && role != UserRole.CASHIER && role != UserRole.ATTENDANT) {
                throw new InsufficientRoleException(role);
            }
            return;
        }
        try {
            if (qrTokenHeader != null && visit.getQrToken().equals(UUID.fromString(qrTokenHeader))) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // Missing, malformed and incorrect QR proofs get the same outcome.
        }
        throw new InvalidTokenException("Valid command QR token required", null);
    }
}
