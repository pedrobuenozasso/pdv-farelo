import type { ReactNode } from "react";

import type { CommandCheckContent, PrintJobContent } from "@/lib/api/print-jobs";

// Pré-visualização HTML do que a impressora térmica realmente produz —
// espelha fielmente `apps/edge-agent/src/ticketImage.ts` (modo gráfico,
// ADR-003): mesmas seções, mesma ordem, sem linhas separadoras (removidas
// a pedido — ficavam "feias" no papel real). Fonte sans-serif em vez de
// monoespaçada aqui de propósito: o ticket real também usa uma fonte
// sans-serif (Arial) renderizada como imagem, não a fonte de máquina de
// escrever do modo texto antigo.

const NOT_INFORMED = "(não informado)";

const timeFormatter = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
});

function stationLabel(productionStation: string | null): string {
  return productionStation ? `Estação: ${productionStation}` : "Estação: (sem estação)";
}

function money(value: number): string {
  return value.toFixed(2).replace(".", ",");
}

// Casca compartilhada: papel estreito, fundo levemente amarelado (imita o
// papel térmico), fonte sans-serif — mesma largura visual nos dois tipos
// de cupom para ficarem comparáveis lado a lado.
function TicketPaper({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-[320px] rounded-sm bg-[#fdfaf2] px-5 py-6 font-sans text-black shadow-[0_1px_3px_rgba(0,0,0,0.15)]">
      {children}
    </div>
  );
}

function Header({ children }: { children: ReactNode }) {
  return <div className="text-center text-xl font-bold">{children}</div>;
}

function Label({ children }: { children: ReactNode }) {
  return <div className="mt-3 text-center text-base font-bold">{children}</div>;
}

function LeftLabel({ children }: { children: ReactNode }) {
  return <div className="mt-1 text-base font-bold">{children}</div>;
}

function Line({ children }: { children: ReactNode }) {
  return <div className="mt-1 text-base leading-snug font-bold">{children}</div>;
}

function Section({ children }: { children: ReactNode }) {
  return <div className="mt-4">{children}</div>;
}

/**
 * Espelha `ticketImage.ts#buildEscPosImageTicket` — o ticket de PEDIDO (sem
 * preços), impresso na estação de produção (cozinha/bar) quando um pedido é
 * criado.
 */
export function KitchenTicketPreview({ content }: { content: PrintJobContent }) {
  return (
    <TicketPaper>
      <Header>Farelo de Bolo</Header>
      <div className="text-center text-base font-bold">Comanda {content.commandNumber}</div>
      <LeftLabel>{stationLabel(content.productionStation)}</LeftLabel>

      <Section>
        <Line>Cliente: {content.customerName ?? NOT_INFORMED}</Line>
        <Line>Fone: {content.customerPhone ?? NOT_INFORMED}</Line>
      </Section>

      <Section>
        <Label>ITENS</Label>
        {content.items.length === 0 ? (
          <Line>(sem itens)</Line>
        ) : (
          content.items.map((item, index) => (
            <Line key={index}>
              {item.quantity}x {item.productName}
            </Line>
          ))
        )}
      </Section>

      <Section>
        <Line>Pedido às {timeFormatter.format(new Date(content.orderCreatedAt))}</Line>
        <Line>Cliente: {content.customerName ?? NOT_INFORMED}</Line>
      </Section>

      <Section>
        <Label>Cliente Original: {content.customerName ?? NOT_INFORMED}</Label>
        <Header>Farelo de Bolo</Header>
      </Section>
    </TicketPaper>
  );
}

/**
 * Espelha `ticketImage.ts#buildCommandCheckEscPosImageTicket` — a
 * "conferência" (com preços e total), impressa no caixa na hora de
 * fechar/conferir a comanda.
 */
export function CommandCheckTicketPreview({ content }: { content: CommandCheckContent }) {
  return (
    <TicketPaper>
      <Header>Conferência</Header>
      <div className="text-center text-base font-bold">Comanda {content.commandNumber}</div>

      <Section>
        {content.items.length === 0 ? (
          <Line>(sem itens)</Line>
        ) : (
          content.items.map((item, index) => (
            <div key={index} className="mt-1 flex justify-between gap-3 text-base leading-snug">
              <span>
                {item.quantity}x {item.productName}
              </span>
              <span className="shrink-0">{money(item.lineTotal)}</span>
            </div>
          ))
        )}
      </Section>

      <div className="mt-4 text-center text-lg font-bold">TOTAL: R$ {money(content.total)}</div>
    </TicketPaper>
  );
}
