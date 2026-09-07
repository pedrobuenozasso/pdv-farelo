/**
 * Formatação do ticket em modo gráfico (ADR-003): em vez de mandar
 * caracteres crus pra impressora desenhar com a fonte de fábrica dela (modo
 * texto, `escpos.ts` — mantido no repositório, mas não mais usado por
 * `poller.ts`), este módulo desenha o ticket inteiro como uma imagem
 * (`@napi-rs/canvas`, fonte do sistema) e manda os bytes já prontos via
 * comando ESC/POS de imagem raster (`GS v 0`). Ver ADR-003 para o porquê
 * completo — resumo: o Pedro comparou lado a lado com o cupom do Sischef
 * (que já imprime em modo gráfico) e pediu a mesma fonte/legibilidade, algo
 * que o modo texto não consegue oferecer (a fonte vem gravada na ROM da
 * impressora, não é trocável via comando).
 *
 * Duas partes deliberadamente separadas:
 *
 * 1. `layoutKitchenTicket`/`layoutCommandCheckTicket`/`drawTicket` — desenham
 *    com `@napi-rs/canvas` e devolvem os pixels crus (RGBA). Não são
 *    testados byte-a-byte (renderização de fonte não é uma garantia
 *    razoável de testar assim — mesma postura já adotada no README.md pra
 *    "sem impressora física disponível neste ambiente"): a prova real é
 *    visual, contra a impressora física.
 * 2. `buildRasterImageCommand` — função pura (pixels -> bytes ESC/POS), essa
 *    sim testável com um bitmap sintético pequeno (ver
 *    `ticketImage.test.ts`), no mesmo espírito de `escpos.test.ts` testar
 *    comandos de controle exatos nas posições certas.
 */

import { createCanvas, type SKRSContext2D } from "@napi-rs/canvas";

import type { CommandCheckContent, PrintJobContent } from "./printJobsClient.js";

const ESC = 0x1b;
const GS = 0x1d;
const LF = 0x0a;

const INIT = Buffer.from([ESC, 0x40]);
const CUT = Buffer.from([GS, 0x56, 0x00]);

/**
 * Largura de impressão em pontos (dots), não em pixels de tela: 576 dots é
 * o valor padrão documentado pra família Epson TM-T em papel de 80mm (72mm
 * de área imprimível a ~8 dots/mm / 203dpi) — mesma ressalva de
 * "não validado contra impressora física de verdade neste ambiente" que
 * `escpos.ts` já registrava pra tabela de código; se o papel real for de
 * 58mm, ajustar esta constante (e MARGIN_DOTS proporcionalmente) é o único
 * lugar a mudar. Precisa ser múltiplo de 8 — `GS v 0` empacota 8 pixels por
 * byte.
 */
const WIDTH_DOTS = 576;
const MARGIN_DOTS = 20;
const CONTENT_WIDTH_DOTS = WIDTH_DOTS - MARGIN_DOTS * 2;

const NOT_INFORMED = "(não informado)";

// Tamanhos de fonte em dots (== pixels do canvas, 1 dot = 1 pixel na
// resolucao nativa da impressora) — calibrados visualmente contra a
// impressora fisica (ver README.md), nao um valor arbitrario de tela.
const FONT_HEADER = "bold 46px Arial";
const FONT_LABEL = "bold 32px Arial";
const FONT_BODY = "bold 32px Arial";
const FONT_TOTAL = "bold 40px Arial";

const LINE_HEIGHT_HEADER = 58;
const LINE_HEIGHT_LABEL = 42;
const LINE_HEIGHT_BODY = 42;
const LINE_HEIGHT_TOTAL = 52;
const SECTION_GAP = 22;
const TOP_MARGIN = 24;
const BOTTOM_MARGIN = 24;

type Align = "left" | "center";

