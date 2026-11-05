-- V74: perfis fiscais de NFC-e aprovados pelo contador (FISC-02), 05/11/2026.
--
-- Origem: leitura de NFC-e autorizadas no Sischef (docs/fisc10-matriz-fiscal-autorizada-20261105.md)
-- e plano de carga com a decisão do contador (docs/fisc02-plano-carga-continuidade-20261105.md).
-- Contador: "usar o mais vantajoso pelas regras atuais" -> bebidas de ICMS-ST inequívoca em SP
-- saem com CFOP 5405 / CSOSN 500; os demais itens mantêm o que o Sischef emite hoje
-- (CFOP 5102 / CSOSN 102). PIS/COFINS CST 99 e origem 0 em todos.
--
-- Segurança desta carga:
--   * Perfis têm UUID determinístico e só são inseridos se ao menos um dos produtos mapeados
--     existir; num banco sem esses produtos (ex.: testes) a migração não insere nada.
--   * Produtos são casados por UUID (estáveis entre ambientes, mesmo padrão da V72) e só recebem
--     perfil se ainda não tiverem um (não sobrescreve escolha manual feita no Admin).
--   * Itens sem NCM (6) continuam sem perfil, e a montagem fiscal continua recusando-os.
--   * GTIN e unidade fiscal NÃO são preenchidos aqui (regra do projeto: nunca inferir SEM GTIN).
--   * Nenhuma nota é emitida por esta migração.

INSERT INTO fiscal_profile (id, name, description, ncm, cfop, csosn, icms_origin, cest, pis_cst, cofins_cst)
SELECT prof.id, prof.name,
       'Carga FISC-02 (V74). Decisão do contador em 05/11/2026; ver docs/fisc02-plano-carga-continuidade-20261105.md.',
       prof.ncm, prof.cfop, prof.csosn, '0', prof.cest, '99', '99'
