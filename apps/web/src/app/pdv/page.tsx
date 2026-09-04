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
// instead of inventing one on the backend just for this, and it can't
// show which numbers are actually open/occupied at a glance (that would
// need bulk status data the backend doesn't expose yet) — every cell
// renders identically until picked. If the grid ever needs to show every
// command's status at once (color-coded, say), a listing endpoint
// becomes the natural next step.
//
// UX choice: selecting a number shows its detail inline below the grid
// (not a modal, not a separate /pdv/{number} route) — simplest for this
// PDV screen; a dedicated route/shell is a natural evolution once /pdv
// grows more sections (prompt mestre seção 21).

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AuthGuard } from "@/components/auth-guard";
import { InternalNav } from "@/components/internal-nav";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
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
import {
  getTotalPaid,
  recordPayment,
  type PaymentMethod,
} from "@/lib/api/payments";
import { cn } from "@/lib/cn";

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

const STATUS_TONE: Record<
  CommandStatus,
  "primary" | "amber" | "neutral" | "red"
> = {
  AVAILABLE: "neutral",
  OPEN: "primary",
  PAYMENT_REQUESTED: "amber",
  CLOSED: "neutral",
  BLOCKED: "red",
};

const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  CREATED: "Criado",
  CONFIRMED: "Confirmado",
  PREPARING: "Em preparo",
  READY: "Pronto",
  DELIVERED: "Entregue",
  CANCELLED: "Cancelado",
};

const ORDER_STATUS_TONE: Record<
  OrderStatus,
  "primary" | "amber" | "green" | "neutral" | "red"
> = {
  CREATED: "neutral",
  CONFIRMED: "neutral",
  PREPARING: "primary",
  READY: "green",
  DELIVERED: "neutral",
  CANCELLED: "red",
};