// Uma linha de texto ja pronta pra desenhar — a etapa de "layout" (medir
// texto, quebrar linha, decidir posicao Y) roda inteira antes de qualquer
// desenho, pra saber a altura final do canvas antes de cria-lo
// (`@napi-rs/canvas`, como qualquer canvas, precisa de altura fixa na
// criacao).
type PreparedLine = {
  text: string;
  y: number;
  font: string;
  align: Align;
  // So usado pela conferencia: preco alinhado a direita na mesma linha do
  // nome do item. Um campo dedicado (em vez de tentar detectar isso a
  // partir de `text`, que teria espaco em praticamente toda linha do
  // ticket, nao so nas de preco).
  rightText?: string;
};

/** Quebra `text` em linhas que cabem em `maxWidth`, sem cortar palavra ao meio. */
function wrapText(ctx: SKRSContext2D, text: string, font: string, maxWidth: number): string[] {
  ctx.font = font;
  const words = text.split(" ");
  const lines: string[] = [];
  let current = "";

  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (current && ctx.measureText(candidate).width > maxWidth) {
      lines.push(current);
      current = word;
    } else {
      current = candidate;
    }
  }
  if (current) lines.push(current);
  return lines;
}

function stationLabel(productionStation: string | null): string {
  return productionStation ? `Estação: ${productionStation}` : "Estação: (sem estação)";
}

