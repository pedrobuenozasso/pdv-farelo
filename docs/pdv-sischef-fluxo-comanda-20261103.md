# PDV Sischef — fluxo de comanda, 03/11/2026

Referência da interface observada na sessão autorizada da cafeteria, PDV
Sischef 1.2.46. Nenhum pedido foi confirmado no Sischef nesta inspeção.
Complementa `referencia-pdv-sischef-20261102.md` e
`sischef-extraction/pdv-comanda-mesa-live.md` no diretório pai.

## Observação direta

1. Em **Comanda e mesa**, a grade traz comandas numeradas e busca por número.
   O topo mostra comandas abertas, mesas ocupadas, pessoas e ocupação.
2. **Um clique** na comanda 1 abre um diálogo com o título
   `Lançamento de pedido — Comanda 1`. A comanda ainda não tinha pedido.
3. O diálogo já abre com busca de produto, categorias, produtos, painel do
   pedido e ações `Confirmar (F10)` e `Receber (F9)`. Não há etapa intermediária
   de abrir a comanda ou apertar `Adicionar item`.
4. No painel do pedido há comanda fixa, mesa opcional, cliente, informações
   adicionais, garçom principal, subtotal, taxa de serviço, desconto e total.
5. Fechar o diálogo sem confirmar não cria pedido. O carrinho é rascunho local;
   apenas as ações de confirmação gravam, conforme observação anterior.
6. Ao adicionar `ÁGUA MINERAL` à comanda 2, o card do produto passou a exibir
   controles `− 1 +`; o painel do pedido mostrou quantidade, valor unitário,
   total e subtotal de R$ 7,00. Ao fechar com esse rascunho, surgiu a escolha
   **Continuar editando** ou **Descartar**. Foi descartado; nenhuma venda foi
   confirmada.

## REA

O bundle público carregado nessa tela foi identificado como
`index-ukV2IMIl.js` (SHA-256
`8e2eecfefee280a328607ed4d646e70f9f76dec343f643d894a2f273a9675d90`).
Foi baixado sem token de sessão nem dados de cliente, para análise estática
com `analyze_javascript_application`. A REA não concluiu a análise: seu helper
de propriedade de processo no macOS não compilou via `xcrun` dentro de 120 s.
Uma tentativa local com prazo de 300 s confirmou falha de compilação: o
`swiftc` instalado é Swift 5.10, mas a interface Swift do SDK macOS 15.2
usa `@_extern`, que esse compilador não reconhece. A tentativa de usar o SDK
14.5 também falhou, pois sua interface foi gerada por outra revisão do Swift
5.10. Para retomar a extração via REA, é necessário corrigir a combinação
Command Line Tools/SDK e reiniciar o processo da REA; repetir a mesma chamada
sem essa correção não produz evidência nova.
O Evidence parcial retido pela REA marcou o único script como `unavailable` e
contou zero rotas recuperadas; isso não significa que a aplicação não tenha
rotas. `list_browser_targets` também não alcançou um endpoint CDP local para
anexar à sessão autenticada. Não se exportaram token nem cookies do Sischef
para contornar essa limitação.
Portanto as conclusões acima vêm da interface observada e da documentação
anterior, não de código decompilado. O bundle e credenciais não entram no Git.

## Paridade e tickets pequenos

| Fluxo | Sischef | Farelo OS após UI-01a | Próximo ticket |
| --- | --- | --- | --- |
| Abrir comanda | Um clique mostra lançamento | Um clique abre subtela com rascunho de pedido | Validar com operador |
| Sair com rascunho | Pede para continuar ou descartar | Mesma confirmação ao voltar às comandas | Validar com operador |
| Buscar produto | Busca focada ao abrir, categorias e produtos | Busca focada; categorias horizontais e lista de produtos | UI-01b: grade de produtos e navegação |
| Contexto do pedido | Comanda, mesa, cliente, garçom, totais juntos | Comanda no cabeçalho; cliente/mesa abaixo; pagamento lateral | UI-01c: reorganizar painel lateral |
| Confirmar | F10 grava e envia à produção | F10 usa `createOrder` e atualiza pedidos | Teste ponta a ponta na cafeteria |
| Receber | F9 confirma e abre recebimento | F9 cria pedido e abre pagamento | Teste ponta a ponta na cafeteria |
| Comanda fechada | Ciclo de atendimento próprio | Exige `Iniciar nova visita` antes de lançar | Testar reuso sem misturar visitas |

Prioridade: validar UI-01a com o operador na cafeteria, depois UI-01b e UI-01c. A auditoria
das demais telas do PDV continua nos documentos de referência; esta nota cobre
o fluxo específico de abertura e lançamento, sem alegar paridade total.

## Validação no Farelo OS publicado

No release Web `81142b1`, a comanda 2 **disponível** abriu o lançamento com um
clique, já com a busca focada. `Água mineral` entrou apenas no rascunho; sair
abriu a confirmação de descarte e, após descartar, a comanda continuou
disponível sem pedido gravado. A tela foi conferida em desktop e em 390 px:
painel de pagamento dentro da subtela e largura da página igual à viewport
mobile, sem rolagem horizontal. Não se executou F10/F9 nessa comanda, pois isso
criaria um pedido de produção real.
