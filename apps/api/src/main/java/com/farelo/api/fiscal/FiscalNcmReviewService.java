package com.farelo.api.fiscal;

import com.farelo.api.catalog.ProductService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Stores historical NCMs for human review, independently of emission profiles. */
@Service
public class FiscalNcmReviewService {

    private final JdbcTemplate jdbc;
    private final ProductService products;

    public FiscalNcmReviewService(JdbcTemplate jdbc, ProductService products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    public List<Review> list() {
        return jdbc.query("""
                SELECT product_id, source_ncm, draft_ncm, reviewed, updated_at,
                       source_cest, draft_cest, source_cfop, draft_cfop,
                       source_csosn, draft_csosn
                FROM fiscal_ncm_review ORDER BY product_id
                """, (rs, rowNum) -> map(rs));
    }

    @Transactional
    public Review save(UUID productId, String draftNcm, boolean reviewed) {
        products.getById(productId);
        jdbc.update("""
                INSERT INTO fiscal_ncm_review (product_id, draft_ncm, reviewed)
                VALUES (?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE SET
                    draft_ncm = EXCLUDED.draft_ncm,
                    reviewed = EXCLUDED.reviewed,
                    updated_at = now()
                """, productId, draftNcm, reviewed);
        return jdbc.queryForObject("""
                SELECT product_id, source_ncm, draft_ncm, reviewed, updated_at,
                       source_cest, draft_cest, source_cfop, draft_cfop,
                       source_csosn, draft_csosn
                FROM fiscal_ncm_review WHERE product_id = ?
                """, (rs, rowNum) -> map(rs), productId);
    }

    @Transactional
    public Review saveTaxCodes(UUID productId, String cest, String cfop, String csosn) {
        products.getById(productId);
        jdbc.update("""
                INSERT INTO fiscal_ncm_review (product_id, draft_cest, draft_cfop, draft_csosn)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE SET
                    draft_cest = EXCLUDED.draft_cest,
                    draft_cfop = EXCLUDED.draft_cfop,
                    draft_csosn = EXCLUDED.draft_csosn,
                    updated_at = now()
                """, productId, cest, cfop, csosn);
        return jdbc.queryForObject("""
                SELECT product_id, source_ncm, draft_ncm, reviewed, updated_at,
                       source_cest, draft_cest, source_cfop, draft_cfop,
                       source_csosn, draft_csosn
                FROM fiscal_ncm_review WHERE product_id = ?
                """, (rs, rowNum) -> map(rs), productId);
    }

    private static Review map(ResultSet rs) throws SQLException {
        return new Review(
                rs.getObject("product_id", UUID.class),
                rs.getString("source_ncm"),
                rs.getString("draft_ncm"),
                rs.getBoolean("reviewed"),
                rs.getObject("updated_at", java.time.OffsetDateTime.class),
                rs.getString("source_cest"), rs.getString("draft_cest"),
                rs.getString("source_cfop"), rs.getString("draft_cfop"),
                rs.getString("source_csosn"), rs.getString("draft_csosn"));
    }

    public record Review(UUID productId, String sourceNcm, String draftNcm,
                         boolean reviewed, java.time.OffsetDateTime updatedAt,
                         String sourceCest, String draftCest,
                         String sourceCfop, String draftCfop,
                         String sourceCsosn, String draftCsosn) {}
}
