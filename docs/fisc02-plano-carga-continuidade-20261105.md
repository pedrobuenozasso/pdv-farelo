# FISC-02 — Plano de carga "continuidade" para aprovação do contador (05/11/2026)

**Nada aplicado.** Este plano transforma a evidência do FISC-10
(`fisc10-matriz-fiscal-autorizada-20261105.md`) numa lista pronta para o
contador aprovar ou corrigir. Depois do "de acordo", a carga é mecânica.

**Premissa (continuidade):** o Farelo passa a emitir como o Sischef emite hoje,
o que a SEFAZ vem autorizando — CFOP **5102**, CSOSN **102**, PIS **99**,
COFINS **99**, origem **0** em todos os itens. Só NCM e CEST variam por
produto. No Farelo, cada `FiscalProfile` carrega NCM+CEST+CFOP+CSOSN+PIS+
COFINS+origem, então a continuidade vira **um perfil por combinação NCM/CEST**.

**Fontes do NCM proposto:** "nota autorizada" = lido em NFC-e autorizada
(FISC-10); "provável" = mapeamento por nome sem nota do item exato;
"histórico" = candidato extraído do Sischef em `fiscal_ncm_review`, sem nota
na amostra. CEST vem sempre do histórico.

**Atenção:** onde o NCM proposto substituiu o histórico (Café Coado Curto e
Longo), o CEST histórico pode não corresponder ao novo NCM — o contador deve
confirmar o par NCM/CEST. As melhorias possíveis (5405/500 para ST, 5101 para
produção própria, correção de NCMs) estão no FISC-10 e mudariam este plano.

## Por produto

