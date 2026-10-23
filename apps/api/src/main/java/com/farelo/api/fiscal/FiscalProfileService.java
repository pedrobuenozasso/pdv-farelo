package com.farelo.api.fiscal;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FiscalProfileService {

    private final FiscalProfileRepository fiscalProfileRepository;

    public FiscalProfileService(FiscalProfileRepository fiscalProfileRepository) {
        this.fiscalProfileRepository = fiscalProfileRepository;
    }

    public FiscalProfile create(String name, String description, String ncm, String cfop, String cst, String csosn,
                                String icmsOrigin, String cest, String pisCst, String cofinsCst) {
        FiscalProfile fiscalProfile = new FiscalProfile(name);
        fiscalProfile.setDescription(description);
        fiscalProfile.setNcm(ncm);
        fiscalProfile.setCfop(cfop);
        fiscalProfile.setCst(cst);
        fiscalProfile.setCsosn(csosn);
        fiscalProfile.setIcmsOrigin(icmsOrigin);
        fiscalProfile.setCest(cest);
        fiscalProfile.setPisCst(pisCst);
        fiscalProfile.setCofinsCst(cofinsCst);
        return fiscalProfileRepository.save(fiscalProfile);
    }

    // No active-only filter yet, same as CategoryService/IngredientService's
    // listAll() — YAGNI, no consumer (Admin) asking for it yet.
    public List<FiscalProfile> listAll() {
        return fiscalProfileRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Reused by update() below.
    public FiscalProfile getById(UUID id) {
        return fiscalProfileRepository.findById(id)
                .orElseThrow(() -> new FiscalProfileNotFoundException(id));
    }

    @Transactional
    public FiscalProfile update(
            UUID id, String name, String description, boolean active, String ncm, String cfop, String cst,
            String csosn, String icmsOrigin, String cest, String pisCst, String cofinsCst) {
        FiscalProfile fiscalProfile = getById(id);

        fiscalProfile.setName(name);
        fiscalProfile.setDescription(description);
        fiscalProfile.setActive(active);
        fiscalProfile.setNcm(ncm);
        fiscalProfile.setCfop(cfop);
        fiscalProfile.setCst(cst);
        fiscalProfile.setCsosn(csosn);
        // Older Admin clients do not yet send these NFC-e fields. Keep any
        // approved values on an omitted input instead of silently clearing them.
        if (icmsOrigin != null) fiscalProfile.setIcmsOrigin(icmsOrigin);
        if (cest != null) fiscalProfile.setCest(cest);
        if (pisCst != null) fiscalProfile.setPisCst(pisCst);
        if (cofinsCst != null) fiscalProfile.setCofinsCst(cofinsCst);

        return fiscalProfileRepository.save(fiscalProfile);
    }

}
