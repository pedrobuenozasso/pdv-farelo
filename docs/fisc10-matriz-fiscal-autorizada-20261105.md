# FISC-10 — Matriz fiscal efetiva lida em NFC-e autorizadas (05/11/2026)

Leitura somente leitura na retaguarda do Sischef (`sistema.sischef.com`), sessão
autenticada da cafeteria. Responde à lacuna registrada em
`engenharia-reversa-retaguarda-sischef-20261104.md`: os campos de grupo
tributário do produto e do grupo de produto aparecem **vazios**, então a regra
efetiva não se deduz do cadastro. Aqui ela é lida **no que a SEFAZ autorizou**.

**Método.** Abertas 8 NFC-e autorizadas consecutivas (status 100), nº 13925 a
13932, emitidas entre 01/11 e 04/11/2026, pela tela `notafiscal.jsf?id=…`
(somente leitura; a nota aparece como "Finalizada – não pode ser alterada").
De cada item foram lidos apenas: descrição, NCM, operação, CFOP e, onde a seção
de impostos foi expandida, ICMS/PIS/COFINS. Todas as notas lidas tinham
destinatário **CLIENTE FINAL**. **Nenhum XML foi baixado**, nenhuma nota foi
alterada, e nenhum dado de consumidor, chave de acesso, CSC ou token entrou
neste documento.

## Resultado

**44 linhas de item, 8 notas, 23 produtos distintos: 100% com a operação
`221134 – VENDA DE MERCADORIAS` e CFOP `5102`.** Na linha com impostos
expandidos (Água mineral, nota 13932): ICMS Simples Nacional **CSOSN 102**,
PIS **CST 99**, COFINS **CST 99**.

Na prática, o Sischef emite **todo o cardápio com um único perfil**:

| Operação | CFOP | ICMS (CSOSN) | PIS | COFINS | Origem |
| --- | --- | --- | --- | --- | --- |
| VENDA DE MERCADORIAS | 5102 | 102 | 99 | 99 | 0 |

O grupo tributário "com ST" (5405/CSOSN 500) e a operação `VENDA DE PRODUÇÃO
DO ESTABELECIMENTO` (221136) **existem no cadastro, mas não aparecem em
nenhuma nota da amostra**. Isso corrige a leitura de `fisc02-rascunho-
classificacao-20261103.md`, que propunha dois perfis como se ambos estivessem
em uso.

## NCM efetivamente autorizado por produto

Nome como aparece na nota do Sischef (pode diferir do nome no Farelo OS).

| Produto (Sischef) | NCM autorizado | Notas | Alerta |
| --- | --- | --- | --- |
| ÁGUA MINERAL | 22011000 | 13925–13932 (4) | produto com CEST de ST emitido como 5102/102 |
| ÁGUA COM GÁS | 22011000 | 13929 | idem |
| ÁGUA DE COCO | 21069030 | 13929, 13927 | |
| REFRIGERANTE | 21069090 | 13931, 13929, 13925 | **NCM incompatível** (refrigerante é 2202.x); ST não aplicada |
| SUCO LARANJA INTEGRAL | 21069090 | 4 notas | |
| SUCO UVA INTEGRAL | 21069090 | 13926 | |
| CHA GELADO SABORES | 21069090 | 13927 | |
| CAFE COADO | 21011200 | 13931, 13928, 13925 | |
| CAFÉ EXPRESSO LONGO | 21011200 | 13927 | |
| CAPPUCCINO DA CASA | 21011200 | 13926 | |
| CAFÉ COM LEITE (PINGADO) | 21069090 | 13926, 13925 | diverge dos demais cafés (21011200) |
| PAO COM OVO E BACON | 21069090 | 4 notas | |
| PÃO NA CHAPA (FATIA) | 21069090 | 3 notas | |
| CESTINHA DE MINI PÃES DE QUEIJO | 19059010 | 3 notas | |
| MINI COXINHAS DE FRANGO COM REQUEIJÃO | 19059090 | 13926 | |
| RAMEQUIN DE REQUEIJÃO | 04061090 | 3 notas | |
| MUFFIN DE FRUTAS (ZERO) | 21069090 | 13930 | diverge do outro muffin (19059020) |
| MUFFIN ZERO DE BANANA COM DAMASCO | 19059020 | 13930 | |
| BOLO DE RED VELVET COM CREAM CHEESE | 19059090 | 13929 | |
| BOLO GELADO SABORES | 19052090 | 13929 | |
| BOLO DE LARANJA | 21069090 | 13927 | diverge dos demais bolos (1905.x) |
| BROWNIE COM COBERTURA DE DOCE DE LEITE | 19059090 | 13928 | |
| PRODUTO PADRAO INTEGRACAO APPS | **00000000** | 13929, 13925 | **NCM inválido em nota autorizada** (produto genérico de pedidos de app) |

