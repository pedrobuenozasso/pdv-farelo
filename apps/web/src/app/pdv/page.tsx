"use client";

// Client component (no SSR need here, same choice as the Admin pages):
// /pdv is an internal, staff-facing tool — heavy interactivity (100
// clickable commands, on-demand detail fetch, open/close actions), no
// SEO/first-paint concern. TanStack Query (already global via
// app/providers.tsx) handles the fetching, same pattern as
// /admin/categories and /admin/products.
//
// Comandas are always numbered 1-100 (fixed, from the seed) — there's no
// GET /api/v1/commands (list-all) endpoint, so the grid is generated here
// instead of inventing one on the backend just for this. If the grid ever
// needs to show every command's status at once (color-coded, say), a
// listing endpoint becomes the natural next step — not needed yet.
//
// UX choice: selecting a number shows its detail inline below the grid
// (not a modal, not a separate /pdv/{number} route) — simplest for this
// first PDV screen; a dedicated route/shell is a natural evolution once
// /pdv grows more sections (prompt mestre seção 21).

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { apiErrorMessage } from "@/lib/api/client";
import {
  closeCommand,
  getCommand,
  openCommand,
  type CommandStatus,
} from "@/lib/api/commands";
import {
  listCommandOrders,
  markOrderCancelled,
  markOrderDelivered,
  type Order,
  type OrderStatus,
} from "@/lib/api/orders";

const COMMAND_NUMBERS = Array.from({ length: 100 }, (_, i) => i + 1);

// Tom de atendente, diferente do texto amigável ao cliente em
// app/c/[commandNumber]/page.tsx — aqui é densidade de informação, não
// acolhimento.
const STATUS_LABEL: Record<CommandStatus, string> = {
  AVAILABLE: "Disponível",
  OPEN: "Aberta",
  PAYMENT_REQUESTED: "Aguardando pagamento",
  CLOSED: "Fechada",
  BLOCKED: "Bloqueada",
};

// Mesmo padrão do STATUS_LABEL de CommandStatus acima — rótulo amigável em
// português para o status cru do pedido, que antes aparecia sem tradução
// (`order.status`) no OrderCard.
const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  CREATED: "Criado",
  CONFIRMED: "Confirmado",
  PREPARING: "Em preparo",
  READY: "Pronto",
  DELIVERED: "Entregue",
  CANCELLED: "Cancelado",
};

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

export default function PdvCommandsPage() {
  const [selectedNumber, setSelectedNumber] = useState<number | null>(null);

  return (
    <main className="mx-auto flex max-w-4xl flex-col gap-6 p-6">
      <div>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">PDV</p>
        <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
          Comandas
        </h1>
      </div>

      <div className="grid grid-cols-5 gap-2 sm:grid-cols-10">
        {COMMAND_NUMBERS.map((number) => (
          <button
            key={number}
            type="button"
            onClick={() => setSelectedNumber(number)}
            className={
              number === selectedNumber
                ? "rounded border border-black bg-black px-2 py-2 text-sm font-medium text-white dark:border-white dark:bg-white dark:text-black"
                : "rounded border border-zinc-300 px-2 py-2 text-sm font-medium text-black dark:border-zinc-700 dark:text-zinc-50"
            }
          >
            {number}
          </button>
        ))}
      </div>

      {selectedNumber !== null ? (
        <CommandDetail
          key={selectedNumber}
          number={selectedNumber}
          onClose={() => setSelectedNumber(null)}
        />
      ) : null}
    </main>
  );
}

function CommandDetail({
  number,
  onClose,
}: {
  number: number;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();

  const commandQuery = useQuery({
    queryKey: ["pdv", "command", number],
    queryFn: () => getCommand(number),
  });

  const ordersQuery = useQuery({
    queryKey: ["pdv", "command", number, "orders"],
    queryFn: () => listCommandOrders(number),
  });

  const openMutation = useMutation({
    mutationFn: () => openCommand(number),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pdv", "command", number] });
    },
  });

  const closeMutation = useMutation({
    mutationFn: () => closeCommand(number),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pdv", "command", number] });
    },
  });

  const command = commandQuery.data;
  const canOpen = command?.status === "AVAILABLE";
  const canClose =
    command?.status === "OPEN" || command?.status === "PAYMENT_REQUESTED";

  const openErrorMessage = apiErrorMessage(
    openMutation.error,
    "Não foi possível abrir a comanda.",
  );
  const closeErrorMessage = apiErrorMessage(
    closeMutation.error,
    "Não foi possível fechar a comanda.",
  );

  return (
    <section className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">
          Comanda {number}
        </h2>
        <button
          type="button"
          onClick={onClose}
          className="text-sm text-zinc-500 hover:underline dark:text-zinc-400"
        >
          Fechar painel
        </button>
      </div>

      {commandQuery.isLoading ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Carregando...
        </p>
      ) : null}
      {commandQuery.isError ? (
        <p className="text-sm text-red-600 dark:text-red-400">
          Não foi possível carregar a comanda.
        </p>
      ) : null}

      {command ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-black dark:text-zinc-50">
            Status:{" "}
            <span className="font-medium">{STATUS_LABEL[command.status]}</span>
          </p>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={!canOpen || openMutation.isPending}
              onClick={() => openMutation.mutate()}
              className="rounded bg-black px-3 py-1.5 text-sm font-medium text-white disabled:opacity-40 dark:bg-white dark:text-black"
            >
              {openMutation.isPending ? "Abrindo..." : "Abrir comanda"}
            </button>
            <button
              type="button"
              disabled={!canClose || closeMutation.isPending}
              onClick={() => closeMutation.mutate()}
              className="rounded border border-zinc-300 px-3 py-1.5 text-sm font-medium text-black disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-50"
            >
              {closeMutation.isPending ? "Fechando..." : "Fechar comanda"}
            </button>
          </div>
          {openErrorMessage ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              {openErrorMessage}
            </p>
          ) : null}
          {closeErrorMessage ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              {closeErrorMessage}
            </p>
          ) : null}
        </div>
      ) : null}

      <div className="flex flex-col gap-3">
        <h3 className="text-sm font-semibold text-black dark:text-zinc-50">
          Pedidos
        </h3>
        {ordersQuery.isLoading ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Carregando...
          </p>
        ) : null}
        {ordersQuery.isError ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            Não foi possível carregar os pedidos.
          </p>
        ) : null}
        {ordersQuery.data && ordersQuery.data.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Nenhum pedido ainda.
          </p>
        ) : null}
        {ordersQuery.data?.map((order) => (
          <OrderCard key={order.id} order={order} commandNumber={number} />
        ))}
      </div>
    </section>
  );
}

