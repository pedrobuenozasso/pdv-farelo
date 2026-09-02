package com.farelo.api.inventory;

import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final ProductService productService;

    public RecipeService(RecipeRepository recipeRepository, ProductService productService) {
        this.recipeRepository = recipeRepository;
        this.productService = productService;
    }

    /**
     * Creates a recipe header for a product. Validates the product exists
     * (reuses {@link ProductService#getById(UUID)}, same cross-domain
     * pattern already used by
     * {@code com.farelo.api.ordering.OrderService} — 404
     * {@code PRODUCT_NOT_FOUND} if it doesn't) and that it doesn't already
     * have an active recipe (409 {@link RecipeAlreadyExistsException} — see
     * {@link Recipe}'s javadoc for why this is checked here *and* backed by
     * a partial unique index at the DB level).
     */
    @Transactional
    public Recipe create(UUID productId) {
        Product product = productService.getById(productId);

        recipeRepository.findByProductIdAndActiveTrue(productId)
                .ifPresent(existing -> {
                    throw new RecipeAlreadyExistsException(productId);
                });

        return recipeRepository.save(new Recipe(product));
    }

    // No active-only filter, same YAGNI reasoning as
    // IngredientService/CategoryService/ProductService's listAll() — no
    // consumer asking for it yet. Uses the JOIN FETCH query (not a plain
    // findAll + Sort) — see RecipeRepository#findAllWithProductOrderByCreatedAtAsc.
    public List<Recipe> listAll() {
        return recipeRepository.findAllWithProductOrderByCreatedAtAsc();
    }

    // Uses the JOIN FETCH query (not plain findById) — see
    // RecipeRepository#findByIdWithProduct.
    public Recipe getById(UUID id) {
        return recipeRepository.findByIdWithProduct(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));
    }

    // Deactivates a recipe (see RecipeController's PATCH endpoint). No
    // "reactivate" counterpart yet — same asymmetry as would be needed
    // for Ingredient/Product if they had a dedicated deactivate endpoint;
    // not requested by this ticket, and reactivating would need to
    // re-check the active-recipe-per-product uniqueness rule again, which
    // is out of scope here.
    @Transactional
    public Recipe deactivate(UUID id) {
        Recipe recipe = getById(id);
        recipe.setActive(false);
        return recipeRepository.save(recipe);
    }

}
