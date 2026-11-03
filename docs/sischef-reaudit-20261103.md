# Reauditoria do Sischef — 03/11/2026

## Estado e fonte

O proprietário pediu nova extração somente leitura de todas as telas da Retaguarda, com prioridade fiscal e contábil. A sessão autenticada foi aberta depois da primeira tentativa. A seção seguinte contém observações feitas **em 03/11/2026**, separadas da evidência histórica de outubro.

A skill REA foi usada para tentar observar uma sessão de navegador isolada. O navegador não iniciou antes do limite do provedor, e a limpeza não foi confirmada. Não houve captura autenticada por REA. A inspeção autenticada prosseguiu pela interface, sem alterar cadastros, emitir documentos ou revelar A1, CSC, tokens, senhas, XMLs com dados de clientes e identificadores pessoais no Git.

## Observado hoje na sessão autenticada

| Tela/filtro | Observação | Limite |
| --- | --- | --- |
| `Notas fiscais → Painel Fiscal`, 01–31/10/2026 | Emitente padrão em Simples Nacional, ambiente produção, certificado exibido como válido até 27/05/2027. **136 NFC-e autorizadas**, R$ 11.748,00; zero canceladas/inutilizadas; **3 sem retorno**, **3 números presos em notas não autorizadas**, 124 notas com alerta de conformidade e 8 cadastros para atenção. A sequência aparece íntegra nos 139 números do período. | Não se abriu o detalhe das três notas, nem se executou reenvio/consulta; estado fiscal dessas notas é incerto. Não usar o próximo número por inferência. |
| `Notas fiscais → Configurar NFe` | Uma empresa emissora padrão; NF-e série 1 e NFC-e série 1; ambiente produção; mesmo vencimento de certificado. | Não se abriu o formulário de edição nem se visualizou CSC/A1. |
| `Configurações → Tributações`, ativos | **8 regras**: COFINS 99, ICMS 00 de entrada, CSOSN 102/500 de saída e 900 de entrada/saída, ICMS-ST 50 de entrada, IPI 00 de entrada, PIS 99. Listagem mostra base e alíquota 0%. | Existência da regra não prova vínculo ou aplicação a produto; conferir XMLs e cadastro. |
| `Tributações → Grupos tributários`, ativos | **2 grupos**, IDs 3551 (CFOP 5102) e 3552 (CFOP 5405). | Vínculos atuais a produtos/grupos ainda não conferidos. |
| `Notas fiscais → NFs emitidas` | Resumo mostra outubro **136 saídas / R$ 11.748**, novembro até a inspeção **41 saídas / R$ 3.758**; filtro da lista em últimos 7 dias. | Totais de novembro são parciais; a lista de 58 registros não é o total do mês. |
| `Notas fiscais → NFs recebidas` | Tela mantém filtros de entrada/terceiros e últimos quatro meses; resumo exibiu zero entradas no mês atual e anterior. | Não comprova ausência histórica de compras ou XMLs recebidos. |
| `Notas fiscais → Eventos da NFe` | **90 registros** na listagem padrão; filtros incluem cancelamento, carta de correção e manifestação. | Lista contém eventos antigos; nenhum evento foi transmitido. |
| `Notas fiscais → Gerar SPED` | Funcionalidade indisponível para esta conta. | Não considerar SPED como fonte de migração. |
| `Notas fiscais → Gerar NFs` | Filtro de últimos 7 dias: **97 registros**; seleção para envio/reenvio e filtros de pedidos sem nota, contingência e integração. | Nenhum envio/reenvio foi acionado. |
| `Financeiro → DRE`, 01–31/10/2026 | Regime declarado: competência; CMV a partir das vendas. Relatório exibido com **R$ 0,00** em receitas, custos, despesas e resultado. | Não é prova de faturamento zero, dada a emissão e o DFC. Exige diagnóstico de classificação/configuração. |
| `Financeiro → DFC`, seis meses até novembro/2026 | Outubro mostra **R$ 22.189,00 de receitas**, despesas em travessão; novembro parcial R$ 4.814,50. Setembro segue R$ 33.995,50. | Saldos acumulados não devem ser lidos como saldo bancário real sem conciliação. |
| `Configurações → Contas` / `Operações` / `Forma de pagamento` | **9 contas**, **63 operações**, **5 formas ativas**. Débito: baixa automática D+2; crédito: D+30. | Não se conferiram movimentos individuais nem liquidação por pedido. |
| `Financeiro → Resumo financeiro` | A própria tela diz que só inclui movimentação efetivada; venda a prazo/cartão entra após recebimento. Filtro padrão observado em novembro/2026. | Explica a diferença de base conceitual, não concilia os valores por si só. |
| `Produtos → Produtos simples` / `Produtos com passos` / `Grupos de produto`, ativos | **287 produtos simples**, **20 produtos com passos** e **35 grupos** nas listagens; grupo define visibilidade por canal e estação de notificação. | Passos e opções atuais ainda não foram exportados item a item. |
| `Cadastros → Pessoas`, ativas | Indicadores da tela: **1.334 clientes**, 4 fornecedores e 14 funcionários. | Não foram extraídos dados pessoais; números são do filtro atual, não equivalem a total histórico. |
| `Caixas → Conferência`, últimos 30 dias | **0 caixas abertos** e **15 de 15 pendentes de conferência**; listagem histórica mostra 629 registros. | Conferência não foi efetuada; verificar impacto no fechamento financeiro antes do corte. |
| `Estoque → Acerto` | Valor mostrado **−R$ 32.283,29**; 264 produtos controlam estoque e **250 estão sem saldo**. | Saldo do Sischef não pode ser importado como estoque inicial sem contagem física/reconciliação. |
| `Relatórios → Principais` | Abas Gestão, Financeiros, Fiscais, Vendas, Compras, Estoques e Cadastros; origem indicada como banco `SLAVE`. | Os relatórios individuais ainda precisam ser gerados com filtros e datas registrados. |
| `Pedidos → Consultar pedidos` / `Integrações e API` | Telas e filtros principais acessíveis. | Pedidos individuais e integração não foram exportados nesta rodada. |
| `Cadastros → Usuários` | **10 usuários ativos**; `adm.farelodebolo` aparece como Supervisor da empresa e `caixa.farelodebolo` como Operador. | Não foram lidas ou copiadas senhas; permissões finas ainda não reconferidas. |
| `Caixas → Turnos` | Um `TURNO 01` ativo, ligado ao operador de caixa; abertura em Caixa (Dinheiro), fechamento em Tesouraria, com operações de suprimento e sangria. | Estado de conferência dos últimos 30 dias permanece pendente conforme linha acima. |
| `Configurações → Comandas/Mesas` | Loja opera por **comanda**; mesa é opcional; **105 comandas** e **zero mesas**. | Confirmar endereçamento de QR e ciclo de visita no Farelo OS em ensaio presencial. |
| `Produtos → Precificação` | 287 itens no filtro; markup médio 12,2×, margem média 60,5%, 11 abaixo do ideal; base selecionada: preço de compra. | Custos ausentes/zerados podem distorcer agregados; não importar preço sugerido sem conferência. |
| `Financeiro → Consultar financeiros` | Filtro padrão de últimos sete dias por vencimento; ignora transferência interna e taxas de cartão. | Para conciliação, registrar filtros e incluir/excluir movimentos conscientemente. |
| `Financeiro → Extratos` / `Baixas` | Extrato exige conta Caixa, Banco ou Tesouraria e período; Baixas tem consulta por período ou pessoa e estados em aberto/liquidados. | Nenhuma baixa ou extrato foi gerado nesta rodada. |
| `Financeiro → Conciliação bancária`, `Cartões` e `Recorrentes` | As três telas indicam funcionalidade indisponível nesta conta. | Não planejar migração como se esses módulos fossem usados na operação atual. |
| `Pedidos → Compra/devoluções` | Formulários distinguem entrada/saída, emissão própria/terceiros, operação padrão, fornecedor/cliente e status. | Nenhum pedido foi criado; verificar quais fluxos são usados antes de replicá-los. |
| `Pedidos → Venda` | Formulário novo inicia como saída normal, operação padrão `102 VENDA DE MERCADORIAS`, com status Pedido e abas de dados/complemento. | Nenhuma venda foi criada; a operação efetiva da cafeteria continua pelo PDV comanda/mesa. |
| `Financeiro → Transferência/adiantamento/contas a receber` | Formulários separaram conta de saída/entrada, pessoa, operação, documento e data; o recebimento tem lançamento próprio. | Nenhuma movimentação foi salva. |
| `Relatórios → Em excel` | Abas incluem Financeiros, Fiscais, Vendas, Estoques, Cadastros, Conferência e Dump (banco); origem `SLAVE`. | Nenhum arquivo novo foi gerado nesta rodada; a exportação é o próximo passo para cruzamento em lote. |

