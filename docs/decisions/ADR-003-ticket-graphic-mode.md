# ADR-003: Impressão de cupom em modo gráfico (`@napi-rs/canvas`)

## Status

Aceito

## Contexto

O Edge Agent (`apps/edge-agent`) imprimia o ticket inteiramente em **modo texto**
ESC/POS (`escpos.ts`, FARELO-078): bytes de caracteres crus, desenhados pela
impressora térmica com a fonte gravada na ROM dela (a família de fonte "Font A"
padrão Epson) — só ajustável via os bits de negrito/altura/largura de `ESC !`, sem
nenhuma forma de trocar a tipografia em si.

Validando a impressão real contra a Epson TM-T20X física da cafeteria, o Pedro
comparou lado a lado com o cupom já impresso hoje pelo sistema legado (Sischef) e
pediu explicitamente a mesma fonte/legibilidade — "quero essa mesma letra... quero um
negócio clean, bonito... e grande". O cupom do Sischef usa uma fonte sans-serif limpa
e uniforme, incompatível com qualquer combinação de bold/altura-dobrada disponível no
modo texto: aumentar/engrossar o Font A da ROM da impressora nunca produziria a mesma
tipografia, porque a fonte em si não é a mesma — é uma limitação de hardware/firmware,
não um parâmetro ajustável via software.

A configuração de impressão do próprio Sischef (visível na tela de "Local de
Impressão" do Windows) já expõe essa distinção: "Configurações para modo texto (V1)"
vs. "Configurações modo gráfico (V2)" — confirmando que o cupom bonito que serviu de
referência é, de fato, gerado em modo gráfico (uma imagem já renderizada, enviada
pronta pra impressora), não em modo texto.

## Decisão

Substituir o formatador padrão usado por `poller.ts` (era `escpos.ts`, modo texto)
por um novo módulo, `ticketImage.ts`, que:

1. Desenha o ticket inteiro como uma imagem monocromática usando
   [`@napi-rs/canvas`](https://github.com/Brooooooklyn/canvas) (fonte real do
   sistema — Arial, escolhida por estar disponível tanto em macOS/Linux quanto,
   principalmente, no PC Windows da cafeteria, onde a impressora física está
   conectada por USB).
2. Converte os pixels resultantes em bytes ESC/POS de imagem raster (`GS v 0`,
   comando padrão Epson/ESC-POS pra "raster bit image") — binarizado por luminância
   (pixel escuro vira ponto impresso, pixel claro não).
3. Envia esses bytes pelo mesmo transporte TCP já existente (`printerTransport.ts`,
   inalterado) — a mudança é só *o que* vira bytes, nunca *como* os bytes chegam até
   a impressora.

`escpos.ts` (modo texto) permanece no repositório, testado, mas não é mais usado por
`poller.ts` — histórico do FARELO-078, caso um formatador de texto puro volte a ser
necessário (ex: impressora sem suporte a imagem raster).

### Por que `@napi-rs/canvas` e não outra lib

- **`node-canvas`** (`canvas` no npm): também baseada em Cairo, mas exige toolchain
  nativo (Cairo/Pango/libjpeg) instalado no sistema pra compilar — historicamente
  frágil em setups variados (incluindo o mini PC dedicado da cafeteria, cujo SO/
  dependências não são controladas por este repositório). Rejeitada por esse risco de
  instalação.
- **`@napi-rs/canvas`** (escolhida): mesma API Canvas 2D (compatível o suficiente com
  o código deste módulo), mas distribuída como binário Rust pré-compilado por
  plataforma — `npm install` sem nenhum passo de build nativo, o mesmo "funciona
  direto" que já se espera de qualquer outra dependência deste projeto.

### Por que isso justifica uma dependência nova (ao contrário de `escpos.ts`/
`printerTransport.ts`, que deliberadamente não adicionaram nenhuma)

`escpos.ts`/ADR anteriores documentam a decisão de **não** adicionar
`node-thermal-printer`/`escpos` porque o conjunto de comandos ESC/POS de texto
necessário era pequeno e não precisava de abstração nenhuma. Isso não se aplica aqui:
renderizar texto com uma fonte de verdade (quebra de linha, medição de largura,
antialiasing, rasterização) é trabalho genuinamente complexo que nenhuma
implementação enxuta à mão substitui sem reinventar um motor de fontes — o problema
que este ticket resolve *é* precisamente esse.

## Consequências

- Nova dependência de produção em `apps/edge-agent`: `@napi-rs/canvas`.
- Largura de impressão fixada em 576 dots (72mm imprimíveis, papel 80mm — valor
  padrão documentado pra família Epson TM-T, não validado ainda contra impressora
  58mm; ver ressalva em `ticketImage.ts`).
- A fonte usada (`Arial`) precisa existir no SO onde o Edge Agent roda — presente por
  padrão em Windows (ambiente real da cafeteria) e na maioria das distribuições
  desktop de Linux/macOS; se o mini PC de produção rodar uma imagem headless sem essa
  fonte instalada, é o único ajuste necessário (trocar a constante de fonte ou
  instalar o pacote de fontes correspondente).
- `escpos.ts`/`escpos.test.ts` (modo texto) tornam-se código não utilizado por
  `poller.ts`, mas não foram removidos — permanecem testados e disponíveis caso um
  cenário futuro precise de impressão em texto puro novamente.
