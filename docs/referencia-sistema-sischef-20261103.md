# Referência: Retaguarda do Sischef (back-office) — 03/11/2026

Levantamento do sistema administrativo que a cafeteria usa hoje
(`sistema.sischef.com`, "Retaguarda"), feito com a sessão real do Admin, para
alimentar os tickets fiscais (**FISC-01, FISC-02, FISC-04**) e a migração em
geral. São dados do próprio negócio; **dados pessoais de clientes (consumidores
das notas) não foram extraídos** — onde uma tela os contém, isto é anotado, não
transcrito.

## Mapa do sistema (árvore de menus)

Retaguarda do Sischef, menu lateral e URLs observadas:

- **Início** — `/inicio.jsf`
- **Cadastros**: Pessoas (`/admin/pessoa/pessoaList.jsf` — contém dados de
  clientes, PII), Usuários (`/admin/unidade/usuarioSistemaList.jsf`),
  Operadores PDV off (`/admin/usuario/operadorList.jsf`)
- **Produtos/Taxas**: Grupos de produto (`grupoprodutoList.jsf`), Produtos
  simples (`produtoList.jsf`), Produtos com passos (`produtoPassosList.jsf`),
  Taxas de entrega, Regras por horário (`precoPorHorarioList.jsf`),
  Precificação (`precoPendente.jsf`)
- **Financeiro**: contas a pagar/receber, transferências, adiantamentos,
  recorrentes, consultar financeiros, extratos, conciliação bancária, baixas,
  recibos, conferência de caixa, turnos
- **Caixas**
- **Pedidos**: pedidos de saída/compra, devoluções, consultar pedidos
- **Notas fiscais**: **Painel Fiscal** (`notafiscal/painelFiscal.jsf`), Gerar
  NFs (`pedido/pedidoNFCeList.jsf`), **NFs emitidas**
  (`notafiscal/notaEletronicaList.jsf`), NFs recebidas, Gerar SPED, Carta de
  correção, Manifestação destinatário, Eventos da NFe
- **PDV Sischef**: Abrir PDV, Itens excluídos, Itens transferidos, Senha PDV
- **Integrações e API** — `/admin/pedido/integracao/integracoes.jsf`
- **Estoque**: Acerto de estoque, Entrada/saída manual
- **Relatórios**: Principais, Em excel, Parâmetros
- **Configurações**: Parâmetros (`parametro-config/<CATEGORIA>/`)

## FISC-01 — Dados do emitente (confirmado na fonte)

Do Painel Fiscal → "Configuração fiscal da empresa" (é o que sai como emitente
no XML de cada nota):

| Campo | Valor |
| --- | --- |
| Nome fantasia | FARELO DE BOLO QUINTAL E CAFE |
| Razão social | pessoa física (titular) — copiar a string exata de "Dados da empresa"; contém CPF, não transcrita aqui |
| CNPJ | 47.586.528/0001-58 |
| **Inscrição estadual** | **714180410115** |
| Inscrição estadual ST | não preenchido |
| Inscrição municipal | não preenchido |
| CNAE | não preenchido |
| Regime tributário | **Simples Nacional** → **CRT 1** |
| Município / UF | **Vinhedo / SP** (código IBGE **3556206**) |
| Série NF-e (55) | 1 |
| Série NFC-e (65) | 1 |
| CSC da NFC-e | Configurado (ID 000001) — o token é segredo, não exibido |
| Certificado A1 | válido até **27/05/2027** |
| Ambiente | **Produção** |

**Destrava o FISC-01.** Os campos que estavam vazios no banco do Farelo OS
agora têm valor conferido na fonte: **IE = 714180410115** e **código IBGE de
Vinhedo = 3556206**. O **CRT 1 fica confirmado** (Simples Nacional) — era uma
pendência registrada no `contexto.md`. Preencher no `/admin/fiscal-company` e
salvar (o formulário novo normaliza o CNPJ).

## FISC-02 / FISC-03 — Classificação fiscal dos itens

O Sischef separa **o que é do produto** do **que é da tributação**:

- **No produto** (aba Tributações fiscais): NCM, CEST, **origem da mercadoria
  (0 - Nacional)**, unidade tributável. Exemplo conferido — ÁGUA COM GÁS:
  NCM `22011000`, CEST `0300100`, origem 0, UN.
