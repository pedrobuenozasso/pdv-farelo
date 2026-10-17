package com.farelo.api.security.rbac;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ensures future API handlers cannot silently become public. */
@SpringBootTest
class ControllerAccessPolicyIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiHandlerDeclaresAnAccessPolicy() {
        List<String> unclassified = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.farelo.api.")) {
                return;
            }
            boolean role = handler.hasMethodAnnotation(RequireRole.class)
                    || handler.getBeanType().isAnnotationPresent(RequireRole.class);
            boolean publicEndpoint = handler.hasMethodAnnotation(PublicEndpoint.class)
                    || handler.getBeanType().isAnnotationPresent(PublicEndpoint.class);
            boolean deviceKey = handler.hasMethodAnnotation(RequireDeviceKey.class)
                    || handler.getBeanType().isAnnotationPresent(RequireDeviceKey.class);
            if ((role ? 1 : 0) + (publicEndpoint ? 1 : 0) + (deviceKey ? 1 : 0) != 1) {
                unclassified.add(mapping + " -> " + handler);
            }
        });
        assertThat(unclassified).isEmpty();
    }
}
