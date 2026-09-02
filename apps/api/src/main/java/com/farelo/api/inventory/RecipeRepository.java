package com.farelo.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    // The natural key of this entity (see Recipe's javadoc): the one query
    // a future recipe/consumption consumer will actually need, even though
    // nothing outside tests calls it yet in this ticket's scope.
    Optional<Recipe> findByProductIdAndActiveTrue(UUID productId);

    // JOIN FETCH r.product — same reasoning as PrintJobRepository's
    // findByIdWithOrder/findByStatusOrderByCreatedAtAsc (the FARELO-055
    // lesson, originally documented on OrderRepository): open-in-view is
    // false (application.yml), and RecipeResponse#from reads
    // recipe.getProduct().getName() in the controller, after this method's
    // own (short) transaction has already closed — without eagerly
    // fetching product here, that's an uninitialized lazy proxy needing a
    // live session, i.e. a guaranteed LazyInitializationException. Backs
    // RecipeService#getById (used by getById/deactivate).
    @Query("SELECT r FROM Recipe r JOIN FETCH r.product WHERE r.id = :id")
    Optional<Recipe> findByIdWithProduct(@Param("id") UUID id);

    // Same JOIN FETCH reasoning as findByIdWithProduct above. Backs
    // RecipeService#listAll.
    @Query("SELECT r FROM Recipe r JOIN FETCH r.product ORDER BY r.createdAt ASC")
    List<Recipe> findAllWithProductOrderByCreatedAtAsc();

}
