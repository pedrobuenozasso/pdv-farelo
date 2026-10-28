-- Historical Sischef NCMs for human review. This table is never read by the NFC-e builder.
-- The original source is immutable; draft_ncm can be corrected in Admin.
CREATE TABLE fiscal_ncm_review (
    product_id UUID PRIMARY KEY REFERENCES product(id) ON DELETE CASCADE,
    source_ncm VARCHAR(8),
    draft_ncm VARCHAR(8),
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fiscal_ncm_review_source CHECK (source_ncm IS NULL OR source_ncm ~ '^[0-9]{8}$'),
    CONSTRAINT ck_fiscal_ncm_review_draft CHECK (draft_ncm IS NULL OR draft_ncm ~ '^[0-9]{8}$'),
    CONSTRAINT ck_fiscal_ncm_review_confirmed CHECK (NOT reviewed OR draft_ncm IS NOT NULL)
);

-- Only matching catalog products are loaded. Existing edits are never replaced.
INSERT INTO fiscal_ncm_review (product_id, source_ncm, draft_ncm)
SELECT p.id, seed.ncm, seed.ncm
FROM (VALUES
    ('b1d7be45-f78c-5401-b681-a5df72dd8846'::uuid, NULL),
    ('94bb21ae-b532-5ee2-b35d-f6608b5cf986'::uuid, '21069090'),
    ('0abc040a-a7bc-531e-8ad2-a8804aa08b30'::uuid, '21069090'),
    ('0c8d94be-7aff-5129-9d5d-b6146e239312'::uuid, '21069090'),
    ('dd471fcb-8dff-5b80-aa53-118457759c82'::uuid, '21069090'),
    ('bbb80f14-b792-5c85-a1ab-557c0fdf524a'::uuid, '19021100'),
    ('3828dd71-1349-5f25-b325-1bbad1b0611b'::uuid, '19059010'),
    ('39d19c87-732e-5d0c-b6cb-d6bfab124f2e'::uuid, '21069090'),
    ('5cb68da4-139e-52a5-bb68-01c5b67d453e'::uuid, '21011200'),
    ('f90d8ba6-24c7-53a0-87e1-72a619eccd5e'::uuid, '22011000'),
    ('36eff81f-5d56-56a8-a1cb-1d4e56962af4'::uuid, '22011000'),
    ('857a533c-497f-5373-bbeb-f9722be7b550'::uuid, '21069030'),
    ('4e21d1e6-7b21-5b4c-93f5-69cf088ed37b'::uuid, '21069090'),
    ('db60b053-ae7d-5ea5-a60a-4b2e63641109'::uuid, '22029900'),
    ('77d3c582-8a14-5ff6-8f40-397c3008d896'::uuid, '22029900'),
    ('d15b002f-37c7-5148-95e1-b6303c74e20d'::uuid, '21069010'),
    ('430ec91f-d6de-5611-8959-09518009e9f7'::uuid, '21069010'),
    ('e4818522-1a7d-501c-b8fa-e1126a7631ef'::uuid, '21069090'),
    ('b2080a7d-7b7a-5838-badd-41439b1f9f7f'::uuid, '21069090'),
    ('5983dcaa-0071-54cb-b6cb-671bee58b4df'::uuid, '21011200'),
    ('9475749c-23ec-53d1-9ac2-b24242583a52'::uuid, '21011200'),
    ('a9627e8c-d0f5-582c-ae73-31152498cecd'::uuid, '21069090'),
    ('081bb239-a622-582d-9599-d761c944ccb5'::uuid, '18063110'),
    ('21907629-3162-569a-8f7d-d2f00cf52643'::uuid, '21011200'),
    ('da4c9494-9ca7-54af-b73f-b79607205a32'::uuid, '21011200'),
    ('60337855-5bba-5a2f-90f5-416426fddfdd'::uuid, '21011200'),
    ('40ed71b3-63a9-5a04-86cb-17b85dba5f9e'::uuid, '21011200'),
    ('46630787-4f0b-57a8-baea-98891e4ca341'::uuid, '21069090'),
    ('3681b43a-617a-5488-8f62-6a9fc49d1519'::uuid, NULL),
    ('c938b94d-8726-5c88-875e-09ac02c78e98'::uuid, '19059090'),
    ('ae53aa0f-41bb-5c5c-824d-f26d8877246a'::uuid, '19052090'),
    ('5ad6b950-7adf-5ed1-8c07-bf8cb97842df'::uuid, '17049090'),
    ('fdc353bf-08cc-5820-aed8-ce81e86c62e0'::uuid, '21069090'),
    ('431c5a39-6f90-5504-97b1-eea8a3d03343'::uuid, NULL),
    ('7f91ab97-a3b7-5cb0-8c29-813e8cd709da'::uuid, '19052090'),
    ('29ac81a1-31f2-5026-9c3b-77f5f78dfdc4'::uuid, '21069090'),
    ('817b6dee-6a21-52e7-8adf-7da191d60261'::uuid, NULL),
    ('bce14cf7-7345-5b3a-a91f-49e349aa961b'::uuid, NULL),
    ('871f317b-946a-5090-a519-65a81e51e720'::uuid, NULL),
    ('7f799bca-6b7c-576d-8e23-f1f7445af308'::uuid, '20081100'),
    ('a262c51f-44ea-51ab-afb3-2a9345210083'::uuid, '21069090'),
    ('8851b6b6-9834-578c-b181-4ed467881875'::uuid, NULL),
    ('b7ae5dba-3c9f-5156-ab40-a1c775f39041'::uuid, '21069090'),
    ('d2ac7a58-b030-52dd-803f-94a8ba960bef'::uuid, NULL),
    ('1faf7c9b-41c7-5797-abaf-d3d1ccdbbc31'::uuid, '19059090'),
    ('a042fe5f-53c8-5b9b-90e0-635762008084'::uuid, '21069090'),
    ('7b861d0d-8a31-51cf-8726-e33573b38ad3'::uuid, '21069090'),
    ('3c35fe48-e388-56ea-b6dd-b273b4e665f9'::uuid, '21069090'),
    ('4a069840-0106-5ab3-9b63-cc21908bf4c7'::uuid, '21069090'),
    ('9678e9de-3236-506e-9205-0fb808288904'::uuid, '21069090'),
    ('df27193b-f967-50d1-abdc-12a25b25e755'::uuid, '21069090'),
    ('82bf78b9-b60b-54c7-b930-cff5074d4fc6'::uuid, '21069090')
) AS seed(product_id, ncm)
JOIN product p ON p.id = seed.product_id
ON CONFLICT (product_id) DO NOTHING;
