package com.farelo.api.table;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FARELO-315. Deliberately has <b>no</b> dependency on {@code
 * com.farelo.api.command} anywhere in this class — the {@code table}
 * domain's core (this service, its repository, its entity) only ever
 * flows one direction into {@code command} ({@code Command} references
 * {@link DiningTable}, and {@code CommandService} depends on this service
 * to resolve a table by number — see that class's javadoc), never the
 * reverse. Computing occupancy needs to read {@code Command} rows (how
 * many tables currently have an active comanda), so that computation lives
 * in {@code CommandService} instead of here — see {@code
 * CommandService#countOccupiedTables}'s javadoc for why, and {@code
 * DiningTableController#occupancy} for how the two numbers get combined
 * without either core domain depending on the other.
 */
@Service
public class DiningTableService {

    private final DiningTableRepository diningTableRepository;

    public DiningTableService(DiningTableRepository diningTableRepository) {
        this.diningTableRepository = diningTableRepository;
    }

    // Read-check-write without a locking strategy, same documented
    // trade-off CommandService#open already accepts for its own
    // AVAILABLE check — a real concern only under near-simultaneous
    // requests creating the same number, out of scope here.
    @Transactional
    public DiningTable create(int number, int capacity) {
        if (diningTableRepository.findByNumber(number).isPresent()) {
            throw new DiningTableNumberAlreadyExistsException(number);
        }
        return diningTableRepository.save(new DiningTable(number, capacity));
    }

    public List<DiningTable> listAll() {
        return diningTableRepository.findAll(Sort.by(Sort.Direction.ASC, "number"));
    }

    public DiningTable findByNumber(int number) {
        return diningTableRepository.findByNumber(number)
                .orElseThrow(() -> new DiningTableNotFoundException(number));
    }

    public long count() {
        return diningTableRepository.count();
    }

}
