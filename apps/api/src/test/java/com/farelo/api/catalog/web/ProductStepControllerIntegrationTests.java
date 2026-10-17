package com.farelo.api.catalog.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.catalog.ProductStepOptionRepository;
import com.farelo.api.catalog.ProductStepRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductStepControllerIntegrationTests extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductStepRepository steps;
    @Autowired private ProductStepOptionRepository options;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService tokens;
    @Autowired private PasswordEncoder passwords;

    @AfterEach
    void cleanSteps() {
        options.deleteAllInBatch();
        steps.deleteAllInBatch();
    }

    private Product product(String name, String price) {
        Category category = categories.saveAndFlush(new Category("Bebidas"));
        return products.saveAndFlush(new Product(name, new BigDecimal(price), category));
    }

    private String token(UserRole role) {
        User user = users.save(new User("Operator", "step-" + UUID.randomUUID() + "@farelo.dev",
                passwords.encode("test-password"), role));
        return "Bearer " + tokens.issue(user).token();
    }

    private String path(Product product) {
        return "/api/v1/products/" + product.getId() + "/steps";
    }

    private String body(UUID optionId, int minimum, int maximum, int order, String price) {
        return """
                {"steps":[{"label":"SABOR","minChoices":%d,"maxChoices":%d,
                "displayOrder":%d,"level":"Mesmo nivel do produto principal","chargeType":"Unitário",
                "options":[{"optionProductId":"%s","displayOrder":0,"price":%s}]}]}
                """.formatted(minimum, maximum, order, optionId, price);
    }

    @Test
    void adminCanReplaceAndReadLinkPriceAndInactiveOption() throws Exception {
        Product parent = product("Cappuccino", "0.00");
        Product option = product("Canela", "20.00");
        option.setActive(false);
        products.saveAndFlush(option);
        String admin = token(UserRole.ADMIN);

        mvc.perform(put(path(parent)).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(option.getId(), 1, 10, 0, "16.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].minChoices").value(1))
                .andExpect(jsonPath("$[0].options[0].price").value(16.00))
                .andExpect(jsonPath("$[0].options[0].optionActive").value(false));

        mvc.perform(get(path(parent)).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].options[0].optionName").value("Canela"));

        mvc.perform(put(path(parent)).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(option.getId(), 1, 10, 0, "17.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].options[0].price").value(17.00));
        mvc.perform(get(path(parent)).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].options", hasSize(1)));

        mvc.perform(put(path(parent)).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steps\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsInvalidConfigurationWithoutReplacingExistingSteps() throws Exception {
        Product parent = product("Picolé", "0.00");
        Product option = product("Morango", "12.00");
        String admin = token(UserRole.MANAGER);
        mvc.perform(put(path(parent)).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(option.getId(), 1, 100, 0, "12.00")))
                .andExpect(status().isOk());

        mvc.perform(put(path(parent)).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(option.getId(), 2, 1, 0, "12.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_STEP_INVALID"));
        mvc.perform(put(path(parent)).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), 1, 1, 0, "12.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get(path(parent)).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maxChoices").value(100));
    }

    @Test
    void rejectsDuplicateOrderAndInvalidPrice() throws Exception {
        Product parent = product("Cerveja", "0.00");
        Product option = product("Lata", "15.00");
        String admin = token(UserRole.ADMIN);
        String duplicated = """
                {"steps":[
                  {"label":"A","minChoices":0,"maxChoices":1,"displayOrder":0,
                   "level":"Subitem","chargeType":"Unitário","options":[]},
                  {"label":"B","minChoices":0,"maxChoices":1,"displayOrder":0,
                   "level":"Subitem","chargeType":"Unitário","options":[]}
                ]}
                """;
        mvc.perform(put(path(parent)).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(duplicated))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_STEP_INVALID"));
        String duplicateOption = """
                {"steps":[{"label":"A","minChoices":0,"maxChoices":2,"displayOrder":0,
                "level":"Subitem","chargeType":"Unitário","options":[
                  {"optionProductId":"%s","displayOrder":0,"price":15.00},
                  {"optionProductId":"%s","displayOrder":1,"price":15.00}
                ]}]}
                """.formatted(option.getId(), option.getId());
        mvc.perform(put(path(parent)).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(duplicateOption))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_STEP_INVALID"));
        mvc.perform(put(path(parent)).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(option.getId(), 1, 1, 0, "-0.01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAdminOrManagerForReadAndWrite() throws Exception {
        Product parent = product("Chá", "13.00");
        Product option = product("Camomila", "0.00");
        mvc.perform(get(path(parent))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(parent)).header("Authorization", token(UserRole.CASHIER)))
                .andExpect(status().isForbidden());
        mvc.perform(put(path(parent)).header("Authorization", token(UserRole.CASHIER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(option.getId(), 0, 1, 0, "0.00")))
                .andExpect(status().isForbidden());
    }
}
