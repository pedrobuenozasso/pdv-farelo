package com.farelo.api.customer.web;

import com.farelo.api.customer.Customer;
import com.farelo.api.customer.CustomerService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * {@code /api/v1/customers} (FARELO-313) — {@code POST} (register a new
 * customer) and {@code GET} (search by name/phone).
 *
 * <p><b>RBAC decision, made explicitly rather than copied from {@code
 * CategoryController}/{@code ProductController}</b>: both endpoints here
 * require {@link UserRole#ADMIN}/{@link UserRole#MANAGER}/
 * {@link UserRole#CASHIER}/{@link UserRole#ATTENDANT} — the same
 * front-of-house role list {@code CommandController#open}/{@code
 * #updateCustomer} use, <b>not</b> {@code CategoryController}'s
 * {@code ADMIN}+{@code MANAGER}-only. Registering/looking up a customer is
 * an operational, front-of-house action any staff member handling a
 * comanda performs routinely (the same nature as editing a comanda's
 * customer info) — it is not back-office catalog authoring (deciding
 * what's on the menu), which is why {@code CategoryController}/{@code
 * ProductController} restrict writes to {@code ADMIN}/{@code MANAGER}
 * alone. {@code KITCHEN} is excluded, same reasoning {@code
 * CommandController} already documents for why kitchen staff have no
 * business opening/editing comandas.
 *
 * <p><b>{@code GET} is protected here, unlike {@code
 * CategoryController#list}/{@code ProductController#list}</b>: those stay
 * deliberately open because the anonymous "Cardápio QR" flow depends on
 * them. Customer records hold personal data (name + phone) with no
 * anonymous/public consumer of any kind, so there is no equivalent reason
 * to leave this one unprotected — the default (require a caller role)
 * applies here.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CustomerRequest request, UriComponentsBuilder uriComponentsBuilder) {
        Customer customer = customerService.create(request.name(), request.phone());

        URI location = uriComponentsBuilder
                .path("/api/v1/customers/{id}")
                .buildAndExpand(customer.getId())
                .toUri();

        return ResponseEntity.created(location).body(CustomerResponse.from(customer));
    }

    // "query" absent/blank lists every customer (see
    // CustomerService#search's javadoc) — the natural default for a search
    // box with nothing typed yet.
    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public List<CustomerResponse> search(@RequestParam(required = false) String query) {
        return customerService.search(query).stream()
                .map(CustomerResponse::from)
                .toList();
    }

}