function OrderCard({
  order,
  commandNumber,
}: {
  order: Order;
  commandNumber: number;
}) {
  const queryClient = useQueryClient();
  // Confirmação inline de duas etapas para cancelar — cancelamento é
  // irreversível (CANCELLED é terminal, ver docs/domain-model.md), então um
  // único clique acidental não deve disparar a mutation. Evita
  // `window.confirm` (fora do padrão visual do resto da tela); em vez
  // disso troca o botão "Cancelar pedido" por uma pergunta inline com
  // "Confirmar"/"Voltar", mesma ideia de estado local que o resto do
  // arquivo já usa para UI (ex: `selectedNumber`).
  const [confirmingCancel, setConfirmingCancel] = useState(false);

  const ordersQueryKey = ["pdv", "command", commandNumber, "orders"];

  const deliverMutation = useMutation({
    mutationFn: () => markOrderDelivered(order.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ordersQueryKey });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => markOrderCancelled(order.id),
    onSuccess: () => {
      setConfirmingCancel(false);
      queryClient.invalidateQueries({ queryKey: ordersQueryKey });
    },
  });

  const canDeliver = order.status === "READY";
  // Qualquer status não-terminal aceita cancelamento — mesmo conjunto de
  // origens válidas do backend (CREATED/CONFIRMED/PREPARING/READY),
  // expresso aqui como "tudo que não é DELIVERED/CANCELLED" para não ter
  // que manter as duas listas em sincronia manualmente.
  const canCancel =
    order.status !== "DELIVERED" && order.status !== "CANCELLED";

  const deliverErrorMessage = apiErrorMessage(
    deliverMutation.error,
    "Não foi possível marcar o pedido como entregue.",
  );
  const cancelErrorMessage = apiErrorMessage(
    cancelMutation.error,
    "Não foi possível cancelar o pedido.",
  );

  const subtotal = order.items.reduce(
    (sum, item) => sum + item.unitPrice * item.quantity,
    0,
  );

  return (
    <div className="rounded border border-zinc-200 p-3 dark:border-zinc-800">
      <div className="flex items-center justify-between text-xs text-zinc-500 dark:text-zinc-400">
        <span>{ORDER_STATUS_LABEL[order.status]}</span>
        <span>{dateTimeFormatter.format(new Date(order.createdAt))}</span>
      </div>
      <ul className="mt-1 flex flex-col gap-0.5">
        {order.items.map((item) => (
          <li
            key={item.id}
            className="flex justify-between text-sm text-black dark:text-zinc-50"
          >
            <span>
              {item.quantity}× {item.productName}
            </span>
            <span>
              {currencyFormatter.format(item.unitPrice * item.quantity)}
            </span>
          </li>
        ))}
      </ul>
      <div className="mt-1 flex justify-between border-t border-zinc-200 pt-1 text-sm font-medium text-black dark:border-zinc-800 dark:text-zinc-50">
        <span>Subtotal</span>
        <span>{currencyFormatter.format(subtotal)}</span>
      </div>

      {canDeliver || canCancel ? (
        <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-zinc-200 pt-2 dark:border-zinc-800">
          {canDeliver ? (
            <button
              type="button"
              disabled={deliverMutation.isPending}
              onClick={() => deliverMutation.mutate()}
              className="rounded bg-black px-3 py-1.5 text-sm font-medium text-white disabled:opacity-40 dark:bg-white dark:text-black"
            >
              {deliverMutation.isPending
                ? "Marcando..."
                : "Marcar como entregue"}
            </button>
          ) : null}

          {canCancel ? (
            confirmingCancel ? (
              <div className="flex items-center gap-2">
                <span className="text-sm text-zinc-600 dark:text-zinc-300">
                  Cancelar este pedido?
                </span>
                <button
                  type="button"
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate()}
                  className="rounded bg-red-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-40"
                >
                  {cancelMutation.isPending ? "Cancelando..." : "Confirmar"}
                </button>
                <button
                  type="button"
                  disabled={cancelMutation.isPending}
                  onClick={() => setConfirmingCancel(false)}
                  className="rounded border border-zinc-300 px-3 py-1.5 text-sm font-medium text-black disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-50"
                >
                  Voltar
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmingCancel(true)}
                className="rounded border border-red-300 px-3 py-1.5 text-sm font-medium text-red-600 dark:border-red-900 dark:text-red-400"
              >
                Cancelar pedido
              </button>
            )
          ) : null}
        </div>
      ) : null}
      {deliverErrorMessage ? (
        <p className="mt-1 text-sm text-red-600 dark:text-red-400">
          {deliverErrorMessage}
        </p>
      ) : null}
      {cancelErrorMessage ? (
        <p className="mt-1 text-sm text-red-600 dark:text-red-400">
          {cancelErrorMessage}
        </p>
      ) : null}
    </div>
  );
}
