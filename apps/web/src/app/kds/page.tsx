"use client";

// Client Component — same reasoning as /pdv (app/pdv/page.tsx): the KDS is
// an internal, staff-facing screen, heavy interactivity (polling + per-order
// transition buttons), no SEO/first-paint concern.
//
// Difference from /pdv: /pdv is an attendant clicking around a screen they
// look at on demand — every refetch there is triggered by a mutation
// succeeding. /kds is meant to run unattended on a screen in the kitchen;
// nobody is there to hit refresh, so the query polls itself via
// `refetchInterval` below to notice new orders and orders that left the
// queue (e.g. marked READY from this same screen — the backend's
// GET /api/v1/orders already excludes READY, so it just stops showing up
// on the next poll).
//
// Reuses GET /api/v1/orders (FARELO-059) via listKitchenQueue, and the
// POST .../preparing / .../ready transitions (FARELO-057/058) via
// markOrderPreparing/markOrderReady.
//
// Visual: fixed DARK surface, independent of the rest of the app's warm
// cream theme (see globals.css's comment on why) and independent of
// system light/dark preference — a kitchen display needs to stay
// distance-legible and high-contrast under bright kitchen lighting at all
// times, not follow a toggle. No InternalNav here either: this screen
// runs unattended on its own display, so it stays distraction-free
// (matches the design canvas shared with the user).

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type CSSProperties } from "react";

import { AuthGuard } from "@/components/auth-guard";
import { apiErrorMessage } from "@/lib/api/client";
import {
  listKitchenQueue,
  markOrderPreparing,
  markOrderReady,
  type Order,
} from "@/lib/api/orders";
import { cn } from "@/lib/cn";
import { useNowSeconds } from "@/lib/clock";

const QUEUE_QUERY_KEY = ["kds", "queue"];

// The kitchen screen is expected to stay open/unattended indefinitely — poll
// every few seconds instead of waiting for a manual refresh (unlike /pdv).
const REFETCH_INTERVAL_MS = 5_000;

const KDS_VARS = {
  "--kds-bg": "oklch(20% 0.018 50)",
  "--kds-surface": "oklch(26% 0.022 50)",
  "--kds-ink": "oklch(96% 0.01 70)",
  "--kds-ink-soft": "oklch(76% 0.02 60)",
  "--kds-ink-faint": "oklch(58% 0.02 55)",
  "--kds-line": "oklch(40% 0.02 50)",
  "--kds-primary": "oklch(72% 0.14 50)",
  "--kds-primary-ink": "oklch(18% 0.02 50)",
  "--kds-green": "oklch(72% 0.14 150)",
  "--kds-amber": "oklch(76% 0.14 80)",
  "--kds-red": "oklch(68% 0.18 25)",
} as CSSProperties;

export default function KdsPage() {
  const queueQuery = useQuery({
    queryKey: QUEUE_QUERY_KEY,
    queryFn: listKitchenQueue,
    refetchInterval: REFETCH_INTERVAL_MS,
    // Keep polling even if the tab loses focus/visibility — this is meant
    // to run on a fixed display, not a tab a person tabs in and out of
    // (TanStack Query pauses background polling by default).
    refetchIntervalInBackground: true,
  });

  const nowSeconds = useNowSeconds();
  const clock = nowSeconds > 0 ? formatClock(new Date(nowSeconds * 1_000)) : "";

  return (
    <AuthGuard>
      <main
        style={KDS_VARS}
        className="min-h-screen bg-[var(--kds-bg)] px-8 py-6 text-[var(--kds-ink)]"
      >
        <div className="mb-6 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--kds-primary)] font-serif text-sm font-semibold text-[var(--kds-primary-ink)] italic">
              FB
            </span>
            <h1 className="font-serif text-2xl font-semibold">
              Fila da cozinha
            </h1>
          </div>
          <div className="font-mono text-xl font-bold text-[var(--kds-ink-soft)] tabular-nums">
            {clock}
          </div>
        </div>

        {queueQuery.isLoading ? (
          <p className="text-sm text-[var(--kds-ink-faint)]">Carregando...</p>
        ) : null}
        {queueQuery.isError ? (
          <p className="text-sm text-[var(--kds-red)]">
            Não foi possível carregar a fila.
          </p>
        ) : null}
        {queueQuery.data && queueQuery.data.length === 0 ? (
          <p className="text-sm text-[var(--kds-ink-faint)]">
            Nenhum pedido na fila.
          </p>
        ) : null}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {queueQuery.data?.map((order) => (
            <QueueOrderCard
              key={order.id}
              order={order}
              nowSeconds={nowSeconds}
            />
          ))}
        </div>
      </main>
    </AuthGuard>
  );
}

