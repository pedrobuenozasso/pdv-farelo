# Ensaio presencial do PDV — cafeteria

Preparado em 24/10/2026 para validar a versão privada `f522695` da VPS. O
roteiro usa uma comanda escolhida pela equipe e valores reais conferidos na
tela. Não registrar dados de cliente, senha, comprovante de cartão ou chave
privada neste arquivo.

## Acesso a partir do Windows da cafeteria

A Web da VPS escuta apenas em `127.0.0.1:3000` do servidor. Para acessá-la do
Windows sem torná-la pública, criar uma chave SSH própria no PC e instalar
**somente a chave pública** em uma conta restrita a encaminhar a porta 3000.
Conferir a impressão digital ED25519 do servidor antes de aceitar o primeiro
acesso: `SHA256:s4+iJTV1NdyIIO9UyUQSRzAq+i2JmJD/CFHe5uFPFPw`. Depois de
instalada a chave pública e testada a conta, usar um túnel local e abrir
`http://127.0.0.1:3000/login`. Não copiar a chave privada do Mac para o PC nem
expor a porta 3000 à internet.

## Fluxo a validar na bancada

1. Entrar em `/login` com o usuário `caixa.farelodebolo`. Confirmar que o
   operador chega a `/pdv` e que telas administrativas seguem a política de
   acesso esperada. Testar o ADM separadamente, sem anotar as senhas.
2. Abrir uma comanda de teste identificada pela equipe. Adicionar um item do
   cardápio com preço conhecido e confirmar o pedido. Conferir item, preço,
   quantidade, estado do pedido e fila de impressão de cozinha/bar.
3. Usar **Imprimir Conferência** no PDV. Conferir no papel nome, quantidade,
   preços, desconto se houver e total. Verificar também a fila de impressão
   em `/admin/print-jobs` com o ADM caso a impressora não responda.
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
