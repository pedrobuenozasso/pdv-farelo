package com.farelo.api.customer;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // FARELO-313: name is required (enforced by CustomerRequest's
    // @NotBlank, not repeated here); phone is optional and always stored
    // normalized, same convention as CommandService#normalizePhone.
    @Transactional
    public Customer create(String name, String phone) {
        Customer customer = new Customer(name.trim(), normalizePhone(phone));
        return customerRepository.save(customer);
    }

    // FARELO-313's "busca" half: a blank/absent query returns every
    // customer (ordered by name) instead of an empty result — the natural
    // "show me the list" behavior for a search box with nothing typed yet,
    // avoiding a separate listAll() endpoint for the same data.
    public List<Customer> search(String query) {
        if (query == null || query.isBlank()) {
            return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        }
        return customerRepository.search(query.trim());
    }

    // FARELO-313: deliberately the same heuristic as
    // CommandService#normalizePhone (strip everything but digits, prepend
    // Brazil's country code for a bare 10/11-digit local number) — not
    // extracted into a shared utility. This is the second occurrence of
    // this exact logic in the codebase; a shared `PhoneNumbers` helper
    // would be a reasonable follow-up if a third consumer appears, but
    // extracting one now for two call sites, in two otherwise-unrelated
    // domains, would be exactly the "abstração prematura" AGENTS.md warns
    // against — there's no shared package either already depends on that
    // a helper could live in without inventing a new cross-domain
    // dependency neither needs for anything else.
    static String normalizePhone(String rawPhone) {
        if (rawPhone == null) {
            return null;
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (!digits.startsWith("55") && (digits.length() == 10 || digits.length() == 11)) {
            digits = "55" + digits;
        }
        return digits;
    }

}