function formatOrderTime(orderCreatedAt: string): string {
  return new Date(orderCreatedAt).toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function money(value: number): string {
  return value.toFixed(2).replace(".", ",");
}

/**
 * Monta o layout do ticket de PEDIDO (sem precos) — mesmo conteudo de
 * `escpos.ts#buildEscPosTicket`, mas em linhas de canvas em vez de bytes de
 * texto. `ctx` aqui e so usado pra medir texto (`measureText`), nunca pra
 * desenhar — desenhar acontece depois, em `drawTicket`.
 */
function layoutKitchenTicket(ctx: SKRSContext2D, content: PrintJobContent): PreparedLine[] {
  const lines: PreparedLine[] = [];
  let y = TOP_MARGIN;

  function push(text: string, font: string, align: Align, lineHeight: number): void {
    y += lineHeight;
    lines.push({ text, y, font, align });
  }

  // "FARELO DE BOLO" grande no topo (marca) — "COMANDA N" vem menor,
  // abaixo, como identificador secundário (pedido do Pedro: o nome do
  // café é o maior destaque, não o número da comanda).
  push("Farelo de Bolo", FONT_HEADER, "center", LINE_HEIGHT_HEADER);
  push(`Comanda ${content.commandNumber}`, FONT_LABEL, "center", LINE_HEIGHT_LABEL);
  y += SECTION_GAP / 2;
  push(stationLabel(content.productionStation), FONT_LABEL, "left", LINE_HEIGHT_LABEL);
  y += SECTION_GAP;

  push(`Cliente: ${content.customerName ?? NOT_INFORMED}`, FONT_BODY, "left", LINE_HEIGHT_BODY);
  push(`Fone: ${content.customerPhone ?? NOT_INFORMED}`, FONT_BODY, "left", LINE_HEIGHT_BODY);
  y += SECTION_GAP;

  push("ITENS", FONT_LABEL, "center", LINE_HEIGHT_LABEL);
  if (content.items.length === 0) {
    push("(sem itens)", FONT_BODY, "left", LINE_HEIGHT_BODY);
  } else {
    for (const item of content.items) {
      const wrapped = wrapText(
        ctx,
        `${item.quantity}x ${item.productName}`,
        FONT_BODY,
        CONTENT_WIDTH_DOTS,
      );
      for (const wrappedLine of wrapped) {
        push(wrappedLine, FONT_BODY, "left", LINE_HEIGHT_BODY);
      }
    }
  }
  y += SECTION_GAP;

  push(`Pedido às ${formatOrderTime(content.orderCreatedAt)}`, FONT_BODY, "left", LINE_HEIGHT_BODY);
  push(`Cliente: ${content.customerName ?? NOT_INFORMED}`, FONT_BODY, "left", LINE_HEIGHT_BODY);
  y += SECTION_GAP;

  // Rodapé centralizado, mesmo destaque visual do "ITENS"/header — pedido
  // do Pedro: fecha o cupom repetindo o nome do cliente e a marca do café.
  push(
    `Cliente Original: ${content.customerName ?? NOT_INFORMED}`,
    FONT_LABEL,
    "center",
    LINE_HEIGHT_LABEL,
  );
  push("Farelo de Bolo", FONT_HEADER, "center", LINE_HEIGHT_HEADER);

  return lines;
}

/**
 * Monta o layout da CONFERENCIA (com preco por item e total) — mesmo
 * conteudo de `escpos.ts#buildCommandCheckEscPosTicket`. Preco alinhado a
 * direita na mesma linha do nome do item (ou na ultima linha, se o nome
 * precisar quebrar) via `ctx.measureText` do preco.
 */
function layoutCommandCheckTicket(
  ctx: SKRSContext2D,
  content: CommandCheckContent,
): PreparedLine[] {
  const lines: PreparedLine[] = [];
  let y = TOP_MARGIN;

  function push(
    text: string,
    font: string,
    align: Align,
    lineHeight: number,
    rightText?: string,
  ): void {
    y += lineHeight;
    lines.push({ text, y, font, align, rightText });
  }

  push("Conferência", FONT_HEADER, "center", LINE_HEIGHT_HEADER);
  push(`Comanda ${content.commandNumber}`, FONT_LABEL, "center", LINE_HEIGHT_LABEL);
  y += SECTION_GAP;

  if (content.items.length === 0) {
    push("(sem itens)", FONT_BODY, "left", LINE_HEIGHT_BODY);
  } else {
    for (const item of content.items) {
      const priceText = money(item.lineTotal);
      ctx.font = FONT_BODY;
      const priceWidth = ctx.measureText(priceText).width;
      const nameMaxWidth = CONTENT_WIDTH_DOTS - priceWidth - 16;

      const wrapped = wrapText(
        ctx,
        `${item.quantity}x ${item.productName}`,
        FONT_BODY,
        nameMaxWidth,
      );
      const lastIndex = wrapped.length - 1;
      wrapped.forEach((wrappedLine, index) => {
        // Preco so na ultima linha do item — se o nome nao quebrou, e a
        // unica linha mesmo.
        push(
          wrappedLine,
          FONT_BODY,
          "left",
          LINE_HEIGHT_BODY,
          index === lastIndex ? priceText : undefined,
        );
      });
    }
  }

  y += SECTION_GAP;
  push(`TOTAL: R$ ${money(content.total)}`, FONT_TOTAL, "center", LINE_HEIGHT_TOTAL);

  return lines;
}

/**
 * Desenha `lines` (ja com posicao Y calculada) num canvas de
 * `WIDTH_DOTS` x `height`, fundo branco (essencial — canvas nasce
 * transparente, e pixel transparente nao pode virar "preto" na hora de
 * binarizar). `line.rightText`, quando presente, e desenhado alinhado a
 * direita na mesma linha (usado pela conferencia).
 */
function drawTicket(lines: PreparedLine[], height: number) {
  const canvas = createCanvas(WIDTH_DOTS, height);
  const ctx = canvas.getContext("2d");

  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, WIDTH_DOTS, height);
  ctx.fillStyle = "#000000";
  ctx.textBaseline = "alphabetic";

  for (const line of lines) {
    ctx.font = line.font;

    if (line.align === "center") {
      ctx.textAlign = "center";
      ctx.fillText(line.text, WIDTH_DOTS / 2, line.y);
    } else {
      ctx.textAlign = "left";
      ctx.fillText(line.text, MARGIN_DOTS, line.y);
    }

    if (line.rightText) {
      ctx.textAlign = "right";
      ctx.fillText(line.rightText, WIDTH_DOTS - MARGIN_DOTS, line.y);
    }
  }

  return ctx.getImageData(0, 0, WIDTH_DOTS, height);
}

