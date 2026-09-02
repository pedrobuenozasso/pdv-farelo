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
// markOrderPreparing/markOrderReady — first frontend screen to call either
// transition endpoint, closing the "pedido → KDS → preparado → READY"
// milestone from the prompt mestre (docs/architecture.md, roadmap #1).

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiErrorMessage } from "@/lib/api/client";
import {
  listKitchenQueue,
  markOrderPreparing,
  markOrderReady,
  type Order,
} from "@/lib/api/orders";

const QUEUE_QUERY_KEY = ["kds", "queue"];

// The kitchen screen is expected to stay open/unattended indefinitely — poll
// every few seconds instead of waiting for a manual refresh (unlike /pdv).
const REFETCH_INTERVAL_MS = 5_000;

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

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 p-6">
      <div>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">KDS</p>
        <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
          Fila da cozinha
        </h1>
      </div>

      {queueQuery.isLoading ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Carregando...
        </p>
      ) : null}
      {queueQuery.isError ? (
        <p className="text-sm text-red-600 dark:text-red-400">
          Não foi possível carregar a fila.
        </p>
      ) : null}
      {queueQuery.data && queueQuery.data.length === 0 ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Nenhum pedido na fila.
        </p>
      ) : null}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {queueQuery.data?.map((order) => (
          <QueueOrderCard key={order.id} order={order} />
        ))}
      </div>
    </main>
  );
}

function QueueOrderCard({ order }: { order: Order }) {
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

  return (
    <div className="flex flex-col gap-2 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">
          Comanda {order.commandNumber}
        </h2>
        <span className="text-xs font-medium text-zinc-500 dark:text-zinc-400">
          {formatElapsed(order.createdAt)}
        </span>
      </div>

      {/* Sem preço aqui de propósito: tela de cozinha, staff só precisa
          saber o que preparar e quanto de cada item — não quanto custa
          (diferente do OrderCard do /pdv, que mostra subtotal em
          dinheiro para o atendente). */}
      <ul className="flex flex-col gap-0.5">
        {order.items.map((item) => (
          <li key={item.id} className="text-sm text-black dark:text-zinc-50">
            {item.quantity}× {item.productName}
          </li>
        ))}
      </ul>

      <div className="mt-2 flex flex-col gap-1">
        {canStartPreparing ? (
          <button
            type="button"
            disabled={preparingMutation.isPending}
            onClick={() => preparingMutation.mutate()}
            className="rounded bg-black px-3 py-1.5 text-sm font-medium text-white disabled:opacity-40 dark:bg-white dark:text-black"
          >
            {preparingMutation.isPending ? "Iniciando..." : "Iniciar preparo"}
          </button>
        ) : null}
        {canMarkReady ? (
          <button
            type="button"
            disabled={readyMutation.isPending}
            onClick={() => readyMutation.mutate()}
            className="rounded bg-black px-3 py-1.5 text-sm font-medium text-white disabled:opacity-40 dark:bg-white dark:text-black"
          >
            {readyMutation.isPending ? "Marcando..." : "Marcar como pronto"}
          </button>
        ) : null}
        {preparingError ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            {preparingError}
          </p>
        ) : null}
        {readyError ? (
          <p className="text-sm text-red-600 dark:text-red-400">{readyError}</p>
        ) : null}
      </div>
    </div>
  );
}

// "Há quanto tempo" o pedido está na fila, em minutos desde createdAt —
// diferente do OrderCard do /pdv (dateTimeFormatter, horário absoluto): o
// que importa para a cozinha é a duração da espera, não o relógio. Uma
// diferença entre dois instantes não depende de fuso horário (é apenas uma
// subtração de timestamps), então não há conversão para America/Sao_Paulo
// a fazer aqui — só formatação relativa. Recalculada a cada render; como a
// tela já re-renderiza a cada poll (REFETCH_INTERVAL_MS), isso é suficiente
// sem precisar de um timer próprio.
function formatElapsed(createdAt: string): string {
  const elapsedMs = Date.now() - new Date(createdAt).getTime();
  const elapsedMinutes = Math.max(0, Math.floor(elapsedMs / 60_000));
  if (elapsedMinutes < 60) {
    return `${elapsedMinutes} min`;
  }
  const hours = Math.floor(elapsedMinutes / 60);
  const minutes = elapsedMinutes % 60;
  return `${hours}h ${minutes}min`;
}
