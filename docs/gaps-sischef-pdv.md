# Funcionalidades do Sischef ainda não implementadas no Farelo OS

Levantado explorando o PDV real (`pdv.sischef.com/pedidosIdentificador`) e o
back-office (`sistema.sischef.com`) em 07/09/2026 — comparação funcional,
não uma lista de tickets numerados ainda. Deliberadamente **fora** deste
levantamento (o Pedro pediu pra não priorizar agora): Delivery completo,
Venda rápida, Balança, Cardápios Integrados (sincronização com QR
Pedir/Goomer etc.) — toda essa família de integração externa/canais.

## 1. Ciclo de vida do caixa (abertura/fechamento)

Farelo OS hoje não tem nenhum conceito de "caixa" como sessão — o Sischef
trata isso como uma entidade própria, ligada a um Turno:

- **Abertura de caixa**: vincula um Turno (conta de abertura/fechamento
  pré-configuradas, ex. "Caixa Dinheiro" → "Tesouraria") a um operador, com
  timestamp.
- **Fechamento de caixa**: informa "valor de fechamento (dinheiro)",
  registra data de fechamento, e fica com status `Não conferido` até
  alguém rodar a conferência.
- **Conferência de caixa**: passo separado do fechamento — bate o valor
  informado contra o que o sistema calculou.
- **Sangria**: retirada de dinheiro do caixa durante o turno (segurança/
  depósito parcial).
- **Suprimento**: reforço de troco/fundo de caixa durante o turno.
- **Lançar despesa/receita direto do caixa**: movimento financeiro ad-hoc
  sem passar por um pedido.
- **Turnos**: um turno é um template nomeado (ex. "TURNO 01") com contas de
  abertura/fechamento fixas — reaproveitado a cada abertura de caixa.

## 2. Tipo de pedido escolhido na criação

O PDV do Sischef pergunta o tipo **antes** de escolher produtos: `DELIVERY`
/ `BALCÃO` / `COMANDA` / `VENDA RÁPIDA`. Farelo OS só tem o fluxo de
comanda — não tem um conceito de "balcão" (venda sem comanda, cliente leva
na hora) como tipo de pedido distinto.

## 3. Mesa como conceito separado de Comanda

O Sischef tem **Mesa** e **Comanda** como entidades diferentes, mas
vinculáveis (uma comanda pode estar associada a uma mesa). Métricas de
topo: "Mesas ocupadas", "Qtde de Pessoas" (quantidade de gente sentada),
"Ocupação %" (mesas ocupadas / total). Farelo OS só tem Comanda — não tem
Mesa, nem esses três indicadores de ocupação.

## 4. Garçom responsável pelo pedido

Ao abrir um pedido, o Sischef atribui um "Garçom Principal" (removível/
trocável) — quem está atendendo aquela comanda. Farelo OS não atribui
nenhum atendente a uma comanda/pedido.

## 5. Vincular cliente cadastrado na hora do pedido

"Selecionar/Cadastrar cliente" aparece já na primeira tela de lançamento do
pedido — ou seleciona um cliente existente ou cadastra um novo, sem sair do
fluxo. Farelo OS só grava `customerName`/`customerPhone` como texto livre
no Order — não tem uma entidade Cliente/Pessoa nem uma tela de
seleção/cadastro nesse ponto do fluxo.

## 6. Observação do pedido (nível do pedido, não do item)

Um campo de texto livre "Observação do pedido" no passo final de
lançamento — anotação geral do pedido inteiro. Farelo OS não tem isso (só
`OrderItem` tem campos de cancelamento, não uma observação livre de
pedido).

## 7. "Complemento identificador"

Um campo extra de texto livre pra identificar melhor o pedido/comanda
(além do número). Não claro exatamente pra que serve sem testar mais a
fundo — mencionar como uma dúvida aberta, não uma funcionalidade confirmada.

## 8. Ações por item do pedido

