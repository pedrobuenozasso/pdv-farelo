package com.farelo.api.cashier;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FARELO-306: create/list {@link Shift} templates. Backs {@code POST}/
 * {@code GET /api/v1/shifts}.
 */
@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    /**
     * Creates a {@link Shift} template. 409 {@link
     * ShiftNameAlreadyExistsException} if {@code name} is already taken —
     * the pre-check here mirrors {@code UserService#create}'s reasoning:
     * the real source of truth under concurrency is {@code uk_shift_name}
     * at the DB level, this check just turns the common case into a clean
     * 409 instead of a generic constraint-violation error.
     */
    @Transactional
    public Shift create(String name, String openingAccountName, String closingAccountName) {
        shiftRepository.findByName(name).ifPresent(existing -> {
            throw new ShiftNameAlreadyExistsException(name);
        });
        return shiftRepository.save(new Shift(name, openingAccountName, closingAccountName));
    }

    // No active-only filter — same YAGNI reasoning as CategoryService/
    // IngredientService's own listAll(): no consumer needs it yet. Ordered
    // by name, same convention as UserService#listAll.
    public List<Shift> listAll() {
        return shiftRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Reused by CashSessionService#open (FARELO-307) to resolve the shift a
    // new cash session opens against.
    public Shift getById(UUID id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException(id));
    }

}
