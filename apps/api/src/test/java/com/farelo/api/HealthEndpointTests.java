package com.farelo.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTests extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReturnsUp() {
        TestRestTemplate restTemplate = new TestRestTemplate();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:%d/actuator/health".formatted(port), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

}
