# FISC-02 — Rascunho de classificação fiscal dos 52 itens — 03/11/2026

**Atualização de 05/11/2026:** a leitura de 8 NFC-e autorizadas (nº 13925–13932)
mostrou que o Sischef emite **todo** o cardápio com um único perfil —
operação VENDA DE MERCADORIAS, CFOP 5102, CSOSN 102, PIS/COFINS 99 —,
inclusive itens com ST e produção própria. A divisão em Perfil A/B abaixo é,
portanto, uma **melhoria a decidir pelo contador**, não o estado atual. A
opção de continuidade e o NCM efetivamente autorizado por produto estão em
`fisc10-matriz-fiscal-autorizada-20261105.md`.

**RASCUNHO PARA CONFERÊNCIA DA CONTABILIDADE. Não aplicado, não aprovado.**
Nenhum `FiscalProfile` foi criado nem associado a produto a partir deste
documento. A emissão continua bloqueada até a contabilidade validar.

Base: os 52 produtos ativos do Farelo OS + os NCM/CEST/CFOP/CSOSN **candidatos
históricos** extraídos do Sischef (tabela `fiscal_ncm_review`), cruzados com o
modelo fiscal real lido na retaguarda do Sischef em 03/11
(`referencia-sistema-sischef-20261103.md`): o cardápio usa **2 perfis**.

## Os dois perfis (Simples Nacional, saída/venda)

| Perfil | CFOP | CSOSN | PIS | COFINS | Origem | Quando |
| --- | --- | --- | --- | --- | --- | --- |
| **A — padrão** | 5102 | 102 | 99 (0%) | 99 (0%) | 0 | item preparado na casa ou vendido sem ICMS-ST |
| **B — com ST** | 5405 | 500 | 99 (0%) | 99 (0%) | 0 | bebida industrializada revendida com ICMS-ST já retido |

Regra prática proposta: o que a cafeteria **prepara** (cafés, smoothies, sodas
italianas, bolos, salgados, porções) é **Perfil A**. O que ela **compra pronto
e revende** (água, refrigerante, energético, cerveja em garrafa/lata) é
**Perfil B**. A contabilidade decide o caso de cada um; isto é a leitura da
configuração atual, não um parecer.

## Proposta — Perfil B (com ST): bebidas industrializadas revendidas

| Item | NCM staged | CEST staged | Observação |
| --- | --- | --- | --- |
| Água mineral | 22011000 | 0300100 | NCM/CEST coerentes |
| Água com gás | 22011000 | 0300100 | NCM/CEST coerentes |
| Água de Coco | 21069030 | 0301300 | industrializada (Sococo); confirmar NCM |
| Água Tônica Antárctica | 22029900 | 0302200 | coerente |
| Energético Red Bull | 22029900 | 0302200 | coerente |
| **Refrigerantes** | 21069090 ⚠️ | — ⚠️ | **NCM errado** (deveria ser 2202.10.00) e **CEST faltando** |
| **Cerveja** | 21069090 ⚠️ | — ⚠️ | **NCM errado** (cerveja = 2203.00.00); CEST faltando |
| Suco de Laranja INTEGRAL | 21069090 ⚠️ | 1709700 | garrafinha 250mL — se revendido pronto, é ST; se espremido na casa, Perfil A. **Decidir** |

## Proposta — Perfil A (padrão 5102/102): preparados, doces e salgados

Bebidas preparadas: Café expresso, Café expresso longo, Café com Leite
(Pingado), Café Coado Curto, Café Coado Longo, Café Gelado Artesanal,
Achocolatado Quente, Mocaccino, Cappuccino com Canela, Cappuccino com
Chocolate, Leite Quente, Soda Italiana de Blueberry, Soda Italiana de Pink
Lemonade, Smoothie de Açaí, Smoothie de Frutas Vermelhas, Smoothie de Manga,
Smoothie de Maracujá, Vitamina de Abacate, Vitamina de Banana, Vitamina de
Mamão.

Doces: Bolo de Laranja, Bolo Gelado Surpresa de Uva, Brownie com Doce de Leite,
Cookie, Muffin de Frutas Zero Açúcar, Muffin Vegano de Chocolate, Pirulito 7
Belo, bolo de festa Ninho com Morango, Bolo Gelado de Abacaxi com Coco.

Salgados/porções: Cestinha de mini pães de queijo, Misto Quente, Omelete
Recheado, Pão na Chapa com Manteiga, Pão na Chapa com Ovos e Bacon, Queijo
Quente com Molho Pesto, Porção de Amendoim, Porção de Croquete de Carne com
Gorgonzola, Porção de Croquete de Legumes, Porção de Croquete de Mandioca com
Carne Seca, Porção de Mini Croquete com Geleia de Pimenta, Porção de Mini Kibe
com Geleia de Hortelã.

## Pendências que a contabilidade precisa resolver antes de emitir

1. **NCMs errados ou genéricos.** Muitos itens vieram do Sischef com NCM
   `21069090` (preparações alimentícias diversas — catch-all), inclusive onde
   claramente não cabe: **Cerveja** e **Refrigerantes**. O relatório de triagem
   (`conferencia-ncm-cardapio-20261027.md`) já apontava divergência com a TIPI.
   Cada NCM precisa ser confirmado item a item.
2. **8 itens sem NCM nenhum** na extração: bolo de festa Ninho com Morango,
   Bolo Gelado de Abacaxi com Coco, Muffin de Frutas Zero Açúcar, Porção de
   Croquete de Legumes, Porção de Mini Croquete com Geleia de Pimenta, e os 3
   Picolés. Precisam de NCM antes de entrar em nota.
3. **Picolés:** se forem industrializados revendidos, podem ser Perfil B (ST);
   se artesanais da casa, Perfil A. Decidir.
4. **Suco de Laranja Integral:** garrafinha revendida (ST) vs. espremido na casa
   (padrão). Decidir.
5. **CEST faltando** nos itens de Perfil B que o exigem (Refrigerantes,
   Cerveja).
6. **Sodas italianas** vieram com CEST no Sischef, mas se são preparadas na casa
   não deveriam ter CEST/ST — confirmar.

## Próximo passo técnico (após o OK da contabilidade)

Criar 2 `FiscalProfile` no Farelo OS (A e B), gravar NCM/CEST/unidade por
produto e associar cada item ao perfil correto. Só então a montagem fiscal da
comanda deixa de recusar os itens por falta de classificação. Enquanto este
rascunho não for aprovado, nada é aplicado.
