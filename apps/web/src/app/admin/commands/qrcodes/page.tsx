"use client";

// FARELO-250/251/252/253 — QR codes for the 1-100 seeded comandas.
//
// Each printed URL includes the current visit's random QR token. Reprint
// after reopening a comanda: the next visit gets a new token.
//
// FARELO-251 ("Tela Admin de QR Codes"): this page, listing all 100.
//
// FARELO-252 ("Download individual"): SVG preferred (crisp at any print
// size, and this is what's on screen already — no re-render needed),
// PNG offered too via a hidden QRCodeCanvas per card.
//
// FARELO-253 ("Página de impressão em lote"): no separate route — a
// print-only grid (Tailwind's `print:` variant) renders alongside the
// normal screen UI in the same page; `window.print()` triggers the
// browser's own print dialog, same convention used for the printer-
// physical-ticket flow elsewhere in this app (that one goes through
// PrintJob/Edge Agent instead, since it targets a thermal receipt printer
// unattended — this one is a one-off admin action printing to whatever
// printer the browser dialog offers, so the native dialog is the right
// tool, not a new PrintJob type).

import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { QRCodeCanvas, QRCodeSVG } from "qrcode.react";
import { useQuery } from "@tanstack/react-query";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Button } from "@/components/ui/button";
import { LogoBadge } from "@/components/logo-badge";
import { useOrigin } from "@/lib/use-origin";
import { listCommandQrTokens, type CommandQr } from "@/lib/api/command-qr";

function menuUrl(origin: string, number: number, qrToken: string): string {
  const path = process.env.NEXT_PUBLIC_MENU_ORIGIN
    ? `/comanda${number}`
    : `/c/${number}`;
  return `${origin}${path}?qr=${encodeURIComponent(qrToken)}`;
}

