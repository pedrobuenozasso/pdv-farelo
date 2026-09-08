package com.farelo.api.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // FARELO-313: case-insensitive substring match on name OR phone, so a
    // single search box (the PDV's future "selecionar cliente" use case)
    // can find a customer either way without staff needing to know which
    // field they're typing into. Ordered by name for a stable, predictable
    // listing — same tiebreaker convention as
    // CategoryService#listAll.
    @Query("""
            SELECT c FROM Customer c
            WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR c.phone LIKE CONCAT('%', :query, '%')
            ORDER BY c.name ASC
            """)
    List<Customer> search(@Param("query") String query);

}
