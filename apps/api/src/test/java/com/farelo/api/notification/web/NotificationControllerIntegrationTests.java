package com.farelo.api.notification.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.notification.Notification;
import com.farelo.api.notification.NotificationRepository;
import com.farelo.api.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/notifications} (FARELO-110),
 * against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Clears the {@code notification} table itself in {@code @BeforeEach}
 * for a deterministic starting point on every test, including a literal
 * empty-list assertion — same rationale already documented on {@code
 * PrintJobControllerIntegrationTests}/{@code CategoryControllerIntegrationTests}
 * for wiping a table before asserting: safe here specifically because
 * {@code notification} is a brand-new table with no FK from any other
 * entity, and because test classes in this suite run sequentially, not
 * concurrently.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void clearNotificationsTable() {
        notificationRepository.deleteAll();
    }

    @Test
    void returnsEmptyListWhenNoNotificationsExist() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsAllNotificationsOrderedByCreatedAtAscWhenNoStatusFilterIsGiven() throws Exception {
        Notification pending = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999999", "Pedido pronto"));
        // Distinct, increasing createdAt for a deterministic order assertion —
        // same pattern used elsewhere in this suite (e.g.
        // PrintJobControllerIntegrationTests' ordering test).
        Thread.sleep(10);
        Notification sent = notificationRepository.saveAndFlush(
                new Notification(NotificationType.STOCK_LOW, "5511988888888", "Estoque baixo: Leite"));
        sent.markSent();
        notificationRepository.saveAndFlush(sent);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(pending.getId().toString()))
                .andExpect(jsonPath("$[0].type").value("ORDER_READY"))
                .andExpect(jsonPath("$[0].recipient").value("5511999999999"))
                .andExpect(jsonPath("$[0].content").value("Pedido pronto"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].id").value(sent.getId().toString()))
                .andExpect(jsonPath("$[1].status").value("SENT"));
    }

    @Test
    void filtersNotificationsByStatus() throws Exception {
        Notification pending = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999999", "Pedido pronto"));
        Notification failed = notificationRepository.saveAndFlush(
                new Notification(NotificationType.PRINT_FAILED, "5511977777777", "Falha na impressão"));
        failed.markFailed();
        notificationRepository.saveAndFlush(failed);

        mockMvc.perform(get("/api/v1/notifications").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pending.getId().toString()));

        mockMvc.perform(get("/api/v1/notifications").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(failed.getId().toString()));
    }

}
