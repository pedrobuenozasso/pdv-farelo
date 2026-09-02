/**
 * Formatação ESC/POS do ticket impresso (FARELO-078) — função pura, sem I/O:
 * recebe o `PrintJobContent` já pronto (snapshot montado pelo backend, ver
 * `docs/domain-model.md`, pacote `printing`, FARELO-072/074) e devolve os
 * bytes exatos a enviar para a impressora térmica (`printerTransport.ts`
 * cuida do transporte de rede).
 *
 * **Decisão — comandos ESC/POS escritos à mão, sem lib nova**: ADR-002
 * cogitou `node-thermal-printer`/`escpos` como opção futura, mas o conjunto
 * de comandos necessário aqui é pequeno e bem documentado/padronizado (ESC @
 * para inicializar, ESC ! para negrito/fonte grande, ESC a para alinhamento,
 * GS V para corte de papel) — poucas sequências de bytes fixas, sem
 * necessidade de descobrir modelo/protocolo de impressora em runtime nem de
 * abstrações que uma lib ofereceria (múltiplos protocolos de impressora,
 * detecção de hardware, impressão de imagem/QR code). Seguindo o espírito já
 * registrado em `AGENTS.md`/ADR-002 ("não adicionar dependência sem
 * necessidade real"), a lib não paga o próprio custo aqui: mais uma
 * dependência para manter/atualizar em troca de ~10 linhas de constantes de
 * bytes que não mudam. Sem ADR-003 novo por não haver dependência nova a
 * justificar — ver README.md para o registro dessa decisão.
 *
 * Nenhuma regra de negócio de pedido aqui (mesmo princípio de todo o Edge
 * Agent, seção 11 do prompt mestre): este módulo só formata o que já veio
 * pronto em `content` — não decide quais itens pertencem a qual estação,
 * não consulta preço, não interpreta o pedido.
 */

import type { PrintJobContent } from "./printJobsClient.js";

const ESC = 0x1b;
const GS = 0x1d;
const LF = 0x0a;

/** ESC @ — reseta a impressora para o estado inicial (limpa buffer/formatação residual). */
const INIT = Buffer.from([ESC, 0x40]);

/** ESC a n — alinhamento: 0 = esquerda, 1 = centro. */
const ALIGN_LEFT = Buffer.from([ESC, 0x61, 0x00]);
const ALIGN_CENTER = Buffer.from([ESC, 0x61, 0x01]);

/**
 * ESC ! n — seleciona modo de impressão via máscara de bits. `0x00` = modo
 * normal (usado para restaurar depois do cabeçalho). `0x38` combina três
 * bits: negrito/emphasized (0x08) + fonte com altura dobrada (0x10) + fonte
 * com largura dobrada (0x20) — destaque máximo padrão ESC/POS para o número
 * da comanda, sem depender de um comando de fonte específico de fabricante.
 */
const PRINT_MODE_NORMAL = Buffer.from([ESC, 0x21, 0x00]);
const PRINT_MODE_COMMAND_HIGHLIGHT = Buffer.from([ESC, 0x21, 0x38]);

/**
 * GS V m — corte de papel. `m = 0` (`0x00`) é corte total (guilhotina),
 * padrão suficiente para um ticket de comanda (não precisa do corte
 * parcial, `m = 1`, que deixa uma aba presa).
 */
const CUT = Buffer.from([GS, 0x56, 0x00]);

const SEPARATOR = "-".repeat(32);

/** Converte uma linha de texto (ASCII) + quebra de linha (LF) para bytes. */
function textLine(text: string): Buffer {
  return Buffer.concat([Buffer.from(text, "ascii"), Buffer.from([LF])]);
}

function stationLine(productionStation: string | null): string {
  // Mesma convenção já usada em `formatPrintJobLog` (printJobsClient.ts):
  // "sem estação" é um rótulo explícito, nunca o literal "null" no papel —
  // ver FARELO-074 no domain-model.md sobre itens sem estação atribuída.
  return productionStation
    ? `Estacao: ${productionStation}`
    : "Estacao: (sem estacao)";
}

/**
 * Monta os bytes ESC/POS de um ticket impresso a partir de um
 * `PrintJobContent`: inicialização, número da comanda em destaque
 * (centralizado, negrito, fonte dobrada), estação de produção (ou "sem
 * estação" quando `null`), lista de itens (quantidade + nome) e corte de
 * papel no final.
 *
 * Função pura — sem rede, sem I/O, sem `console.log`. Testável só com
 * asserções sobre os bytes retornados (ver `escpos.test.ts`).
 */
export function buildEscPosTicket(content: PrintJobContent): Buffer {
  const parts: Buffer[] = [
    INIT,
    ALIGN_CENTER,
    PRINT_MODE_COMMAND_HIGHLIGHT,
    textLine(`COMANDA ${content.commandNumber}`),
    PRINT_MODE_NORMAL,
    ALIGN_LEFT,
    textLine(stationLine(content.productionStation)),
    textLine(SEPARATOR),
  ];

  if (content.items.length === 0) {
    parts.push(textLine("(sem itens)"));
  } else {
    for (const item of content.items) {
      parts.push(textLine(`${item.quantity}x ${item.productName}`));
    }
  }

  parts.push(textLine(""));
  parts.push(textLine(""));
  parts.push(CUT);

  return Buffer.concat(parts);
}