export default function CommandQrCodesAdminPage() {
  const currentOrigin = useOrigin();
  // Public QR codes must keep working when Admin lives behind a separate
  // protected hostname. Local development keeps using its current origin.
  const origin = process.env.NEXT_PUBLIC_MENU_ORIGIN || currentOrigin;
  const qrQuery = useQuery({
    queryKey: ["commands", "qr"],
    queryFn: listCommandQrTokens,
  });
  const codes = qrQuery.data ?? [];
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [printCodes, setPrintCodes] = useState<CommandQr[]>([]);
  const [rangeFrom, setRangeFrom] = useState("1");
  const [rangeTo, setRangeTo] = useState("100");

  function toggle(number: number) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(number)) {
        next.delete(number);
      } else {
        next.add(number);
      }
      return next;
    });
  }

  function selectRange() {
    const from = Math.max(1, Number(rangeFrom) || 1);
    const to = Math.min(100, Number(rangeTo) || 100);
    if (from > to) return;
    const next = new Set(selected);
    for (let n = from; n <= to; n++) next.add(n);
    setSelected(next);
  }

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <div className="flex flex-col gap-6 print:hidden">
          <div>
            <h1 className="font-serif text-2xl font-semibold">
              QR Codes das comandas
            </h1>
            <p className="text-ink-soft mt-0.5 text-sm">
              {selected.size} de 100 selecionadas para impressão
            </p>
            <p className="text-ink-soft mt-1 text-sm">
              Reimprima o QR da comanda após cada novo atendimento. O código
              anterior deixa de autorizar pedidos.
            </p>
          </div>

          <div className="border-line bg-surface flex flex-wrap items-end gap-3 rounded-2xl border p-4">
            <div className="flex flex-col gap-1">
              <label className="text-ink text-xs font-medium">
                Selecionar intervalo
              </label>
              <div className="flex items-center gap-2">
                <input
                  inputMode="numeric"
                  value={rangeFrom}
                  onChange={(event) => setRangeFrom(event.target.value)}
                  className="border-line bg-bg w-16 rounded-lg border px-2 py-1.5 text-sm outline-none"
                />
                <span className="text-ink-soft text-sm">até</span>
                <input
                  inputMode="numeric"
                  value={rangeTo}
                  onChange={(event) => setRangeTo(event.target.value)}
                  className="border-line bg-bg w-16 rounded-lg border px-2 py-1.5 text-sm outline-none"
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={selectRange}
                  className="px-3 py-1.5 text-[13px]"
                >
                  Adicionar
                </Button>
              </div>
            </div>
            <Button
              type="button"
              variant="outline"
              onClick={() =>
                setSelected(new Set(codes.map((code) => code.number)))
              }
              className="px-3 py-1.5 text-[13px]"
            >
              Selecionar todas
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => setSelected(new Set())}
              className="px-3 py-1.5 text-[13px]"
            >
              Limpar seleção
            </Button>
            <Button
              type="button"
              disabled={
                selected.size === 0 || qrQuery.isFetching || qrQuery.isError
              }
              onClick={async () => {
                const latest = await qrQuery.refetch();
                if (latest.isSuccess && latest.data) {
                  flushSync(() => setPrintCodes(latest.data));
                  window.print();
                }
              }}
              className="ml-auto px-4 py-2 text-[13px]"
            >
              Imprimir selecionados
            </Button>
          </div>

          {qrQuery.isPending && <p>Carregando QR Codes...</p>}
          {qrQuery.isError && <p>Não foi possível carregar os QR Codes.</p>}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {codes.map(({ number, qrToken }) => (
              <QrCodeCard
                key={number}
                number={number}
                url={menuUrl(origin, number, qrToken)}
                selected={selected.has(number)}
                onToggle={() => toggle(number)}
              />
            ))}
          </div>
        </div>

        {/* FARELO-253: print-only — hidden on screen, shown only inside
            the browser's print output (window.print() above). */}
        <div className="hidden print:block">
          <div className="grid grid-cols-3 gap-6">
            {printCodes
              .filter(({ number }) => selected.has(number))
              .map(({ number, qrToken }) => (
                <div
                  key={number}
                  className="flex break-inside-avoid flex-col items-center gap-2 border border-dashed border-black p-4"
                >
                  <div className="flex items-center gap-1.5">
                    <LogoBadge size={20} />
                    <span className="text-xs font-semibold">Farelo</span>
                  </div>
                  <QRCodeSVG
                    value={menuUrl(origin, number, qrToken)}
                    size={120}
                  />
                  <span className="text-sm font-bold">Comanda {number}</span>
                </div>
              ))}
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function QrCodeCard({
  number,
  url,
  selected,
  onToggle,
}: {
  number: number;
  url: string;
  selected: boolean;
  onToggle: () => void;
}) {
  const svgRef = useRef<SVGSVGElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  function downloadSvg() {
    const svg = svgRef.current;
    if (!svg) return;
    const markup = new XMLSerializer().serializeToString(svg);
    const blob = new Blob([markup], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    triggerDownload(url, `comanda-${number}.svg`);
    URL.revokeObjectURL(url);
  }

  function downloadPng() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    triggerDownload(canvas.toDataURL("image/png"), `comanda-${number}.png`);
  }

  return (
    <div
      className={`border-line bg-surface flex flex-col items-center gap-2 rounded-2xl border p-3 ${
        selected ? "ring-primary ring-2" : ""
      }`}
    >
      <label className="flex w-full items-center gap-1.5 text-xs font-semibold">
        <input type="checkbox" checked={selected} onChange={onToggle} />
        Comanda {number}
      </label>
      <QRCodeSVG ref={svgRef} value={url} size={96} />
      {/* Hidden — only used to produce a PNG on demand (canvas pixels
          render regardless of CSS visibility). */}
      <QRCodeCanvas ref={canvasRef} value={url} size={256} className="hidden" />
      <div className="flex gap-1.5">
        <button
          type="button"
          onClick={downloadSvg}
          className="text-primary text-[11px] font-semibold hover:underline"
        >
          SVG
        </button>
        <span className="text-ink-faint text-[11px]">·</span>
        <button
          type="button"
          onClick={downloadPng}
          className="text-primary text-[11px] font-semibold hover:underline"
        >
          PNG
        </button>
      </div>
    </div>
  );
}

function triggerDownload(href: string, filename: string) {
  const link = document.createElement("a");
  link.href = href;
  link.download = filename;
  link.click();
}
