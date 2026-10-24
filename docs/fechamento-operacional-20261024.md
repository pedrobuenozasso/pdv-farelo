# Fechamento operacional do PDV — 24/10/2026

Critério de conclusão: uma comanda real percorre pedido, produção, conferência,
pagamento e fechamento com valores corretos; a equipe opera as telas sem recorrer
ao Sischef; a NFC-e autorizada e recuperável acompanha a venda; impressão e
backup foram ensaiados na cafeteria. Cada etapa abaixo deve ser demonstrada,
não inferida de um build ou de uma tela aberta.

| Fluxo | Evidência atual | Próxima prova para concluir |
| --- | --- | --- |
| Login do Caixa | `caixateste` entrou pelo túnel privado no Windows; principal ativo com senha esquecida. | Trocar a senha do principal pelo Admin, entrar com ele e confirmar permissões. |
| Comanda → pedido | Item e preço apareceram corretamente no teste presencial. Dois `OrderCreated` recentes foram processados pelo outbox; os dois pedidos de teste foram depois cancelados. | Conferir opções, observações, quantidades, cancelamento e envio de uma comanda completa com a equipe. |
| Produção | O backend criou `KITCHEN_TICKET`s e o KDS consulta a fila a cada 5 s; ainda não há login `KITCHEN` nem ensaio presencial da tela. | Abrir o KDS com papel autorizado, confirmar chegada do pedido e transições preparando/pronto/entregue. |
| Impressão | A Epson TM-T20X é USB. O Edge Agent atual usa TCP; o teste via diálogo do Edge produziu papel com layout errado. Dois tickets de pedidos cancelados ficaram pendentes no banco. | Definir integração USB e formato do ticket planejado, ensaiar papel real e evitar duplicidade. A correção `b73238d` filtra tickets de pedidos cancelados da fila entregue ao agente. |
| Pagamento e caixa | As telas e serviços existem, mas não houve fechamento presencial de uma venda com maquininha separada. | Registrar apenas pagamento realmente recebido; conferir total, troco, fechamento, movimento e reversão/cancelamento. |
| Fiscal | XML, assinatura, numeração e clientes SEFAZ existem como serviços explícitos; banco da VPS ainda não tem emitente, perfis, sequência ou documento. Nenhuma NFC-e foi transmitida. | Classificar cardápio, configurar emitente/CSC/A1 com segredos fora do Git, validar XSD e homologar autorização, consulta e cancelamento antes de produção. |
| Segurança e continuidade | Web privada por túnel SSH; API/DB internos. Backup pré-release existe, mas backup externo automático e restauração periódica estão pendentes. | Completar backup externo, recuperação, monitoramento e acesso HTTPS/domínio se houver uso fora do túnel. |

Em 24/10 a correção que filtra tickets de pedidos cancelados passou em
PostgreSQL descartável: 913 testes backend, zero falhas, erros ou testes
pulados. O build Web com webpack, lint e TypeScript passaram após remover os
botões de impressão não validados. Isso verifica o código; os passos de bancada
da tabela continuam necessários.

Ordem de trabalho: (1) impedir efeito colateral de testes e fechar o fluxo
comanda/produção/caixa; (2) corrigir os problemas concretos de interface trazidos
pela equipe; (3) completar fiscal de homologação e produção; (4) integrar a
Epson USB com o layout aprovado; (5) ensaio de ponta a ponta e corte controlado
do Sischef. A impressão pelo navegador foi retirada do PDV porque falhou na
validação física; nenhum ticket pendente deve ser disparado na impressora sem
revisão.
