import type { Order } from "@/lib/api/orders";

const money = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

type BrowserConference = {
  commandNumber: number;
  orders: Order[];
  totalOwed: number;
  totalDiscount: number;
};

// The physical Epson at the cafe is attached to the Windows USB spooler.
// This path uses the browser's print dialog and does not enqueue an Edge
// Agent job, avoiding an accidental duplicate if that agent is installed.
export function printConferenceInBrowser({
  commandNumber,
  orders,
  totalOwed,
  totalDiscount,
}: BrowserConference): boolean {
  const items = orders
    .filter((order) => order.status !== "CANCELLED")
    .flatMap((order) => order.items)
    .filter((item) => !item.cancelled);
  if (items.length === 0) return false;

  const printWindow = window.open("", "_blank", "width=420,height=640");
  if (!printWindow) return false;

  // Only static markup is written. Product names enter the document through
  // textContent, so catalog data cannot inject HTML into the print window.
  printWindow.document.write(`<!doctype html><html lang="pt-BR"><head>
    <meta charset="utf-8"><title>Conferência ${commandNumber}</title>
    <style>
      @page { margin: 4mm; }
      body { width: 72mm; margin: 0 auto; color: #111; font: 12px Arial, sans-serif; }
      h1 { margin: 0 0 5mm; font-size: 17px; text-align: center; }
      h2 { margin: 0; font-size: 14px; }
      .muted { color: #555; }
      .line { border-top: 1px dashed #777; margin: 3mm 0; }
      .row { display: flex; justify-content: space-between; gap: 3mm; margin: 2mm 0; }
      .row span:first-child { flex: 1; overflow-wrap: anywhere; }
      .row span:last-child { white-space: nowrap; }
      .total { font-size: 15px; font-weight: bold; }
      footer { margin-top: 5mm; text-align: center; font-size: 10px; }
    </style></head><body><main id="receipt"></main></body></html>`);
  printWindow.document.close();

  const doc = printWindow.document;
  const receipt = doc.getElementById("receipt");
  if (!receipt) return false;

  const addText = (tag: string, content: string, className?: string) => {
    const element = doc.createElement(tag);
    element.textContent = content;
    if (className) element.className = className;
    receipt.append(element);
  };
  const addRow = (label: string, value: string, className?: string) => {
    const row = doc.createElement("div");
    row.className = `row${className ? ` ${className}` : ""}`;
    for (const content of [label, value]) {
      const part = doc.createElement("span");
      part.textContent = content;
      row.append(part);
    }
    receipt.append(row);
  };

  addText("h1", "Farelo de Bolo");
  addText("h2", `Conferência · Comanda ${commandNumber}`);
  addText(
    "p",
    new Intl.DateTimeFormat("pt-BR", {
      dateStyle: "short",
      timeStyle: "short",
      timeZone: "America/Sao_Paulo",
    }).format(new Date()),
    "muted",
  );
  addText("div", "", "line");
  for (const item of items) {
    addRow(
      `${item.quantity} × ${item.productName}`,
      money.format(item.unitPrice * item.quantity),
    );
  }
  addText("div", "", "line");
  if (totalDiscount > 0) {
    addRow("Desconto", `−${money.format(totalDiscount)}`);
  }
  addRow("TOTAL", money.format(totalOwed), "total");
  addText("footer", "Conferência para pagamento · Não é documento fiscal");

  printWindow.focus();
  printWindow.setTimeout(() => printWindow.print(), 150);
  return true;
}
