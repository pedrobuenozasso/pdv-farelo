package com.farelo.api.ordering.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.catalog.ProductStep;
import com.farelo.api.catalog.ProductStepOption;
import com.farelo.api.catalog.ProductStepOptionRepository;
import com.farelo.api.catalog.ProductStepRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigurableOrderIntegrationTests extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductStepRepository steps;
    @Autowired ProductStepOptionRepository options;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService jwt;

    private String token;

    @BeforeEach
    void setUp() {
        User cashier = users.save(new User("Options cashier", "options-" + UUID.randomUUID() + "@farelo.dev",
                encoder.encode("senha-forte-123"), UserRole.CASHIER));
        token = jwt.issue(cashier).token();
    }

    private Product product(String name, String price) {
        Category category = categories.save(new Category("Options " + UUID.randomUUID()));
        return products.save(new Product(name, new BigDecimal(price), category));
    }

    private ProductStepOption configure(Product parent, Product child, String price) {
        ProductStep step = steps.save(new ProductStep(parent, "Sabor", 1, 1, 0,
                "Mesmo nivel do produto principal", "Unitário"));
        return options.save(new ProductStepOption(step, child, 0, new BigDecimal(price)));
    }

    private String body(Product parent, ProductStepOption option, boolean selected) {
        String choices = selected ? """
                ,"options":[{"stepId":"%s","optionId":"%s","quantity":1}]
                """.formatted(option.getStep().getId(), option.getId()) : "";
        return """
                {"items":[{"productId":"%s","quantity":2%s}]}
                """.formatted(parent.getId(), choices);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body, String key) {
        return post("/api/v1/orders/balcao").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void freezesSelectedOptionAndCalculatesBaseZeroAndPositivePrice() throws Exception {
        Product beer = product("Cerveja", "0.00");
        Product heineken = product("Heineken", "99.00");
        ProductStepOption beerOption = configure(beer, heineken, "15.00");
        String key = UUID.randomUUID().toString();
        String body = body(beer, beerOption, true);

        String first = mvc.perform(request(body, key)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(15.00))
                .andExpect(jsonPath("$.items[0].options[0].optionName").value("Heineken"))
                .andExpect(jsonPath("$.items[0].options[0].unitPrice").value(15.00))
                .andReturn().getResponse().getContentAsString();

        beerOption.setPrice(new BigDecimal("30.00"));
        heineken.setName("Nova Heineken");
        options.save(beerOption);
        products.save(heineken);
        assertThat(mvc.perform(request(body, key)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).isEqualTo(first);

        Product sandwich = product("Misto", "25.00");
        Product glutenFree = product("Pão sem glúten", "20.00");
        ProductStepOption bread = configure(sandwich, glutenFree, "5.00");
        mvc.perform(request(body(sandwich, bread, true), UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(30.00));
    }

    @Test
    void rejectsMissingRequiredChoiceInactiveChoiceAndWrongStep() throws Exception {
        Product cappuccino = product("Cappuccino", "0.00");
        Product flavor = product("Canela", "16.00");
        ProductStepOption choice = configure(cappuccino, flavor, "16.00");
        mvc.perform(request(body(cappuccino, choice, false), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_OPTION_INVALID"));

        flavor.setActive(false);
        products.save(flavor);
        mvc.perform(request(body(cappuccino, choice, true), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_OPTION_INVALID"));

        Product other = product("Other", "1.00");
        Product otherFlavor = product("Other flavor", "1.00");
        ProductStepOption foreign = configure(other, otherFlavor, "1.00");
        mvc.perform(request(body(cappuccino, foreign, true), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_OPTION_INVALID"));
    }

    @Test
    void sameIdempotencyKeyWithDifferentChoiceIsRejected() throws Exception {
        Product beer = product("Beer choices", "0.00");
        Product first = product("First", "1.00");
        Product second = product("Second", "1.00");
        ProductStepOption one = configure(beer, first, "15.00");
        ProductStepOption two = options.save(new ProductStepOption(one.getStep(), second, 1,
                new BigDecimal("16.00")));
        String key = UUID.randomUUID().toString();
        mvc.perform(request(body(beer, one, true), key)).andExpect(status().isCreated());
        mvc.perform(request(body(beer, two, true), key)).andExpect(status().isConflict());
    }
}
