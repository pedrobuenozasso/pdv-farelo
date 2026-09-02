package com.farelo.api.printing;

import com.farelo.api.ordering.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    // Derived query (Spring Data), same minimal style as the rest of this
    // repository. Added for FARELO-072: lets a caller (today, only tests)
    // confirm which PrintJob(s) exist for a given order, without needing a
    // new custom @Query — order is @ManyToOne on PrintJob, so a plain
    // equality WHERE clause is all this needs.
    List<PrintJob> findByOrder(Order order);

}