| Categoria | Produto (Farelo) | NCM proposto | CEST | Fonte | Atenção do contador |
| --- | --- | --- | --- | --- | --- |
| Bebidas Alcoólicas | Cerveja | 21069090 | — | histórico (sem nota na amostra) | NCM incompatível (cerveja = 22030000); sem nota na amostra |
| Bebidas geladas | Café Gelado Artesanal | 21011200 | — | histórico (sem nota na amostra) | — |
| Bebidas geladas | Energético Red Bull Tradicional | 22029900 | 0302200 | histórico (sem nota na amostra) | — |
| Bebidas geladas | Refrigerantes | 21069090 | — | nota autorizada (REFRIGERANTE) | NCM incompatível; COCA-COLA ZERO sai com 22021000 (correto). Corrigir? |
| Bebidas geladas | Suco de Laranja INTEGRAL | 21069090 | 1709700 | nota autorizada (SUCO LARANJA INTEGRAL) | — |
| Bebidas geladas | Água Tônica Antárctica | 22029900 | 0302200 | histórico (sem nota na amostra) | — |
| Bebidas geladas | Água com gás | 22011000 | 0300100 | nota autorizada (ÁGUA COM GÁS) | — |
| Bebidas geladas | Água de Coco | 21069030 | 0301300 | nota autorizada (ÁGUA DE COCO) | — |
| Bebidas geladas | Água mineral | 22011000 | 0300100 | nota autorizada (ÁGUA MINERAL) | — |
| Bebidas quentes | Achocolatado Quente | 18063110 | 1700200 | histórico (sem nota na amostra) | — |
| Bebidas quentes | Café Coado Curto | 21011200 | 1709700 | nota autorizada (CAFE COADO) | histórico 21069090 ≠ autorizado 21011200 |
| Bebidas quentes | Café Coado Longo | 21011200 | 1709700 | nota autorizada (CAFE COADO) | histórico 21069090 ≠ autorizado 21011200 |
| Bebidas quentes | Café com Leite (Pingado) | 21069090 | — | nota autorizada (CAFÉ COM LEITE (PINGADO)) | — |
| Bebidas quentes | Café expresso | 21011200 | — | histórico (sem nota na amostra) | — |
| Bebidas quentes | Café expresso longo(carioca) | 21011200 | 1710900 | nota autorizada (CAFÉ EXPRESSO LONGO) | — |
| Bebidas quentes | Cappuccino Com Canela | 21011200 | 1710900 | nota autorizada (CAPPUCCINO DA CASA) | — |
| Bebidas quentes | Cappuccino Com Chocolate | 21011200 | 1710900 | nota autorizada (CAPPUCCINO DA CASA) | — |
| Bebidas quentes | Leite Quente | 21011200 | 1710900 | histórico (sem nota na amostra) | 21011200 é preparação de café; revisar |
| Bebidas quentes | Mocaccino | 21011200 | 1710900 | histórico (sem nota na amostra) | — |
| Bolo de Festa | bolo de festa Ninho com Morango | — | — | SEM NCM | — |
| Comidinhas Salgadas | Cestinha de mini pães de queijo | 19059010 | 1706000 | nota autorizada (CESTINHA DE MINI PÃES DE QUEIJO) | — |
| Comidinhas Salgadas | Misto Quente | 21069090 | — | nota autorizada (MISTO QUENTE) | — |
| Comidinhas Salgadas | Omelete Recheado com Presunto e Queijo | 19021100 | 1704900 | histórico (sem nota na amostra) | 19021100 é massa alimentícia; revisar |
| Comidinhas Salgadas | Pão na Chapa Com Manteiga | 21069090 | — | nota autorizada (PÃO NA CHAPA (FATIA)) | — |
| Comidinhas Salgadas | Pão na Chapa com Ovos Mexidos e Bacon | 21069090 | 1709700 | nota autorizada (PAO COM OVO E BACON) | — |
| Comidinhas Salgadas | Queijo Quente com Molho Pesto | 21069090 | 1709700 | histórico (sem nota na amostra) | — |
| Doces | Bolo Gelado Surpresa de Uva | 19052090 | 1705100 | nota autorizada (BOLO GELADO SABORES) | — |
| Doces | Bolo Gelado de Abacaxi com Coco | 19052090 | — | provável (BOLO GELADO SABORES) | — |
| Doces | Bolo de Laranja | 21069090 | — | nota autorizada (BOLO DE LARANJA) | diverge dos demais bolos (1905.x) |
| Doces | Brownie com cobertura de Doce de Leite | 19059090 | 1703100 | nota autorizada (BROWNIE COM COBERTURA DE DOCE DE LEITE) | — |
| Doces | Cookie | 21069090 | 1709700 | histórico (sem nota na amostra) | — |
| Doces | Muffin de Frutas Zero Açucar | 21069090 | — | nota autorizada (MUFFIN DE FRUTAS (ZERO)) | diverge do outro muffin autorizado (19059020) |
| Doces | Pirulito 7 Belo | 17049090 | 1700800 | histórico (sem nota na amostra) | — |
| Opções Veganas | Muffin Vegano de Chocolate (contém açúcar, sem glúten) | 19052090 | 1705100 | histórico (sem nota na amostra) | — |
| Para Dividir (tempo de espera 30min) | Porção de Amendoim | 20081100 | — | histórico (sem nota na amostra) | — |
| Para Dividir (tempo de espera 30min) | Porção de Croquete de Carne com Gorgonzola | 21069090 | — | histórico (sem nota na amostra) | — |
| Para Dividir (tempo de espera 30min) | Porção de Croquete de Legumes (Vegetariano) | — | — | SEM NCM | — |
| Para Dividir (tempo de espera 30min) | Porção de Croquete de Mandioca com Carne Seca | 21069090 | — | histórico (sem nota na amostra) | — |
| Para Dividir (tempo de espera 30min) | Porção de Mini Croquete com Geleia de Pimenta | — | — | SEM NCM | — |
| Para Dividir (tempo de espera 30min) | Porção de Mini Kibe Com Geleia de Hortelã | 19059090 | — | histórico (sem nota na amostra) | — |
| Picolés | Picolé de Chocolate | — | — | SEM NCM | — |
| Picolés | Picolé de Limão | — | — | SEM NCM | — |
| Picolés | Picolé de Uva | — | — | SEM NCM | — |
| SMOOTHIES E VITAMINAS | SMOOTHIE DE AÇAI | 21069090 | — | histórico (sem nota na amostra) | — |
| SMOOTHIES E VITAMINAS | SMOOTHIE DE FRUTAS VERMELHAS | 21069090 | — | histórico (sem nota na amostra) | — |
| SMOOTHIES E VITAMINAS | SMOOTHIE DE MANGA | 21069090 | — | histórico (sem nota na amostra) | — |
| SMOOTHIES E VITAMINAS | SMOOTHIE DE MARACUJA CREMOSO | 21069090 | — | histórico (sem nota na amostra) | — |
| SMOOTHIES E VITAMINAS | VITAMINA DE ABACATE | 21069090 | — | histórico (sem nota na amostra) | — |
| SMOOTHIES E VITAMINAS | VITAMINA DE BANANA | 21069090 | — | histórico (sem nota na amostra) | — |
| SMOOTHIES E VITAMINAS | VITAMINA DE MAMÃO | 21069090 | — | histórico (sem nota na amostra) | — |
| SODAS ITALIANAS | SODA ITALIANA DE BLUEBERRY | 21069010 | 1708701 | histórico (sem nota na amostra) | preparada na casa com CEST de ST; revisar CEST |
| SODAS ITALIANAS | SODA ITALIANA DE PINK LEMONADE | 21069010 | 0301200 | histórico (sem nota na amostra) | preparada na casa com CEST de ST; revisar CEST |

## Perfis a criar

