package com.farelo.api.command;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandRepository extends JpaRepository<Command, UUID> {

    Optional<Command> findByNumber(int number);

    List<Command> findAllByOrderByNumberAsc();

    // FARELO-315: distinct table count, not command count — nothing today
    // stops two different commands from referencing the same table (no
    // uniqueness constraint added for that), so this must not
    // double-count a table linked to more than one in-use comanda. See
    // CommandService#countOccupiedTables's javadoc for which statuses
    // count as "occupied" and why this lives here rather than in the
    // `table` domain.
    @Query("SELECT COUNT(DISTINCT c.table.id) FROM Command c WHERE c.table IS NOT NULL AND c.status IN :statuses")
    long countDistinctOccupiedTables(@Param("statuses") Collection<CommandStatus> statuses);

}
