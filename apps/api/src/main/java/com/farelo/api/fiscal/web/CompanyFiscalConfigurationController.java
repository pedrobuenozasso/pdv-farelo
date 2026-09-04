package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.CompanyFiscalConfiguration;
import com.farelo.api.fiscal.CompanyFiscalConfigurationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/company-fiscal-configuration} (FARELO-155) — singular
 * path, no {@code {id}}, and only {@code GET}/{@code PUT}: deliberately NOT
 * the generic {@code POST}/{@code GET} list/{@code GET {id}}/{@code PUT
 * {id}} shape {@code FiscalProfileController} uses.
 *
 * <p><b>Why no {@code POST} / no collection shape.</b> {@code
 * FiscalProfile} is a many-rows lookup value — many fiscal profiles can
 * exist, a {@code Product} picks one by id, so a collection endpoint
 * ({@code POST} to create another, {@code GET} to list them all, {@code
 * GET {id}}/{@code PUT {id}} to address one of many) is the right shape.
 * {@code CompanyFiscalConfiguration} is the opposite: the business has
 * exactly one fiscal identity, full stop — there is no "list them" or
 * "which one" question to ask. A generic {@code POST}/{@code GET} list/
 * {@code PUT {id}} shape would let a client {@code POST} a second company
 * config with nothing stopping it (the table itself has no uniqueness
 * constraint either — see {@link CompanyFiscalConfiguration}'s javadoc),
 * silently breaking the one invariant this entity exists to guarantee.
 * {@code GET}/{@code PUT} on a singular, id-less path instead models "there
 * is exactly one" at the API surface: {@code GET} always means "the"
 * configuration, {@code PUT} always means "set/replace the" configuration
 * — there is no id a client could get wrong or use to create a second row.
 *
 * <p>{@code PUT} is create-or-replace (see {@link
 * CompanyFiscalConfigurationService#save}): the very first call creates the
 * one row, every call after that replaces it. This is also why there's no
 * separate {@code POST} to "create" the row first — {@code PUT} already
 * covers both "not configured yet" and "already configured" in one
 * operation, so a client integrating this endpoint (e.g. a future Admin
 * "Configurações" screen, prompt mestre seção 21) never needs to branch on
 * whether the row exists yet.
 *
 * <p>{@code GET} returns {@code 404}/{@code
 * COMPANY_FISCAL_CONFIGURATION_NOT_FOUND} (via {@link
 * com.farelo.api.fiscal.CompanyFiscalConfigurationNotFoundException}) when
 * {@code PUT} has never been called — no migration seeds a default row,
 * since this codebase has no real business's CNPJ/razão social to seed it
 * with, and inventing placeholder fiscal identity data would be worse than
 * a 404.
 *
 * <p><b>Singleton-shape decision — option (a), not DB-enforced.</b> The
 * "at most one row" invariant is kept by this controller/service (no
 * {@code POST}, {@code PUT} always finds-and-replaces the one existing row
 * via {@code findFirstByOrderByCreatedAtAsc()}) rather than by a database
 * constraint (fixed/known id, unique index on a dummy column). No other
 * domain in this codebase has a "singleton settings row" precedent to
 * follow either way (searched for one; none exists). Chosen because this
 * codebase's consistent style is "don't over-engineer for a constraint
 * with no real enforcement need yet" (AGENTS.md: "não... criar abstrações
 * prematuras") — nothing in this table is ever addressed by a raw id from
 * outside the API (unlike {@code fiscal_profile}, which {@code
 * product.fiscal_profile_id} FKs into), so a stray extra row inserted
 * outside the API surface (which nothing here prevents, same as every
 * other unenforced invariant in this codebase, e.g. {@code Category.name}
 * not being unique) would simply never be reachable through {@code GET}/
 * {@code PUT} (both always resolve to the earliest-created row) — low
 * blast radius. If real operational discipline ever proves insufficient,
 * enforcing this at the DB level is a small, isolated follow-up.
 *
 * <p><b>No {@code @RequireRole} anywhere on this controller</b> — same
 * precedent as {@code FiscalProfileController}/{@code IngredientController}
 * (both still entirely unprotected as of their own first tickets):
 * applying RBAC to a brand-new domain/entity's first endpoints is
 * consistently deferred to a dedicated future ticket in this codebase (see
 * {@code docs/domain-model.md}'s {@code fiscal} section, FARELO-150
 * subsection, for the fuller precedent list) — never bundled into the
 * ticket that first creates the write surface, unless that ticket's own
 * text is itself about protecting something (it isn't, here). If/when
 * company fiscal configuration needs to be Admin-only, that's a follow-up
 * ticket to decide, not this one.
 */
@RestController
@RequestMapping("/api/v1/company-fiscal-configuration")
public class CompanyFiscalConfigurationController {

    private final CompanyFiscalConfigurationService companyFiscalConfigurationService;

    public CompanyFiscalConfigurationController(CompanyFiscalConfigurationService companyFiscalConfigurationService) {
        this.companyFiscalConfigurationService = companyFiscalConfigurationService;
    }

    @GetMapping
    public CompanyFiscalConfigurationResponse get() {
        return CompanyFiscalConfigurationResponse.from(companyFiscalConfigurationService.get());
    }

    @PutMapping
    public CompanyFiscalConfigurationResponse put(@Valid @RequestBody CompanyFiscalConfigurationRequest request) {
        CompanyFiscalConfiguration companyFiscalConfiguration = companyFiscalConfigurationService.save(
                request.cnpj(), request.legalName(), request.tradeName(),
                request.stateRegistration(), request.address());
        return CompanyFiscalConfigurationResponse.from(companyFiscalConfiguration);
    }

}