/**
 * Converte pixels RGBA (`ImageData`-like) em bytes ESC/POS de imagem
 * raster (`GS v 0`, ver especificacao Epson) — funcao pura, testavel com
 * um bitmap pequeno sintetico (ver `ticketImage.test.ts`). Binariza por
 * luminancia (< 128 vira ponto preto) — texto desenhado em preto puro
 * sobre fundo branco so produz cinza nas bordas anti-aliased da fonte, e o
 * corte em 128 fica no meio do caminho perceptivel dessa transicao.
 *
 * `widthDots` precisa ser multiplo de 8 (empacotamento de 8 pixels por
 * byte, MSB primeiro) — `WIDTH_DOTS` acima ja garante isso; esta funcao
 * lanca se receber algo que nao seja.
 */
export function buildRasterImageCommand(
  pixels: Uint8ClampedArray,
  widthDots: number,
  heightDots: number,
): Buffer {
  if (widthDots % 8 !== 0) {
    throw new Error(`widthDots deve ser multiplo de 8 (recebido ${widthDots})`);
  }

  const widthBytes = widthDots / 8;
  const imageBytes = Buffer.alloc(widthBytes * heightDots);

  for (let row = 0; row < heightDots; row++) {
    for (let col = 0; col < widthDots; col++) {
      const pixelIndex = (row * widthDots + col) * 4;
      const r = pixels[pixelIndex]!;
      const g = pixels[pixelIndex + 1]!;
      const b = pixels[pixelIndex + 2]!;
      const luminance = (r + g + b) / 3;

      if (luminance < 128) {
        const byteIndex = row * widthBytes + Math.floor(col / 8);
        const bitInByte = 7 - (col % 8);
        imageBytes[byteIndex] = imageBytes[byteIndex]! | (1 << bitInByte);
      }
    }
  }

  const header = Buffer.from([
    GS,
    0x76,
    0x30, // GS v 0
    0x00, // m = 0 (modo normal)
    widthBytes & 0xff,
    (widthBytes >> 8) & 0xff,
    heightDots & 0xff,
    (heightDots >> 8) & 0xff,
  ]);

  return Buffer.concat([header, imageBytes]);
}

function finalHeight(lines: PreparedLine[]): number {
  const lastY = lines.length > 0 ? lines[lines.length - 1]!.y : TOP_MARGIN;
  return lastY + BOTTOM_MARGIN;
}

// Canvas descartavel so pra medir texto durante o layout (measureText nao
// depende da altura do canvas) — evita criar dois canvases full-size.
function measuringContext(): SKRSContext2D {
  return createCanvas(WIDTH_DOTS, 10).getContext("2d");
}

/** Ticket de PEDIDO (sem precos) em modo grafico — substitui `escpos.ts#buildEscPosTicket` em `poller.ts`. */
export function buildEscPosImageTicket(content: PrintJobContent): Buffer {
  const measureCtx = measuringContext();
  const lines = layoutKitchenTicket(measureCtx, content);
  const imageData = drawTicket(lines, finalHeight(lines));
  const raster = buildRasterImageCommand(imageData.data, WIDTH_DOTS, imageData.height);

  return Buffer.concat([INIT, raster, Buffer.from([LF, LF, LF, LF]), CUT]);
}

/** Conferencia (com precos e total) em modo grafico — substitui `escpos.ts#buildCommandCheckEscPosTicket` em `poller.ts`. */
export function buildCommandCheckEscPosImageTicket(content: CommandCheckContent): Buffer {
  const measureCtx = measuringContext();
  const lines = layoutCommandCheckTicket(measureCtx, content);
  const imageData = drawTicket(lines, finalHeight(lines));
  const raster = buildRasterImageCommand(imageData.data, WIDTH_DOTS, imageData.height);

  return Buffer.concat([INIT, raster, Buffer.from([LF, LF, LF, LF]), CUT]);
}
