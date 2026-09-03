package com.farelo.api.audit.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.audit.AuditLog;
import com.farelo.api.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/audit-logs} (FARELO-125), against
 * a real PostgreSQL instance (Testcontainers). No {@code @RequireRole} —
 * every request here goes unauthenticated on purpose, proving the endpoint
 * stayed unprotected (see {@code AuditLogController}'s javadoc).
 *
 * <p>Clears the {@code audit_log} table itself in {@code @BeforeEach} for a
 * deterministic starting point on every test, including a literal
 * empty-list assertion — same rationale already documented on {@code
 * NotificationControllerIntegrationTests}: safe here specifically because
 * {@code audit_log} is a brand-new table with no FK from any other entity
 * (see {@code V25__create_audit_log_table.sql} — {@code user_id}
 * deliberately carries none), and because test classes in this suite run
 * sequentially, not concurrently.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clearAuditLogTable() {
        auditLogRepository.deleteAll();
    }

    @Test
    void returnsEmptyListWhenNoAuditLogsExist() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsAllAuditLogsOrderedByCreatedAtDescWhenNoFilterIsGiven() throws Exception {
        UUID userId = UUID.randomUUID();
        AuditLog older = auditLogRepository.saveAndFlush(new AuditLog(
                userId, "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":10}", "{\"price\":12}"));
        // Distinct, increasing createdAt for a deterministic order
        // assertion — same pattern used elsewhere in this suite (e.g.
        // NotificationControllerIntegrationTests' ordering test).
        Thread.sleep(10);
        AuditLog newer = auditLogRepository.saveAndFlush(new AuditLog(
                userId, "Ana Souza", "ana@farelo.dev",
                "STOCK_ADJUSTED", "Ingredient", UUID.randomUUID(), null, "{\"qty\":5}"));

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$[0].action").value("STOCK_ADJUSTED"))
                .andExpect(jsonPath("$[0].entityType").value("Ingredient"))
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].userName").value("Ana Souza"))
                .andExpect(jsonPath("$[0].previousValue").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$[1].action").value("PRICE_CHANGED"));
    }

    @Test
    void filtersAuditLogsByEntityTypeAndEntityId() throws Exception {
        UUID entityId = UUID.randomUUID();
        AuditLog match = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", entityId, "{\"price\":10}", "{\"price\":12}"));
        auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":10}", "{\"price\":12}"));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "Product")
                        .param("entityId", entityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(match.getId().toString()));
    }

    @Test
    void filtersAuditLogsByUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        AuditLog match = auditLogRepository.saveAndFlush(new AuditLog(
                userId, "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":10}", "{\"price\":12}"));
        auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Carlos Lima", "carlos@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":10}", "{\"price\":12}"));

        mockMvc.perform(get("/api/v1/audit-logs").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(match.getId().toString()));
    }

}
