-- Existing sales cannot be backfilled safely from today's mutable profile.
-- New orders copy these fields at creation; null means fiscal review needed.
ALTER TABLE order_item
    ADD COLUMN fiscal_ncm_snapshot VARCHAR(8),
    ADD COLUMN fiscal_cest_snapshot VARCHAR(7),
    ADD COLUMN fiscal_cfop_snapshot VARCHAR(4),
    ADD COLUMN fiscal_cst_snapshot VARCHAR(3),
    ADD COLUMN fiscal_csosn_snapshot VARCHAR(3),
    ADD COLUMN fiscal_icms_origin_snapshot VARCHAR(1),
    ADD COLUMN fiscal_pis_cst_snapshot VARCHAR(2),
    ADD COLUMN fiscal_cofins_cst_snapshot VARCHAR(2);