function formatClock(date: Date): string {
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function QueueOrderCard({
  order,
  nowSeconds,
}: {
  order: Order;
  nowSeconds: number;
}) {
  const queryClient = useQueryClient();

  const preparingMutation = useMutation({
    mutationFn: () => markOrderPreparing(order.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUEUE_QUERY_KEY });
    },
  });

  const readyMutation = useMutation({
    mutationFn: () => markOrderReady(order.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUEUE_QUERY_KEY });
    },
  });

  // CREATED and CONFIRMED both show the "iniciar preparo" action, per the
  // ticket's spec of GET /api/v1/orders's own status set. In practice only
  // CREATED reaches PREPARING today (docs/api.md: the backend's /preparing
  // rejects anything but CREATED, and nothing in the product yet creates a
  // CONFIRMED order) — CONFIRMED is a reserved OrderStatus value without a
  // producer. The button covers it anyway so the KDS doesn't need a change
  // the day something starts creating CONFIRMED orders.
  const canStartPreparing =
    order.status === "CREATED" || order.status === "CONFIRMED";
  const canMarkReady = order.status === "PREPARING";

  const preparingError = apiErrorMessage(
    preparingMutation.error,
    "Não foi possível iniciar o preparo.",
  );
  const readyError = apiErrorMessage(
    readyMutation.error,
    "Não foi possível marcar como pronto.",
  );

  const elapsedMinutes = Math.max(
    0,
    Math.floor(
      (nowSeconds * 1_000 - new Date(order.createdAt).getTime()) / 60_000,
    ),
  );
  const urgency: "green" | "amber" | "red" =
    elapsedMinutes >= 10 ? "red" : elapsedMinutes >= 5 ? "amber" : "green";
  const urgencyBorder = {
    green: "border-[color-mix(in_oklch,var(--kds-green)_50%,transparent)]",
    amber: "border-[color-mix(in_oklch,var(--kds-amber)_55%,transparent)]",
    red: "border-[color-mix(in_oklch,var(--kds-red)_55%,transparent)]",
  }[urgency];
  const urgencyBadge = {
    green:
      "bg-[color-mix(in_oklch,var(--kds-green)_20%,transparent)] text-[var(--kds-green)]",
    amber:
      "bg-[color-mix(in_oklch,var(--kds-amber)_22%,transparent)] text-[var(--kds-amber)]",
    red: "bg-[color-mix(in_oklch,var(--kds-red)_20%,transparent)] text-[var(--kds-red)]",
  }[urgency];

  return (
    <div
      className={cn(
        "flex flex-col gap-3 rounded-2xl border-[1.5px] bg-[var(--kds-surface)] p-5",
        urgencyBorder,
      )}
    >
      <div className="flex items-start justify-between">
        <h2 className="font-serif text-2xl font-semibold">
          Comanda {order.commandNumber}
        </h2>
        <span
          className={cn(
            "rounded-full px-3 py-1 text-[13px] font-extrabold",
            urgencyBadge,
          )}
        >
          {elapsedMinutes < 1 ? "<1 min" : `${elapsedMinutes} min`}
        </span>
      </div>

      {/* Sem preço aqui de propósito: tela de cozinha, staff só precisa
          saber o que preparar e quanto de cada item — não quanto custa
          (diferente do OrderCard do /pdv, que mostra subtotal em
          dinheiro para o atendente). */}
      <ul className="flex flex-col gap-1 border-t border-[var(--kds-line)] pt-2">
        {order.items.map((item) => (
          <li key={item.id} className="text-base font-medium">
            {item.quantity}× {item.productName}
          </li>
        ))}
      </ul>

      <div className="mt-1 flex flex-col gap-2">
        {canStartPreparing ? (
          <button
            type="button"
            disabled={preparingMutation.isPending}
            onClick={() => preparingMutation.mutate()}
            className="flex items-center justify-center gap-2 rounded-xl bg-[var(--kds-primary)] py-3.5 text-[15px] font-extrabold text-[var(--kds-primary-ink)] disabled:opacity-40"
          >
            {preparingMutation.isPending ? "Iniciando..." : "Iniciar preparo"}
          </button>
        ) : null}
        {canMarkReady ? (
          <button
            type="button"
            disabled={readyMutation.isPending}
            onClick={() => readyMutation.mutate()}
            className="flex items-center justify-center gap-2 rounded-xl bg-[var(--kds-primary)] py-3.5 text-[15px] font-extrabold text-[var(--kds-primary-ink)] disabled:opacity-40"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M20 6 9 17l-5-5" />
            </svg>
            {readyMutation.isPending ? "Marcando..." : "Pronto"}
          </button>
        ) : null}
        {preparingError ? (
          <p className="text-sm text-[var(--kds-red)]">{preparingError}</p>
        ) : null}
        {readyError ? (
          <p className="text-sm text-[var(--kds-red)]">{readyError}</p>
        ) : null}
      </div>
    </div>
  );
}
