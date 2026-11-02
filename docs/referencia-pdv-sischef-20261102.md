# Referência: PDV do Sischef (sistema em uso hoje) — 02/11/2026

Levantamento feito observando o PDV que a cafeteria usa hoje
(`pdv.sischef.com`, versão 1.2.46) com sessão real do operador de caixa, e o
cardápio público do QR Pedir (`farelodebolo.qrpedir.com`). O objetivo é guiar
o UI-01 (usabilidade do PDV do Farelo OS) e o FISC-03 (regra fiscal de
variantes/adicionais), registrando **o que a equipe já tem de memória
muscular** para que a troca pelo Farelo OS exija o mínimo de retreinamento.
Isto descreve o comportamento observado de um sistema de terceiro; não é
cópia de identidade visual.

## Fluxo de lançamento (o que a equipe faz hoje)

Clicar numa comanda da grade **abre direto a tela de lançamento** — não há um
passo separado de "criar comanda". O carrinho é **local até confirmar**: dá
para montar o pedido inteiro, mudar de ideia e sair sem gravar nada. Apenas
dois botões gravam:

- **Confirmar (F10)** — grava o pedido e manda para produção.
- **Confirmar e receber (F9)** — grava e abre a tela de pagamento.

Essa separação é o centro da operação: garçom lança e confirma; caixa lança e
recebe. Mesma tela, dois desfechos.

## Atalhos de teclado (memória muscular da equipe)

O PDV foi desenhado para a mão não sair do teclado. Mapa completo observado no
painel de atalhos:

| Atalho          | Ação                                   |
| --------------- | -------------------------------------- |
| `Ctrl+F`        | Buscar produto                         |
| setas + `Enter` | Navegar a grade e lançar               |
| `F2`            | Cliente                                |
| `F3`            | Alternar tipo do pedido                |
| `F4`            | Endereço de entrega                    |
| `F8`            | Garçom                                 |
| `Ctrl++`        | Último item: +1                        |
| `Ctrl+-`        | Último item: −1                        |
| `Ctrl+Del`      | Remover último item                    |
| `Ctrl+O`        | Editar último item (observação)        |
| `Ctrl+D`        | Desconto                               |
| `F7`            | Consultar estoque do último produto    |
| `F10`           | Confirmar pedido                       |
| `F9`            | Confirmar e receber                    |
| `Ctrl+1`..`9`   | Escolher forma de pagamento            |

Na busca, as setas navegam a grade e `Enter` lança — o campo de texto não
prende as setas. **Se o Farelo OS não cobrir ao menos `Ctrl+F`, setas+Enter,
`F10` e `F9`, a equipe sente no primeiro dia.** É boa parte do risco do UI-01,
e é velocidade de mão, não estética.

## Estrutura da tela de lançamento

Três colunas: **categorias** (bolinha colorida por categoria) → **grade de
produtos** (card com nome e preço em verde; alterna para lista compacta) →
**painel do pedido** (comanda, mesa, cliente `F2`, informações adicionais,
carrinho, rodapé Subtotal / Tx. serviço / Desconto / Total).

- Produto já no carrinho fica **destacado com `− qtd +` embutido no card** —
  ajuste de quantidade sem ir ao carrinho.
- Produto **fora do horário de venda aparece riscado, com o horário** em que é
  vendido. (Recurso que o Farelo OS não tem.)
- O carrinho mostra **unitário e total** por item, separados.

Menu por item (`...`): Editar, Infos, **Adicionais**, Desconto, Duplicar,
Renotificar, **Não notificar** (lança sem mandar para a cozinha) e Remover.

Menu do pedido (`⋮`): **"Imprimir ao confirmar" é um checkbox e vem
desmarcado**, Copiar resumo, Desconto, Reenviar itens, Conhecer a tela nova.

## Modelo de adicionais = "passos"

Tentar abrir "Adicionais" num item simples retorna *"não possui **passos**
configurados"*. O modelo é **produto → passos → opções** (grupos de
modificadores), não um campo solto. Isto importa para o **FISC-03**: a regra
fiscal de variantes/adicionais precisa cobrir uma hierarquia, com preço e
classificação por componente. O PDV do Farelo OS já modela isto como
`product-steps` no backend.

## Pagamento (o que falta no Farelo OS)

- **Pagamento parcial**: lista de "Pagamentos realizados" + **"Falta receber"**
  em destaque laranja.
- **Dividir conta** e **Taxa de serviço** como ações próprias.
- Formas: Dinheiro, Integração com apps, Débito, Crédito, PIX (`Ctrl+1..5`).

## Cancelamento

Cancelar um pedido **exige justificativa obrigatória** (campo de texto antes de
confirmar). Controle simples e eficaz; vale replicar.

## Observações de produto/catálogo

- A operação usa **comanda numerada pura** (1 a 105). **Nenhuma mesa
  cadastrada**; o próprio Sischef trata mesas como opcionais. Não construir
  gestão de mesas no Farelo OS.
- O catálogo do Farelo OS (52 produtos) **já foi importado** do QR Pedir; os
  itens batem. O que falta não é reimportar e sim: descrições completas de ~14
  itens (o cardápio público trunca as longas) e **fotos** — que **não existem
  no QR Pedir** (a única imagem lá é o logo), então precisam ser criadas, não
  migradas.
- O QR Pedir é produto da própria Sischef (imagens servidas de
  `sischef-images`), o que explica a integração entre os dois.

## Estética

Magenta/rosa como cor única de marca sobre branco e cinza, sem gradiente nem
sombra pesada. Competente e legível. Copiar a **lógica** (densidade,
hierarquia, estados claros, teclado primeiro); a identidade visual do Farelo OS
é própria.

## Segurança (achado incidental)

O markup da tela do PDV do Sischef expõe, em texto puro no `href` do bloco de
caixa, um **token JWT de login automático sem expiração** (`nbf` de 2024, sem
claim `exp`) para a conta do caixa. É falha do Sischef, não do Farelo OS.
Ação: abrir chamado pedindo invalidação do token, trocar a senha da conta no
Sischef e não reutilizar essa senha no Farelo OS. Não registrar o token aqui.
