# Migração Sischef → Farelo OS

Material bruto exportado do Sischef (sistema atual do café, `sistema.sischef.com`)
via **Relatórios → Em excel → Dump (banco)** — exportação nativa em CSV, sem
precisar de scraping/MCP. Puxado em 07/09/2026, banco "SLAVE" (réplica de
leitura, exportação não afeta o sistema em produção).

**Os arquivos em `raw/` NÃO estão no git** (`.gitignore`) — contêm dados
financeiros e de clientes reais. Trate como sensível.

## O que cada arquivo contém

Todos os CSVs são `;`-delimitados, **sem linha de cabeçalho**. Colunas
inferidas por inspeção (não confirmadas com o Sischef) — confirme antes de
confiar em qualquer coluna específica para lógica de negócio.

- **`dump_produto.csv`** (452 linhas) — catálogo completo de produtos:
  id, nome, ?, categoria (nome do grupo), código interno, ?, unidade, NCM,
  ?, saldo/estoque, ?, preço, ?, ?, status (`ATIVO`/`INATIVO`). Inclui
  variações/modificadores (ex. "SABOR DO BOLO" tem 40 linhas — são sabores
  de um produto, não 40 itens de cardápio) misturados com produtos reais de
  cardápio — precisa filtrar pelo grupo antes de usar.
- **`dum_grupo_produto.csv`** (31 linhas) — grupos/categorias de produto:
  id, nome, coluna 3 (`SIM`/`NAO`), coluna 4 (`SIM`/`NAO`). Grupos com
  `SIM;SIM` são categorias reais de cardápio (Bebidas Geladas, Doces,
  Comidinhas Salgadas, Vendinha, Bebidas Quentes, Sabor do Picolé, Bebidas
  Alcoólicas, Doces Especiais, Opções Veganas, Smoothies e Vitaminas, Sodas
  Italianas, Combos, Espaço Infantil); grupos com `NAO;NAO` são grupos de
  modificador/variação (Tipo de Pão, Sucos, Sabor do Sacolé, etc), não
  categorias de cardápio de verdade.
- **`dump_pessoa.csv`** — cadastro de pessoas (clientes/fornecedores): id,
  nome, ..., tipo (`CLIENTE`/etc), ..., telefone, ..., cidade, estado, ...,
  status.
- **`dump_contas.csv`** — plano de contas financeiro: id, nome, is_despesa?,
  código curto, ..., status.
- **`dump_financeiros.csv`** (6,3MB) — histórico financeiro completo
  (contas a pagar/receber). Não inspecionado em detalhe ainda.
- **`dump_movimento_estoque.csv`** (2,5MB) — histórico de movimentos de
  estoque.
- **`DumpPedidos.csv`** (1,7MB) — histórico completo de pedidos.
- **`dump_operacao.csv`** — categorias de operação financeira (receita/
  despesa).
- **`OrdemServico.csv`** — vazio (o café não usa esse módulo do Sischef).

## Configurações coletadas pela UI (não vêm nos dumps)

Levantado navegando Cadastros/Produtos-Taxas/Financeiro/Caixas/Pedidos em
07/09/2026 — dados pequenos, copiados aqui em vez de gerar arquivo:

**Equipe (Cadastros → Usuários + Operadores PDV off)**: 10 contas —
`VANESSA CRISTIAN` (Supervisor da empresa, login `adm.farelodebolo`),
`OPERADOR DE CAIXA` (Operador, login `caixa.farelodebolo` — também é o
operador PDV "Caixa" e "Fiscal" nos Operadores PDV off), `GARCOM01`–
`GARCOM08` (Operador, logins `garcom01.farelodebolo`–`garcom08...`). Mapeamento
sugerido pro Farelo OS: Vanessa → `ADMIN` ou `MANAGER`; Operador de Caixa →
`CASHIER`; Garçons → `ATTENDANT`.

**Taxas de serviço (Produtos/Taxas → Taxas de entrega)** — 4 cadastradas:
Espaço Infantil (R$ 20), Espaço Infantil PCD/Autista (R$ 10), Taxa entrega
apps (R$ 0), Taxa consumo garrafa de vinho/rolha (R$ 30).

**Preço por horário**: nada configurado (vazio).

**Formas de pagamento (Configurações → Forma de pagamento PDVs)** — 5
cadastradas: Caixa (Dinheiro), Integração com Apps, Cartão de Débito (venc.
D+2, baixa automática), Cartão de Crédito (venc. D+30, baixa automática),
PIX.

**Comandas/Mesas (Configurações)**: 105 identificadores cadastrados —
"COMANDA ON" 1–100 (bate exatamente com o seed 1–100 do Farelo OS,
FARELO-031) + mais alguns tipo balcão/convênio na segunda página (não
inspecionados em detalhe).

**Turnos (Caixas)**: 1 turno cadastrado ("TURNO 01", conta abertura/
fechamento = Caixa Dinheiro/Tesouraria, operado por `caixa.farelodebolo`).

## Decisão tomada em 07/09/2026

O catálogo já importado no Farelo OS (96 produtos, 8 categorias, a partir
de prints da tela do QR Pedir) **não foi refeito** com esses dados mais
completos — ficou como está, já em uso desde a abertura do fim de semana.
Esses dumps ficam guardados aqui pra quando a migração completa (clientes,
histórico financeiro/estoque, categorias extras como "Vendinha"/"Combos")
for realmente feita.