Perfis a criar (todos CFOP 5102 · CSOSN 102 · PIS 99 · COFINS 99 · origem 0):

| # | NCM | CEST | Produtos |
| --- | --- | --- | --- |
| 1 | 17049090 | 1700800 | 1 |
| 2 | 18063110 | 1700200 | 1 |
| 3 | 19021100 | 1704900 | 1 |
| 4 | 19052090 | — | 1 |
| 5 | 19052090 | 1705100 | 2 |
| 6 | 19059010 | 1706000 | 1 |
| 7 | 19059090 | — | 1 |
| 8 | 19059090 | 1703100 | 1 |
| 9 | 20081100 | — | 1 |
| 10 | 21011200 | — | 2 |
| 11 | 21011200 | 1709700 | 2 |
| 12 | 21011200 | 1710900 | 5 |
| 13 | 21069010 | 0301200 | 1 |
| 14 | 21069010 | 1708701 | 1 |
| 15 | 21069030 | 0301300 | 1 |
| 16 | 21069090 | — | 16 |
| 17 | 21069090 | 1709700 | 4 |
| 18 | 22011000 | 0300100 | 2 |
| 19 | 22029900 | 0302200 | 2 |

## O que o contador precisa responder

1. **De acordo com a continuidade** (perfil único 5102/102/99/99/0)? Ou adotar
   5405/500 para itens com ST e/ou 5101 para produção própria?
2. NCM dos itens marcados em "Atenção" — em especial Refrigerantes, Cerveja,
   Cafés coados, Omelete, Leite quente e as Sodas italianas (CEST).
3. NCM dos **6 itens sem NCM**: bolo de festa Ninho com Morango, Porção de
   Croquete de Legumes, Porção de Mini Croquete com Geleia de Pimenta e os 3
   Picolés.

## Como aplicar depois do "de acordo"

1. Backup validado do banco (procedimento de `deploy/README.md`).
2. Criar os perfis em `/admin/fiscal-profiles` (ou por script revisado) e
   associar cada produto em `/admin/products`.
3. Conferir em `/admin/fiscal-review` que nenhum item ativo ficou sem perfil.
4. Só então seguir para a homologação (FISC-08).

## Decisão do contador (05/11/2026) e aplicação proposta

O contador orientou **"usar o mais vantajoso pelas regras atuais"**. A vantagem
concreta é tratar como **ICMS retido por ST** as bebidas revendidas cujo imposto
já foi recolhido pelo fornecedor: **CFOP 5405 / CSOSN 500** (PIS/COFINS 99,
origem 0), o que tira esse ICMS do DAS quando a receita é segregada no PGDAS-D.
CFOP 5101 para produção própria não muda imposto no Simples e **não** foi
adotado (continua 5102). Marcar como ST um item **sem** ST retida reduziria o
ICMS devido indevidamente, então só entram itens de ST inequívoca em SP com
classificação coerente:

| Produto (Farelo) | NCM | CEST | Perfil | Origem do dado |
| --- | --- | --- | --- | --- |
| Água mineral | 22011000 | 0300100 | 5405 / 500 | nota autorizada + histórico XML |
| Água com gás | 22011000 | 0300100 | 5405 / 500 | idem |
| Água Tônica Antárctica | 22029900 | 0302200 | 5405 / 500 | histórico XML |
| Energético Red Bull Tradicional | 22029900 | 0302200 | 5405 / 500 | histórico XML |
| Refrigerantes | **22021000** | 1711000 | 5405 / 500 | cadastro Sischef das Cocas; NCM autorizado na nota 13924 (corrige 21069090) |
| Cerveja | **22030000** | **pendente** | 5405 / 500 | NCM de cerveja de malte; o Sischef tem 21069090 em todas as cervejas e nenhum CEST coerente |

**Ficam em continuidade (5102/102) com alerta:** Água de Coco (NCM 21069030
duvidoso para água de coco) e Suco de Laranja Integral — ST incerta, não aplicar
500 sem confirmação.

**Pré-requisitos antes de cadastrar estes perfis:**
1. O gerador de XML precisa emitir o grupo `ICMSSN500` (hoje só emite
   `ICMSSN102`) — implementação em andamento.
2. O CEST da Cerveja e a dupla NCM/CEST de Refrigerantes devem ser
   confirmados; a **homologação (FISC-08) deve incluir uma nota com Cerveja e
   uma com Refrigerante** para a SEFAZ validar a combinação.

**Observação do cardápio (CAT-01):** no Sischef, Refrigerante e Cerveja são
produtos com passos (o cliente escolhe a marca, cada marca com NCM próprio). No
Farelo não há nenhum produto com passos: são itens únicos. Se a equipe precisar
registrar a marca (estoque, bar), isso volta como passos e o perfil fiscal
passa a ser o de cada marca.
