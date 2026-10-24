# Ensaio presencial do PDV — cafeteria

Preparado em 24/10/2026 para validar a versão privada `f522695` da VPS. O
roteiro usa uma comanda escolhida pela equipe e valores reais conferidos na
tela. Não registrar dados de cliente, senha, comprovante de cartão ou chave
privada neste arquivo.

## Acesso a partir do Windows da cafeteria

A Web da VPS escuta apenas em `127.0.0.1:3000` do servidor. Em 24/10, a chave
**pública** do PC foi instalada na conta `farelo-tunnel`. Essa conta exige chave,
não aceita senha nem terminal e só pode encaminhar localmente para
`127.0.0.1:3000` da VPS. O acesso administrativo por chave foi conferido após
o reload do SSH. Em 24/10, o Windows autenticou pela chave e o proprietário
abriu a tela de login pelo túnel local na porta **3000**. Em seguida entrou com
`caixateste` (papel CASHIER); o acesso ao PDV foi confirmado pelo proprietário.
O login de `caixa.farelodebolo`, cuja senha foi esquecida, e o fluxo de venda
ainda precisam de ensaio. A conta de teste não deve ser desativada antes de
restabelecer e testar o acesso principal. Conferir a impressão digital ED25519
do servidor antes de aceitar o primeiro acesso:
`SHA256:s4+iJTV1NdyIIO9UyUQSRzAq+i2JmJD/CFHe5uFPFPw`.

No PowerShell do Windows, executar:

```powershell
ssh -i "$env:USERPROFILE\.ssh\farelo_vps" -o ExitOnForwardFailure=yes -N -L 3000:127.0.0.1:3000 farelo-tunnel@209.126.6.77
```

Deixar a janela aberta enquanto usar o PDV e abrir
`http://127.0.0.1:3000/login`. Se a porta 3000 do Windows já estiver ocupada,
trocar apenas o primeiro `3000` do argumento `-L` por `3001` e abrir
`http://127.0.0.1:3001/login`. Não copiar a chave privada do Mac para o PC nem
expor a porta 3000 da VPS à internet.

## Fluxo a validar na bancada

1. O acesso inicial com `caixateste` foi confirmado em 24/10. Restabelecer a
   senha de `caixa.farelodebolo` por um fluxo seguro e entrar com esse usuário
   em `/login`; confirmar `/pdv` e a política de acesso às telas
   administrativas. Testar o ADM separadamente, sem anotar as senhas.
2. Abrir uma comanda de teste identificada pela equipe. Adicionar um item do
   cardápio com preço conhecido e confirmar o pedido. Conferir item, preço,
   quantidade, estado do pedido e fila de impressão de cozinha/bar.
3. A Epson TM-T20X da cafeteria está conectada por USB ao Windows, enquanto o
   Edge Agent atual envia ESC/POS somente por TCP. O ensaio pelo diálogo do
   navegador em 24/10 produziu papel com layout incorreto. A opção foi retirada
   do PDV; a impressão física permanece pendente até a integração USB e o
   layout planejado serem validados. Não iniciar o Edge Agent na fila atual
   sem revisar os tickets acumulados. A correção `b73238d` impede que tickets
   de pedidos cancelados sejam entregues ao agente.
4. Registrar o pagamento pelo mesmo meio recebido na maquininha separada.
   Conferir que o valor pago fecha exatamente o total da comanda antes de
   escolher **Fechar comanda**. Não lançar um pagamento de cartão ou PIX sem
   confirmação na maquininha.
5. Conferir o movimento em `/pdv/caixa`, o estado final da comanda e a
   ausência de uma segunda cobrança ou segunda impressão involuntária.

Registrar o número da comanda de teste, horário, versão, resultado de cada
etapa e eventual erro **sem dados pessoais**. Se houver falha, manter o
Sischef disponível como retorno operacional até a correção e novo ensaio.

## Emissão fiscal: porta separada

Este ensaio não gera NFC-e. Em 24/10, o banco ainda tinha zero emitentes,
perfis fiscais, sequências e documentos, e o A1 não estava na VPS. Um teste
fiscal posterior deve usar **homologação**, com emitente, classificação de
todos os itens da comanda, série/número, CSC, pacote XSD e custódia do A1
conferidos antes de qualquer transmissão. Guardar XML, protocolo e resultado
da consulta por chave; comparar total, itens, descontos e meios de pagamento
com a comanda. Só depois de autorização real e conferência do DANFE/QR iniciar
um plano de produção.

As decisões pendentes de Pedro e da contabilidade estão em
`docs/fiscal-pendencias-do-proprietario-20261023.md`; o plano técnico está em
`docs/fiscal-sprint-20261023.md`.