Na página inicial havia alerta móvel de **114 NFC-e pagas em cartão sem código da transação** entre 30/09 e 30/10/2026. Esse recorte difere do Painel Fiscal de outubro e dos alertas antigos de setembro; não comparar os números como série temporal sem filtros iguais. Não foram copiados nomes de clientes, chaves de acesso nem links com autenticação.

## Evidência já disponível

| Área | Fonte anterior | O que já foi visto | O que falta reconfirmar |
| --- | --- | --- | --- |
| Fiscal | `../../sischef-extraction/audit.md`, `../../sischef-extraction/current-export-audit-20261017.md` | Emitente/Simples Nacional, 2 grupos tributários, 8 tributações, configuração 55/65, painel, 151 XMLs de setembro, eventos, conformidade, relatórios por produto | Cadastro e vínculos **atuais**, IE/CRT/CSC apenas presença, série e próximo número por ambiente, situação de notas após setembro, NCM/CEST/CFOP/CSOSN dos itens ativos |
| Contábil/financeiro | `../../sischef-extraction/fiscal-accounting-reconciliation.md` | Contas, operações, baixas, DRE, DFC, caixas, pagamentos; recorte de setembro com totais divergentes | Conciliar por transação no mesmo período fechado; esclarecer DRE zerado e despesas ausentes; conferir cartões D+30 e contas pendentes |
| Catálogo | `../../sischef-extraction/product-fiscal-inventory.md`, `../../sischef-extraction/audit.md` | Produtos, grupos, passos, estoque, preço, visibilidade por canal, relatório de NCM | Catálogo e modificadores atuais, regras fiscais de adicionais, preço/ordem/visibilidade, itens técnicos versus vendáveis |
| Operação | `referencia-pdv-sischef-20261102.md`, `../../sischef-extraction/pdv-comanda-mesa-live.md` | Comanda/mesa, atalhos, confirmação, pagamento parcial, cancelamento | Regras atuais de caixa, impressão, permissões, relatórios e integrações |