Menu "..." em cada item já lançado: Editar, Infos, **Adicionais**
(modificadores/complementos do produto, tipo "sem cebola"/"leite
desnatado"), **Desconto** (por item, não só por comanda inteira),
Duplicar, Renotificar (manda de novo pra cozinha), Não notificar (adiciona
sem mandar pra produção). Farelo OS tem desconto só no nível da comanda
inteira (FARELO-230/231/232) — não tem desconto por item, não tem
"adicionais"/modificadores de produto, não tem duplicar/renotificar item.

## 9. Ações do pedido inteiro (menu "...")

No nível do pedido: "Imprimir ao confirmar" (checkbox — imprime
automaticamente ao confirmar, sem precisar de ação separada), "Copiar
resumo do pedido" (pra colar em WhatsApp etc.), "Enviar novamente os itens
para produção" (reimprimir tudo pra cozinha). Farelo OS sempre imprime via
fila assíncrona (Edge Agent) — não tem essa opção de imprimir-ao-confirmar
como comportamento configurável por pedido, nem "copiar resumo".

## 10. Confirmar + Receber num botão só

Além de "Confirmar", existe um botão "Receber" que confirma o pedido E
já abre a tela de pagamento em sequência — atalho pra quando o cliente já
vai pagar ali (ex. balcão). Farelo OS trata confirmar e pagar como dois
fluxos sempre separados.

## Tickets propostos (FARELO-306 a FARELO-320)

Numeração continua de FARELO-305 (último ticket real usado no projeto até
07/09/2026). Cada ticket segue as mesmas regras já em vigor no projeto:
um objetivo principal, ~500 linhas de lógica, testes pra regra de negócio,
compatibilidade com a arquitetura existente, Conventional Commits, ler
CLAUDE.md/AGENTS.md/docs/domain-model.md antes de começar, e **auditar o
código atual antes de assumir que o ticket não existe** (mesma disciplina
já seguida em todos os Blocos anteriores desta migração).

### Domínio Caixa (novo — sem sobreposição com Order/Comanda)

- **FARELO-306**: Criar `Turno` — template nomeado com conta de abertura e
  conta de fechamento pré-configuradas (ex. "Caixa Dinheiro" →
  "Tesouraria"), reaproveitado a cada abertura de caixa.
- **FARELO-307**: Criar `Caixa` — abertura vinculada a um `Turno` e ao
  usuário autenticado (`AuthenticatedPrincipal`), com timestamp de
  abertura. Um caixa aberto por vez por operador (decidir a regra exata
  olhando o `Turno` do Sischef como referência, não copiar 1:1 sem
  necessidade).
- **FARELO-308**: Fechamento de caixa — informa valor de fechamento
  (dinheiro), grava timestamp de fechamento, fica com status inicial "não
  conferido".
- **FARELO-309**: Conferência de caixa — bate o valor informado no
  fechamento contra o total calculado pelo sistema (pagamentos registrados
  no turno); resultado (confere/não confere) e quem conferiu.
- **FARELO-310**: Sangria e Suprimento — movimento de caixa durante o
  turno (retirada/reforço de dinheiro), sem estar ligado a nenhum pedido;
  auditável (ver `AuditLogService`, mesmo seam já usado por
  Payment/Order-cancel).
- **FARELO-311**: Lançar despesa/receita direto do caixa — movimento
  financeiro ad-hoc, fora do fluxo de pedido/pagamento.

### Novas entidades (Cliente e Mesa)

- **FARELO-313**: Criar cadastro de `Cliente` (nome, telefone, outros
  campos que fizerem sentido) + endpoint de busca/criação + hook no fluxo
  do PDV pra selecionar um cliente existente ou cadastrar um novo sem sair
  da tela de pedido. Decidir explicitamente: `Order.customerName`/
  `customerPhone` continuam como estão (snapshot congelado, já
  documentado) ou passam a ser preenchidos a partir do `Cliente`
  selecionado — não migrar dado existente sem avaliar o impacto.
- **FARELO-315**: Criar `Mesa` (número, capacidade) + campo opcional de
  vínculo em `Command`/`Order` (uma comanda pode estar associada a uma mesa)
  + os três indicadores de ocupação (mesas ocupadas, qtde de pessoas,
  % ocupação) em algum lugar visível do PDV.

### Fluxo de pedido/PDV (tickets sequenciais — todos tocam Order/OrderItem)

- **FARELO-312**: Adicionar tipo de pedido `BALCÃO` (venda sem comanda,
  cliente recebe/leva na hora) como alternativa ao fluxo de comanda
  existente.
- **FARELO-314**: Atribuir garçom/atendente responsável a um pedido
  (campo opcional, editável).
- **FARELO-316**: Observação geral do pedido — campo de texto livre no
  `Order`, nível pedido (não item).
- **FARELO-317**: Desconto por item — estende o desconto hoje só a nível
  de comanda inteira (FARELO-230/231/232) pra também poder ser aplicado a
  um `OrderItem` específico. Decidir se reaproveita a entidade `Discount`
  existente (com uma referência opcional a item) ou precisa de modelagem
  própria — avaliar antes de implementar, não assumir.
- **FARELO-318**: Duplicar item (adiciona uma cópia do mesmo item no
  pedido) e Renotificar item (reenvia aquele item específico pra
  produção/impressão sem duplicar).
- **FARELO-319**: Ações do pedido inteiro — "imprimir ao confirmar"
  (comportamento configurável por pedido), "copiar resumo do pedido"
  (texto formatado pronto pra colar em WhatsApp etc.), "reenviar todos os
  itens pra produção".
- **FARELO-320**: Botão "Confirmar e Receber" — atalho de UI que confirma
  o pedido e já abre a tela de pagamento em sequência, sem exigir dois
  cliques/duas telas separadas.

**Fora de escopo por agora** (não criar ticket): "Complemento
identificador" (dúvida aberta, item 7 acima — não confirmado o que faz) e
"Adicionais"/modificadores de produto (item 8 acima — escopo grande
demais pra um ticket só, precisa de desenho próprio antes de virar
ticket).

## Já temos equivalente (não é gap)

- **KDS**: Farelo OS já tem `/kds` — mesma ideia da tela KDS do Sischef.
- Impressão de comanda (cozinha) e conferência (caixa): já implementado
  (Blocos A/E desta migração, modo gráfico ADR-003).