A amostra cobre 23 produtos, não o cardápio inteiro. Para os demais, a fonte
continua sendo os candidatos históricos em `fiscal_ncm_review` (Admin
`/admin/fiscal-review`), que devem ser conferidos contra esta tabela.

## O que isto muda para o Farelo OS (decisão da contabilidade)

1. **Opção de continuidade (menor risco):** emitir no Farelo exatamente como o
   Sischef emite hoje — um perfil fiscal único (CFOP 5102, CSOSN 102, PIS 99,
   COFINS 99, origem 0) e o NCM já autorizado de cada produto. Isso replica o
   que a SEFAZ vem aceitando e reduz o FISC-02 a um "de acordo" do contador.
2. **Melhorias que só o contador pode autorizar** (não aplicar por inferência):
   - Itens revendidos com ICMS já retido por ST (águas, refrigerante,
     energético, cerveja) poderiam sair com **CFOP 5405 / CSOSN 500**. No
     Simples Nacional, emitir esses itens com CSOSN 102 pode levar a recolher
     de novo no DAS um ICMS que já foi pago por substituição: **possível
     economia**.
   - O que a casa **produz** (bolos, salgados, cafés preparados) é venda de
     produção própria; a operação `VENDA DE PRODUÇÃO DO ESTABELECIMENTO` já
     existe e tipicamente leva **CFOP 5101**.
   - Corrigir NCMs: REFRIGERANTE, o produto genérico de app (`00000000`) e as
     divergências entre cafés, bolos e muffins.
3. **FISC-03 (adicionais/variantes):** como tudo sai com o mesmo perfil, a regra
   de continuidade é trivial — o componente segue o perfil único. Só muda se o
   contador adotar 5101/5405 por tipo de item.

## FISC-04 — numeração

A nota **nº 13932** foi autorizada em 04/11/2026 11:04 (série 1, NFC-e
modelo 65). O número "próximo" calculado em 03/11 já foi consumido: **a
sequência avança todo dia de operação** e o número inicial do Farelo em
produção só pode ser fixado **no dia do corte**, depois da última nota do
Sischef. Homologação usa sequência própria.

## Estrutura contábil observada (para o módulo financeiro)

**Operações** (`/admin/operacao/operacaoList.jsf`): 63 ativas, agrupadas em
Despesas com pessoal, Despesas financeiras, Despesas gerais, Despesas
operacionais, Despesas veículos, Receitas financeiras e Receitas operacionais.
Cada uma define se debita/credita o financeiro e se movimenta estoque. As de
venda: `VENDA DE MERCADORIAS` (221134), `VENDA DE MERCADORIAS COM ST` (221135),
`VENDA DE PRODUÇÃO DO ESTABELECIMENTO` (221136) — todas creditam o financeiro e
debitam o estoque. Lista completa preservada fora do Git, no scratchpad da
sessão, e reproduzível pela tela.

**Contas** (`/admin/conta/contaList.jsf`): 9 ativas, registradas aqui só por
nome e tipo (sem dados bancários):

| Conta | Tipo | Regra |
| --- | --- | --- |
| CAIXA (DINHEIRO) | caixa | créditos e débitos |
| CONTA BANCÁRIA | banco | créditos e débitos |
| TESOURARIA | outros | créditos e débitos |
| CARTÃO DE CRÉDITO | operadora de cartão | a prazo, baixa automática D+30, só créditos |
| CARTÃO DE DÉBITO | operadora de cartão | a prazo, baixa automática D+2, só créditos |
| INTEGRAÇÃO COM APPS | operador/caixa integração | a prazo |
| PIX | PIX estático | a prazo |
| CONTAS A PAGAR | outros | só débitos |
| CONTAS A RECEBER | outros | só créditos |

**Parâmetro de venda:** "VENDA – operação padrão nas operações de saída de
mercadoria" aparece marcado como *configurado*, mas o seletor exibe `--`; o
valor efetivo foi confirmado pelas notas (221134).

## Comandas

`/admin/identificador/identificadores.jsf`: a loja atende **por comanda**;
**105 comandas** cadastradas, **0 mesas**. O Farelo OS tem **100** comandas.
Se a equipe usa as comandas físicas 101–105, é preciso cadastrá-las antes do
piloto.

## Lacunas que permanecem

- CSOSN/PIS/COFINS confirmados em uma linha expandida; nas demais, inferidos
  pela operação idêntica (mesma operação → mesmas regras). Expandir mais itens
  se o contador exigir prova por linha.
- Produtos do cardápio que não apareceram na amostra (ex.: cervejas, picolés,
  porções, smoothies) — ler novas notas que os contenham antes de fechar o
  FISC-02.
- Notas de pedido por aplicativo usam o produto genérico com NCM `00000000`;
  decidir com o contador como o Farelo deve tratar esse canal.