Os caminhos `../../sischef-extraction/` acima levam ao diretório irmão do checkout, não são arquivos publicados nesta branch. Antes de fazer referência em ticket público, substituir pelo caminho efetivamente versionado ou pelo resumo sem dados privados.

## Sequência de coleta ao entrar

1. **Fiscal:** painel e conformidade (período e filtros), empresa/emitente, configuração NF-e/NFC-e, séries/numeração, grupos tributários, regras, vínculos produto/grupo/operação, NFs emitidas/recebidas, eventos, relatórios de NCM/CEST/CFOP/CST. Registrar presença de credenciais sem abrir seus valores.
2. **Financeiro e contábil:** operações, contas, formas e credenciadoras; pedidos, pagamentos, baixa e liquidação; caixas/turnos; extrato, DRE, DFC e relatórios. Usar um período fechado idêntico nas telas e chaves de associação, sem misturar venda, emissão e caixa.
3. **Restante:** pessoas/usuários/permissões, produtos/passos/grupos, estoque, compras/devoluções, pedidos, relatórios, parâmetros, integrações e PDV comanda/mesa. Registrar por tela: URL, data, filtros, campos, regras, contagem, lacuna no Farelo OS e evidência.

## Tickets pequenos preparados (ainda não publicados)

A integração GitHub permitiu ler issues, mas recusou criar issues com HTTP 403 (`Resource not accessible by integration`); o `gh` local também está sem token válido. Não afirmar que estes tickets existem no GitHub até publicação confirmada.

### MIG-01 — Reauditar telas e parâmetros atuais do Sischef

Aceite: mapa de todas as áreas acima, com tela/URL/filtro/data, dados e regras observados, diferenças contra o Farelo OS, sem segredos ou dados pessoais. Separar observação atual, evidência de outubro e hipótese.

### FISC-10 — Extrair configuração e documentos fiscais atuais

Aceite: matriz atual de emitente, série/numeração, grupos, tributações e vínculos de itens/operações; confronto amostral de XMLs autorizados e pedidos; lista de decisões para o contador e relação com [FISC-01 a FISC-09](https://github.com/pedrobuenozasso/pdv-farelo/issues?q=is%3Aissue+is%3Aopen+FISC). Sem aplicar código fiscal por inferência.

### FIN-01 — Conciliar pedidos, NFC-e, pagamentos e DRE/DFC

Aceite: um período fechado com filtros uniformes e cruzamento por ID de pedido, data, conta/operação, pagamento e chave fiscal. Explicar por transação a divergência histórica de setembro/2026 entre pedidos (R$ 19.980), NFC-e (R$ 12.154), DFC (R$ 33.995,50) e DRE (R$ 0), sem presumir equivalência entre bases diferentes.

## Próximo passo imediato

Concluir a coleta de vínculos tributários dos 287 produtos simples e 20 produtos com passos, com destaque para os itens ativos no cardápio do Farelo OS. Depois, extrair os relatórios fiscais e financeiros com **o mesmo período fechado** para cruzar pedido, NFC-e, pagamento e baixa. Investigar as três notas de outubro sem retorno e os 15 caixas pendentes pela interface e com responsável pela operação, sem reenviar, cancelar, inutilizar ou conferir por conta própria. Publicar os três tickets no GitHub quando a permissão de escrita da integração ou do `gh` estiver restaurada.