const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  PIX: "Pix",
  CREDIT_CARD: "Cartão de crédito",
  DEBIT_CARD: "Cartão de débito",
  CASH: "Dinheiro",
  OTHER: "Outro",
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
    <AuthGuard>
      <InternalNav />
      <main className="mx-auto flex max-w-5xl flex-col gap-6 p-8">
        <div>
          <p className="text-ink-soft text-sm">PDV</p>
          <h1 className="font-serif text-2xl font-semibold">Comandas</h1>
        </div>

        <div className="grid grid-cols-5 gap-2.5 sm:grid-cols-10">
          {COMMAND_NUMBERS.map((number) => (
            <button
              key={number}
              type="button"
              onClick={() => setSelectedNumber(number)}
              className={cn(
                "aspect-square rounded-xl border text-sm font-bold",
                number === selectedNumber
                  ? "bg-primary text-primary-ink border-transparent"
                  : "border-line bg-surface text-ink-soft hover:border-primary/40",
              )}
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
    </AuthGuard>
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

  const totalPaidQuery = useQuery({
    queryKey: ["pdv", "command", number, "paymentsTotal"],
    queryFn: () => getTotalPaid(number),
  });

  const invalidateCommand = () =>
    queryClient.invalidateQueries({ queryKey: ["pdv", "command", number] });

  const openMutation = useMutation({
    mutationFn: () => openCommand(number),
    onSuccess: invalidateCommand,
  });

  const closeMutation = useMutation({
    mutationFn: () => closeCommand(number),
    onSuccess: invalidateCommand,
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

  const orders = ordersQuery.data ?? [];
  const totalOwed = orders
    .filter((order) => order.status !== "CANCELLED")
    .reduce(
      (sum, order) =>
        sum +
        order.items.reduce(
          (itemSum, item) => itemSum + item.unitPrice * item.quantity,
          0,
        ),
      0,
    );
  const totalPaid = totalPaidQuery.data?.totalPaid ?? 0;
  const remaining = Math.max(totalOwed - totalPaid, 0);

  // Cliente informado no pedido mais recente que trouxe essa informação —
  // ver Order.customerName/customerPhone's javadoc no backend: é um
  // snapshot por pedido, não um cadastro de cliente único por comanda, mas
  // na prática a mesma pessoa costuma abrir todos os pedidos de uma
  // comanda, então o mais recente é a melhor aproximação disponível hoje.
  const customerOrder = [...orders]
    .reverse()
    .find((order) => order.customerName);

  return (
    <Card className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h2 className="font-serif text-xl font-semibold">Comanda {number}</h2>
          {command ? (
            <Badge tone={STATUS_TONE[command.status]} dot>
              {STATUS_LABEL[command.status]}
            </Badge>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-soft hover:text-ink text-sm"
        >
          Fechar painel
        </button>
      </div>

      {commandQuery.isLoading ? (
        <p className="text-ink-faint text-sm">Carregando...</p>
      ) : null}
      {commandQuery.isError ? (
        <p className="text-red text-sm">Não foi possível carregar a comanda.</p>
      ) : null}

      {command ? (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap gap-2">
            <Button
              variant="dark"
              disabled={!canOpen || openMutation.isPending}
              onClick={() => openMutation.mutate()}
            >
              {openMutation.isPending ? "Abrindo..." : "Abrir comanda"}
            </Button>
            <Button
              variant="outline"
              disabled={!canClose || remaining > 0 || closeMutation.isPending}
              onClick={() => closeMutation.mutate()}
              title={
                canClose && remaining > 0
                  ? "Restam pagamentos pendentes"
                  : undefined
              }
            >
              {closeMutation.isPending ? "Fechando..." : "Fechar comanda"}
            </Button>
          </div>
          {openErrorMessage ? (
            <p className="text-red text-sm">{openErrorMessage}</p>
          ) : null}
          {closeErrorMessage ? (
            <p className="text-red text-sm">{closeErrorMessage}</p>
          ) : null}
        </div>
      ) : null}

      {customerOrder ? (
        <div className="border-line bg-bg flex items-center gap-3.5 rounded-xl border px-4 py-3.5">
          <span className="bg-primary-soft text-primary-dark flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-serif text-sm font-semibold">
            {customerOrder
              .customerName!.split(" ")
              .map((part) => part[0])
              .slice(0, 2)
              .join("")
              .toUpperCase()}
          </span>
          <div>
            <div className="font-serif text-[15px] font-semibold">
              {customerOrder.customerName}
            </div>
            <div className="text-ink-soft text-[13px]">
              {customerOrder.customerPhone ?? "Sem telefone informado"} ·
              informado no pedido das{" "}
              {dateTimeFormatter.format(new Date(customerOrder.createdAt))}
            </div>
          </div>
        </div>
      ) : null}

      <div className="grid gap-5 sm:grid-cols-[2fr_1fr] sm:items-start">
        <div className="flex flex-col gap-3">
          <h3 className="text-ink-faint text-xs font-bold tracking-wide uppercase">
            Pedidos
          </h3>
          {ordersQuery.isLoading ? (
            <p className="text-ink-faint text-sm">Carregando...</p>
          ) : null}
          {ordersQuery.isError ? (
            <p className="text-red text-sm">
              Não foi possível carregar os pedidos.
            </p>
          ) : null}
          {ordersQuery.data && ordersQuery.data.length === 0 ? (
            <p className="text-ink-faint text-sm">Nenhum pedido ainda.</p>
          ) : null}
          {ordersQuery.data?.map((order) => (
            <OrderCard key={order.id} order={order} commandNumber={number} />
          ))}
        </div>

        {command ? (
          <PaymentPanel
            commandNumber={number}
            totalOwed={totalOwed}
            totalPaid={totalPaid}
            remaining={remaining}
          />
        ) : null}
      </div>
    </Card>
  );
}

function PaymentPanel({
  commandNumber,
  totalOwed,
  totalPaid,
  remaining,
}: {
  commandNumber: number;
  totalOwed: number;
  totalPaid: number;
  remaining: number;
}) {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);

  const paymentFormSchema = z.object({
    amount: z
      .string()
      .trim()
      .min(1, "Valor é obrigatório")
      .refine((value) => !Number.isNaN(Number(value)), "Valor inválido")
      .refine((value) => Number(value) > 0, "Valor deve ser maior que zero"),
    method: z.enum(["PIX", "CREDIT_CARD", "DEBIT_CARD", "CASH", "OTHER"]),
  });
  type PaymentFormValues = z.infer<typeof paymentFormSchema>;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PaymentFormValues>({
    resolver: zodResolver(paymentFormSchema),
    defaultValues: {
      amount: remaining > 0 ? String(remaining) : "",
      method: "PIX",
    },
  });

  const recordPaymentMutation = useMutation({
    mutationFn: (values: PaymentFormValues) =>
      recordPayment(commandNumber, {
        amount: Number(values.amount),
        method: values.method,
      }),
    onSuccess: () => {
      reset();
      setFormOpen(false);
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "paymentsTotal"],
      });
    },
  });

  const errorMessage = apiErrorMessage(
    recordPaymentMutation.error,
    "Não foi possível registrar o pagamento.",
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="border-line bg-bg rounded-2xl border p-4">
        <div className="text-ink-faint text-xs font-bold tracking-wide uppercase">
          Total da comanda
        </div>
        <div className="mt-1 font-serif text-2xl font-semibold">
          {currencyFormatter.format(totalOwed)}
        </div>
        <div className="bg-bg-alt mt-3 h-1.5 overflow-hidden rounded-full">
          <div
            className="bg-primary h-full"
            style={{
              width: `${totalOwed > 0 ? Math.min((totalPaid / totalOwed) * 100, 100) : 0}%`,
            }}
          />
        </div>
        <div className="mt-2 flex flex-col gap-0.5 text-[13px]">
          <span className="text-ink-soft">
            Pago · {currencyFormatter.format(totalPaid)}
          </span>
          <span className="text-primary-dark font-bold">
            Restante · {currencyFormatter.format(remaining)}
          </span>
        </div>
      </div>

      {formOpen ? (
        <form
          onSubmit={handleSubmit((values) =>
            recordPaymentMutation.mutate(values),
          )}
          noValidate
          className="border-line bg-bg flex flex-col gap-3 rounded-2xl border p-4"
        >
          <div className="flex flex-col gap-1">
            <label className="text-ink text-sm font-medium" htmlFor="amount">
              Valor (R$)
            </label>
            <input
              id="amount"
              inputMode="decimal"
              className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
              {...register("amount")}
            />
            {errors.amount ? (
              <p className="text-red text-sm">{errors.amount.message}</p>
            ) : null}
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-ink text-sm font-medium" htmlFor="method">
              Forma de pagamento
            </label>
            <select
              id="method"
              className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
              {...register("method")}
            >
              {Object.entries(PAYMENT_METHOD_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          {errorMessage ? (
            <p className="text-red text-sm">{errorMessage}</p>
          ) : null}
          <div className="flex gap-2">
            <Button
              type="submit"
              disabled={recordPaymentMutation.isPending}
              className="flex-1"
            >
              {recordPaymentMutation.isPending ? "Registrando..." : "Confirmar"}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => setFormOpen(false)}
            >
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <div className="border-line bg-surface flex flex-col gap-2 rounded-2xl border p-4">
          <Button
            disabled={remaining <= 0}
            onClick={() => {
              // useForm's `defaultValues` are only read once, at mount —
              // this component mounts before `remaining` settles (both
              // queries still loading), so re-seed the field here with
              // the real value at the moment the form actually opens.
              reset({ amount: String(remaining), method: "PIX" });
              setFormOpen(true);
            }}
          >
            Registrar pagamento
          </Button>
          <p className="text-ink-faint text-center text-xs">
            {remaining <= 0
              ? "Comanda totalmente paga"
              : "Disponível até o restante ser R$ 0,00"}
          </p>
        </div>
      )}
    </div>
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
    <div className="border-line bg-surface rounded-2xl border p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm font-bold">
          Pedido · {dateTimeFormatter.format(new Date(order.createdAt))}
        </span>
        <Badge tone={ORDER_STATUS_TONE[order.status]}>
          {ORDER_STATUS_LABEL[order.status]}
        </Badge>
      </div>
      <ul className="border-line mt-2 flex flex-col gap-0.5 border-t pt-2">
        {order.items.map((item) => (
          <li key={item.id} className="flex justify-between text-sm">
            <span>
              {item.quantity}× {item.productName}
            </span>
            <span className="font-semibold">
              {currencyFormatter.format(item.unitPrice * item.quantity)}
            </span>
          </li>
        ))}
      </ul>
      <div className="text-ink-soft mt-2 flex justify-end text-[13px]">
        Subtotal ·{" "}
        <span className="text-ink ml-1 font-bold">
          {currencyFormatter.format(subtotal)}
        </span>
      </div>

      {canDeliver || canCancel ? (
        <div className="border-line mt-3 flex flex-wrap items-center gap-2 border-t pt-3">
          {canDeliver ? (
            <Button
              variant="dark"
              disabled={deliverMutation.isPending}
              onClick={() => deliverMutation.mutate()}
              className="px-4 py-2 text-[13px]"
            >
              {deliverMutation.isPending
                ? "Marcando..."
                : "Marcar como entregue"}
            </Button>
          ) : null}

          {canCancel ? (
            confirmingCancel ? (
              <div className="flex items-center gap-2">
                <span className="text-ink-soft text-sm">
                  Cancelar este pedido?
                </span>
                <Button
                  variant="danger"
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate()}
                  className="px-4 py-2 text-[13px]"
                >
                  {cancelMutation.isPending ? "Cancelando..." : "Confirmar"}
                </Button>
                <Button
                  variant="outline"
                  disabled={cancelMutation.isPending}
                  onClick={() => setConfirmingCancel(false)}
                  className="px-4 py-2 text-[13px]"
                >
                  Voltar
                </Button>
              </div>
            ) : (
              <Button
                variant="ghost-danger"
                onClick={() => setConfirmingCancel(true)}
                className="px-4 py-2 text-[13px]"
              >
                Cancelar pedido
              </Button>
            )
          ) : null}
        </div>
      ) : null}
      {deliverErrorMessage ? (
        <p className="text-red mt-2 text-sm">{deliverErrorMessage}</p>
      ) : null}
      {cancelErrorMessage ? (
        <p className="text-red mt-2 text-sm">{cancelErrorMessage}</p>
      ) : null}
    </div>
  );
}