FROM (VALUES
    ('b3a1297d-d408-5903-bbfe-00a2890dd17e'::uuid, '5102/102 · NCM 18063110 · CEST 1700200', '18063110', '5102', '102', '1700200'),
    ('6672732d-caa4-564d-be44-7c5a9379cb4d'::uuid, '5102/102 · NCM 19052090 · CEST 1705100', '19052090', '5102', '102', '1705100'),
    ('f8c98909-6c95-52c9-bea9-26e3e0dac4b1'::uuid, '5102/102 · NCM 19052090', '19052090', '5102', '102', NULL),
    ('e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid, '5102/102 · NCM 21069090', '21069090', '5102', '102', NULL),
    ('faee648d-f641-5598-80ad-f234ad69e3e9'::uuid, '5102/102 · NCM 19059090 · CEST 1703100', '19059090', '5102', '102', '1703100'),
    ('c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid, '5102/102 · NCM 21011200', '21011200', '5102', '102', NULL),
    ('6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid, '5102/102 · NCM 21011200 · CEST 1710900', '21011200', '5102', '102', '1710900'),
    ('0835831f-bf9f-560a-8209-88d649237d83'::uuid, 'ST 5405/500 · NCM 22030000', '22030000', '5405', '500', NULL),
    ('963df34a-9ecd-53ba-8104-d2a064068a28'::uuid, '5102/102 · NCM 19059010 · CEST 1706000', '19059010', '5102', '102', '1706000'),
    ('e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid, '5102/102 · NCM 21069090 · CEST 1709700', '21069090', '5102', '102', '1709700'),
    ('87382e5c-a650-5d9d-9c4f-782079573a00'::uuid, 'ST 5405/500 · NCM 22029900 · CEST 0302200', '22029900', '5405', '500', '0302200'),
    ('3aeccc4b-6bd1-5e08-b1ad-749203677bd8'::uuid, '5102/102 · NCM 19021100 · CEST 1704900', '19021100', '5102', '102', '1704900'),
    ('162b0dc8-25a3-5c72-9bef-a14decd58afb'::uuid, '5102/102 · NCM 17049090 · CEST 1700800', '17049090', '5102', '102', '1700800'),
    ('9ca0d50f-8146-5366-97f8-8974ee87d542'::uuid, '5102/102 · NCM 20081100', '20081100', '5102', '102', NULL),
    ('228113a7-d3fd-5fd4-8ab3-f66b3762c26a'::uuid, '5102/102 · NCM 19059090', '19059090', '5102', '102', NULL),
    ('6bbcf7d5-af19-5f3f-978a-a17aeecf1e1b'::uuid, 'ST 5405/500 · NCM 22021000 · CEST 1711000', '22021000', '5405', '500', '1711000'),
    ('0f4d2682-c8fd-54e7-bc1f-cadcb744c64f'::uuid, '5102/102 · NCM 21069010 · CEST 1708701', '21069010', '5102', '102', '1708701'),
    ('e3287780-07a4-59f0-81a9-b9428eb00094'::uuid, '5102/102 · NCM 21069010 · CEST 0301200', '21069010', '5102', '102', '0301200'),
    ('c0141992-ea35-5e97-9110-b1099dccd5c2'::uuid, 'ST 5405/500 · NCM 22011000 · CEST 0300100', '22011000', '5405', '500', '0300100'),
    ('8f9a3e3a-2c60-5514-996a-452fbb1ca945'::uuid, '5102/102 · NCM 21069030 · CEST 0301300', '21069030', '5102', '102', '0301300')
) AS prof(id, name, ncm, cfop, csosn, cest)
WHERE EXISTS (
    SELECT 1
    FROM (VALUES
    ('081bb239-a622-582d-9599-d761c944ccb5'::uuid, 'b3a1297d-d408-5903-bbfe-00a2890dd17e'::uuid),  -- Achocolatado Quente
    ('ae53aa0f-41bb-5c5c-824d-f26d8877246a'::uuid, '6672732d-caa4-564d-be44-7c5a9379cb4d'::uuid),  -- Bolo Gelado Surpresa de Uva
    ('3681b43a-617a-5488-8f62-6a9fc49d1519'::uuid, 'f8c98909-6c95-52c9-bea9-26e3e0dac4b1'::uuid),  -- Bolo Gelado de Abacaxi com Coco
    ('46630787-4f0b-57a8-baea-98891e4ca341'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Bolo de Laranja
    ('c938b94d-8726-5c88-875e-09ac02c78e98'::uuid, 'faee648d-f641-5598-80ad-f234ad69e3e9'::uuid),  -- Brownie com cobertura de Doce de Leite
    ('e4818522-1a7d-501c-b8fa-e1126a7631ef'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café Coado Curto
    ('b2080a7d-7b7a-5838-badd-41439b1f9f7f'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café Coado Longo
    ('5cb68da4-139e-52a5-bb68-01c5b67d453e'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café Gelado Artesanal
    ('a9627e8c-d0f5-582c-ae73-31152498cecd'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Café com Leite (Pingado)
    ('5983dcaa-0071-54cb-b6cb-671bee58b4df'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café expresso
    ('9475749c-23ec-53d1-9ac2-b24242583a52'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Café expresso longo(carioca)
    ('da4c9494-9ca7-54af-b73f-b79607205a32'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Cappuccino Com Canela
    ('60337855-5bba-5a2f-90f5-416426fddfdd'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Cappuccino Com Chocolate
    ('29ac81a1-31f2-5026-9c3b-77f5f78dfdc4'::uuid, '0835831f-bf9f-560a-8209-88d649237d83'::uuid),  -- Cerveja
    ('3828dd71-1349-5f25-b325-1bbad1b0611b'::uuid, '963df34a-9ecd-53ba-8104-d2a064068a28'::uuid),  -- Cestinha de mini pães de queijo
    ('fdc353bf-08cc-5820-aed8-ce81e86c62e0'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Cookie
    ('77d3c582-8a14-5ff6-8f40-397c3008d896'::uuid, '87382e5c-a650-5d9d-9c4f-782079573a00'::uuid),  -- Energético Red Bull Tradicional
    ('40ed71b3-63a9-5a04-86cb-17b85dba5f9e'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Leite Quente
    ('0c8d94be-7aff-5129-9d5d-b6146e239312'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Misto Quente
    ('21907629-3162-569a-8f7d-d2f00cf52643'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Mocaccino
    ('7f91ab97-a3b7-5cb0-8c29-813e8cd709da'::uuid, '6672732d-caa4-564d-be44-7c5a9379cb4d'::uuid),  -- Muffin Vegano de Chocolate (contém açúcar, sem glúten)
    ('431c5a39-6f90-5504-97b1-eea8a3d03343'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Muffin de Frutas Zero Açucar
    ('bbb80f14-b792-5c85-a1ab-557c0fdf524a'::uuid, '3aeccc4b-6bd1-5e08-b1ad-749203677bd8'::uuid),  -- Omelete Recheado com Presunto e Queijo
    ('5ad6b950-7adf-5ed1-8c07-bf8cb97842df'::uuid, '162b0dc8-25a3-5c72-9bef-a14decd58afb'::uuid),  -- Pirulito 7 Belo
    ('7f799bca-6b7c-576d-8e23-f1f7445af308'::uuid, '9ca0d50f-8146-5366-97f8-8974ee87d542'::uuid),  -- Porção de Amendoim
    ('a262c51f-44ea-51ab-afb3-2a9345210083'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Porção de Croquete de Carne com Gorgonzola
    ('b7ae5dba-3c9f-5156-ab40-a1c775f39041'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Porção de Croquete de Mandioca com Carne Seca
    ('1faf7c9b-41c7-5797-abaf-d3d1ccdbbc31'::uuid, '228113a7-d3fd-5fd4-8ab3-f66b3762c26a'::uuid),  -- Porção de Mini Kibe Com Geleia de Hortelã
    ('dd471fcb-8dff-5b80-aa53-118457759c82'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Pão na Chapa Com Manteiga
    ('94bb21ae-b532-5ee2-b35d-f6608b5cf986'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Pão na Chapa com Ovos Mexidos e Bacon
    ('0abc040a-a7bc-531e-8ad2-a8804aa08b30'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Queijo Quente com Molho Pesto
    ('4e21d1e6-7b21-5b4c-93f5-69cf088ed37b'::uuid, '6bbcf7d5-af19-5f3f-978a-a17aeecf1e1b'::uuid),  -- Refrigerantes
    ('a042fe5f-53c8-5b9b-90e0-635762008084'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE AÇAI
    ('7b861d0d-8a31-51cf-8726-e33573b38ad3'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE FRUTAS VERMELHAS
    ('3c35fe48-e388-56ea-b6dd-b273b4e665f9'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE MANGA
    ('4a069840-0106-5ab3-9b63-cc21908bf4c7'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE MARACUJA CREMOSO
    ('d15b002f-37c7-5148-95e1-b6303c74e20d'::uuid, '0f4d2682-c8fd-54e7-bc1f-cadcb744c64f'::uuid),  -- SODA ITALIANA DE BLUEBERRY
    ('430ec91f-d6de-5611-8959-09518009e9f7'::uuid, 'e3287780-07a4-59f0-81a9-b9428eb00094'::uuid),  -- SODA ITALIANA DE PINK LEMONADE
    ('39d19c87-732e-5d0c-b6cb-d6bfab124f2e'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Suco de Laranja INTEGRAL
    ('9678e9de-3236-506e-9205-0fb808288904'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- VITAMINA DE ABACATE
    ('df27193b-f967-50d1-abdc-12a25b25e755'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- VITAMINA DE BANANA
    ('82bf78b9-b60b-54c7-b930-cff5074d4fc6'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- VITAMINA DE MAMÃO
    ('db60b053-ae7d-5ea5-a60a-4b2e63641109'::uuid, '87382e5c-a650-5d9d-9c4f-782079573a00'::uuid),  -- Água Tônica Antárctica
    ('f90d8ba6-24c7-53a0-87e1-72a619eccd5e'::uuid, 'c0141992-ea35-5e97-9110-b1099dccd5c2'::uuid),  -- Água com gás
    ('857a533c-497f-5373-bbeb-f9722be7b550'::uuid, '8f9a3e3a-2c60-5514-996a-452fbb1ca945'::uuid),  -- Água de Coco
    ('36eff81f-5d56-56a8-a1cb-1d4e56962af4'::uuid, 'c0141992-ea35-5e97-9110-b1099dccd5c2'::uuid)  -- Água mineral
    ) AS m(product_id, profile_id)
    JOIN product p ON p.id = m.product_id
    WHERE m.profile_id = prof.id
)
ON CONFLICT (id) DO NOTHING;

UPDATE product p
SET fiscal_profile_id = m.profile_id, updated_at = now()
FROM (VALUES
    ('081bb239-a622-582d-9599-d761c944ccb5'::uuid, 'b3a1297d-d408-5903-bbfe-00a2890dd17e'::uuid),  -- Achocolatado Quente
    ('ae53aa0f-41bb-5c5c-824d-f26d8877246a'::uuid, '6672732d-caa4-564d-be44-7c5a9379cb4d'::uuid),  -- Bolo Gelado Surpresa de Uva
    ('3681b43a-617a-5488-8f62-6a9fc49d1519'::uuid, 'f8c98909-6c95-52c9-bea9-26e3e0dac4b1'::uuid),  -- Bolo Gelado de Abacaxi com Coco
    ('46630787-4f0b-57a8-baea-98891e4ca341'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Bolo de Laranja
    ('c938b94d-8726-5c88-875e-09ac02c78e98'::uuid, 'faee648d-f641-5598-80ad-f234ad69e3e9'::uuid),  -- Brownie com cobertura de Doce de Leite
    ('e4818522-1a7d-501c-b8fa-e1126a7631ef'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café Coado Curto
    ('b2080a7d-7b7a-5838-badd-41439b1f9f7f'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café Coado Longo
    ('5cb68da4-139e-52a5-bb68-01c5b67d453e'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café Gelado Artesanal
    ('a9627e8c-d0f5-582c-ae73-31152498cecd'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Café com Leite (Pingado)
    ('5983dcaa-0071-54cb-b6cb-671bee58b4df'::uuid, 'c18f1ce9-a7e6-5a9f-b4bd-ffac86693d7a'::uuid),  -- Café expresso
    ('9475749c-23ec-53d1-9ac2-b24242583a52'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Café expresso longo(carioca)
    ('da4c9494-9ca7-54af-b73f-b79607205a32'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Cappuccino Com Canela
    ('60337855-5bba-5a2f-90f5-416426fddfdd'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Cappuccino Com Chocolate
    ('29ac81a1-31f2-5026-9c3b-77f5f78dfdc4'::uuid, '0835831f-bf9f-560a-8209-88d649237d83'::uuid),  -- Cerveja
    ('3828dd71-1349-5f25-b325-1bbad1b0611b'::uuid, '963df34a-9ecd-53ba-8104-d2a064068a28'::uuid),  -- Cestinha de mini pães de queijo
    ('fdc353bf-08cc-5820-aed8-ce81e86c62e0'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Cookie
    ('77d3c582-8a14-5ff6-8f40-397c3008d896'::uuid, '87382e5c-a650-5d9d-9c4f-782079573a00'::uuid),  -- Energético Red Bull Tradicional
    ('40ed71b3-63a9-5a04-86cb-17b85dba5f9e'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Leite Quente
    ('0c8d94be-7aff-5129-9d5d-b6146e239312'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Misto Quente
    ('21907629-3162-569a-8f7d-d2f00cf52643'::uuid, '6c4864b0-8d40-5591-8ca8-9286850aa42e'::uuid),  -- Mocaccino
    ('7f91ab97-a3b7-5cb0-8c29-813e8cd709da'::uuid, '6672732d-caa4-564d-be44-7c5a9379cb4d'::uuid),  -- Muffin Vegano de Chocolate (contém açúcar, sem glúten)
    ('431c5a39-6f90-5504-97b1-eea8a3d03343'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Muffin de Frutas Zero Açucar
    ('bbb80f14-b792-5c85-a1ab-557c0fdf524a'::uuid, '3aeccc4b-6bd1-5e08-b1ad-749203677bd8'::uuid),  -- Omelete Recheado com Presunto e Queijo
    ('5ad6b950-7adf-5ed1-8c07-bf8cb97842df'::uuid, '162b0dc8-25a3-5c72-9bef-a14decd58afb'::uuid),  -- Pirulito 7 Belo
    ('7f799bca-6b7c-576d-8e23-f1f7445af308'::uuid, '9ca0d50f-8146-5366-97f8-8974ee87d542'::uuid),  -- Porção de Amendoim
    ('a262c51f-44ea-51ab-afb3-2a9345210083'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Porção de Croquete de Carne com Gorgonzola
    ('b7ae5dba-3c9f-5156-ab40-a1c775f39041'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Porção de Croquete de Mandioca com Carne Seca
    ('1faf7c9b-41c7-5797-abaf-d3d1ccdbbc31'::uuid, '228113a7-d3fd-5fd4-8ab3-f66b3762c26a'::uuid),  -- Porção de Mini Kibe Com Geleia de Hortelã
    ('dd471fcb-8dff-5b80-aa53-118457759c82'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- Pão na Chapa Com Manteiga
    ('94bb21ae-b532-5ee2-b35d-f6608b5cf986'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Pão na Chapa com Ovos Mexidos e Bacon
    ('0abc040a-a7bc-531e-8ad2-a8804aa08b30'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Queijo Quente com Molho Pesto
    ('4e21d1e6-7b21-5b4c-93f5-69cf088ed37b'::uuid, '6bbcf7d5-af19-5f3f-978a-a17aeecf1e1b'::uuid),  -- Refrigerantes
    ('a042fe5f-53c8-5b9b-90e0-635762008084'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE AÇAI
    ('7b861d0d-8a31-51cf-8726-e33573b38ad3'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE FRUTAS VERMELHAS
    ('3c35fe48-e388-56ea-b6dd-b273b4e665f9'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE MANGA
    ('4a069840-0106-5ab3-9b63-cc21908bf4c7'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- SMOOTHIE DE MARACUJA CREMOSO
    ('d15b002f-37c7-5148-95e1-b6303c74e20d'::uuid, '0f4d2682-c8fd-54e7-bc1f-cadcb744c64f'::uuid),  -- SODA ITALIANA DE BLUEBERRY
    ('430ec91f-d6de-5611-8959-09518009e9f7'::uuid, 'e3287780-07a4-59f0-81a9-b9428eb00094'::uuid),  -- SODA ITALIANA DE PINK LEMONADE
    ('39d19c87-732e-5d0c-b6cb-d6bfab124f2e'::uuid, 'e99871fb-1d38-5ef0-be55-db9c85e3ec3d'::uuid),  -- Suco de Laranja INTEGRAL
    ('9678e9de-3236-506e-9205-0fb808288904'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- VITAMINA DE ABACATE
    ('df27193b-f967-50d1-abdc-12a25b25e755'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- VITAMINA DE BANANA
    ('82bf78b9-b60b-54c7-b930-cff5074d4fc6'::uuid, 'e13f81b8-d435-5294-b9ee-df9c9ed74b87'::uuid),  -- VITAMINA DE MAMÃO
    ('db60b053-ae7d-5ea5-a60a-4b2e63641109'::uuid, '87382e5c-a650-5d9d-9c4f-782079573a00'::uuid),  -- Água Tônica Antárctica
    ('f90d8ba6-24c7-53a0-87e1-72a619eccd5e'::uuid, 'c0141992-ea35-5e97-9110-b1099dccd5c2'::uuid),  -- Água com gás
    ('857a533c-497f-5373-bbeb-f9722be7b550'::uuid, '8f9a3e3a-2c60-5514-996a-452fbb1ca945'::uuid),  -- Água de Coco
    ('36eff81f-5d56-56a8-a1cb-1d4e56962af4'::uuid, 'c0141992-ea35-5e97-9110-b1099dccd5c2'::uuid)  -- Água mineral
) AS m(product_id, profile_id)
WHERE p.id = m.product_id
  AND p.fiscal_profile_id IS NULL
  AND EXISTS (SELECT 1 FROM fiscal_profile f WHERE f.id = m.profile_id);