- **Na tributação**: CFOP, CSOSN, PIS, COFINS, ICMS ficam em **grupos
  tributários** reutilizáveis (`produtos/grupoTributarioList.jsf`), associados
  ao produto ou ao grupo do produto. **Não** são campo a campo em cada item.

Existem **apenas 2 grupos tributários** para o cardápio inteiro:

| Grupo | CFOP | Uso |
| --- | --- | --- |
| Tributação venda de mercadoria | **5102** | venda normal (maioria dos itens) |
| Tributação venda de mercadoria com ST | **5405** | itens com ICMS-ST já retido (ex.: refrigerantes/industrializados) |

As operações fiscais (`impostos/tributacaoList.jsf`, 8 no total) definem, para
**saída (venda)**:

- **ICMS (Simples Nacional):** CSOSN **102** (tributada pelo Simples, sem ST) e
  CSOSN **500** (ICMS retido por ST). Há ainda CSOSN 900 (catch-all).
- **PIS:** CST **99** (Outras Operações), 0% · **COFINS:** CST **99**, 0%
- Origem **0 - Nacional**, base e alíquota **0%** em tudo (correto no Simples
  Nacional — imposto recolhido via DAS, não por nota).

**Implicação para o Farelo OS:** a classificação se resume a dois perfis
fiscais, não a dezenas de campos por item:

- **Perfil padrão:** CFOP 5102 · CSOSN 102 · PIS 99 · COFINS 99 · origem 0
- **Perfil com ST:** CFOP 5405 · CSOSN 500 · PIS 99 · COFINS 99 · origem 0

Cada item recebe NCM/CEST próprios (do cadastro Sischef) e cai em um dos dois
perfis conforme tenha ou não ICMS-ST. FISC-03 (adicionais/variantes) segue o
perfil do item pai. Isto deve ser **confirmado com a contabilidade** antes de
emitir — é a leitura da configuração atual, não um parecer.

## FISC-04 — Série e próximo número

- **Produção:** modelo NFC-e (65), **série 1**. Última autorizada observada em
  01/11/2026: **nº 13931** → **próximo número = 13932**. (Lido da chave de
  acesso e da lista de NFs emitidas; número histórico, reconferir no dia.)
- **Homologação:** o Sischef opera só em produção aqui. Para os ensaios de
  homologação do Farelo OS, a numeração de homologação é uma sequência
  independente (começa do 1 na série de teste) — confirmar a série de teste com
  a contabilidade. **Nunca** reaproveitar a numeração de produção para teste.

## Integrações ativas

48 integrações no catálogo, **apenas 2 ativas** (0 com problema): **Cardápio
Sischef** e **QrPedir** — ambas de marca própria (pedido pelo QR/link, sem
marketplace). **Nenhum iFood/marketplace ativo.** O Farelo OS já cobre esse
escopo com seu cardápio público + comanda QR; não há integração de terceiro a
replicar.

## Catálogo

A lista de produtos do Sischef tem **287 registros** (29 páginas), incluindo
inativos, históricos e adicionais — contra 52 ativos no Farelo OS / ~50 no
cardápio QR. A diferença é cauda histórica, não itens vivos faltando. Produtos
têm código de barras próprio no Sischef (ex.: Água com gás = 913).

## Avisos fiscais pendentes no Sischef (contexto, não ação do Farelo OS)

- "Notas fiscais sem código de autorização": **114 notas** pagas em cartão
  saíram sem o código da transação.
- Painel Fiscal: **3 notas "sem retorno"** (precisam de atenção) e 124 notas
  com problema de conformidade no período analisado.

Isto é da operação atual no Sischef; não bloqueia o Farelo OS, mas mostra que a
conciliação cartão↔nota é um ponto de atenção que o Farelo OS deve tratar bem.

## Segurança (recorrente)

Vários links do Sischef (abrir PDV, Analytics) embutem **tokens JWT de login
sem expiração** no `href`, em texto puro. Já reportado na análise do PDV
(`referencia-pdv-sischef-20261102.md`). Ação permanece: pedir invalidação ao
Sischef e não reutilizar senhas entre Sischef e Farelo OS. Tokens não
transcritos aqui.
