"use client";

// Client component (no SSR need here, same choice as the Admin pages):
// /pdv is an internal, staff-facing tool — heavy interactivity (100
// clickable commands, on-demand detail fetch, open/close actions), no
// SEO/first-paint concern. TanStack Query (already global via
// app/providers.tsx) handles the fetching, same pattern as
// /admin/categories and /admin/products.
//
// The staff-only list endpoint provides live status for the comanda grid.
//
// UX choice: selecting a number shows its detail inline below the grid
// (not a modal, not a separate /pdv/{number} route) — simplest for this
// PDV screen; a dedicated route/shell is a natural evolution once /pdv
// grows more sections (prompt mestre seção 21).

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";

import { AuthGuard } from "@/components/auth-guard";
import { InternalNav } from "@/components/internal-nav";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { listCategories, type Category } from "@/lib/api/categories";
import { listCashSessions } from "@/lib/api/cashier";
import { apiErrorMessage } from "@/lib/api/client";
import {
  closeCommand,
  getCommand,
  listCommandVisits,
  listCommands,
  openCommand,
  updateCommandCustomer,
  updateCommandTable,
  type Command,
  type CommandStatus,
} from "@/lib/api/commands";
import {
  assignOrderWaiter,
  cancelOrderItem,
  createBalcaoOrder,
  createOrder,
  duplicateOrderItem,
  listCommandOrders,
  markOrderCancelled,
  markOrderDelivered,
  orderIntentFor,
  type Order,
  type OrderIntent,
  type OrderItem,
  type OrderItemCancelReason,
  type OrderStatus,
  type CreateOrderItemInput,
} from "@/lib/api/orders";
import { listUsers } from "@/lib/api/users";
import {
  applyDiscount,
  applyItemDiscount,
  type DiscountType,
} from "@/lib/api/discounts";
import {
  getPaymentBalance,
  paymentIntentFor,
  recordPayment,
  type PaymentIntent,
  type PaymentMethod,
} from "@/lib/api/payments";
import { listProducts, type Product } from "@/lib/api/products";
import { listProductSteps, type ProductStep } from "@/lib/api/product-steps";
import { getTableOccupancy, listTables } from "@/lib/api/tables";
import { cn } from "@/lib/cn";
import { decodeUserId } from "@/lib/auth";
import { parseMoneyCents } from "@/lib/money-input";
import { freshPaymentAmount } from "@/lib/payment-after-order";
import { useSession } from "@/lib/use-session";

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

// FARELO-200/201: labels for the five fixed OrderItemCancelReason values —
// order matches the enum's declaration on the backend.
const ITEM_CANCEL_REASON_LABEL: Record<OrderItemCancelReason, string> = {
  CUSTOMER_REQUEST: "Pedido do cliente",
  ENTRY_ERROR: "Erro de lançamento",
  OUT_OF_STOCK: "Sem estoque",
  QUALITY_ISSUE: "Problema de qualidade",
  OTHER: "Outro",
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
  const [balcaoOpen, setBalcaoOpen] = useState(false);

  return (
    <AuthGuard allow={["ADMIN", "MANAGER", "CASHIER", "ATTENDANT"]}>
      <InternalNav />
      <main className="mx-auto flex max-w-5xl flex-col gap-6 p-8">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-ink-soft text-sm">PDV</p>
            <h1 className="font-serif text-2xl font-semibold">
              Comanda / mesa
            </h1>
          </div>
          <Button
            variant={balcaoOpen ? "outline" : "dark"}
            onClick={() => setBalcaoOpen((open) => !open)}
          >
            {balcaoOpen ? "Fechar venda balcão" : "+ Venda balcão"}
          </Button>
        </div>

        <TableOccupancySummary />

        {balcaoOpen ? (
          <BalcaoPanel onClose={() => setBalcaoOpen(false)} />
        ) : null}

        <CommandGrid
          selectedNumber={selectedNumber}
          onSelect={setSelectedNumber}
        />

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

function CommandGrid({
  selectedNumber,
  onSelect,
}: {
  selectedNumber: number | null;
  onSelect: (number: number) => void;
}) {
  const [numberFilter, setNumberFilter] = useState("");
  const commands = useQuery({
    queryKey: ["pdv", "commands"],
    queryFn: listCommands,
    refetchInterval: 10000,
  });

  if (commands.isPending) {
    return <p className="text-ink-soft text-sm">Carregando comandas...</p>;
  }
  if (commands.isError) {
    return (
      <p role="alert" className="text-red text-sm">
        Não foi possível carregar as comandas. Confira sua conexão e tente
        novamente.
      </p>
    );
  }

  const openCount = commands.data.filter(
    ({ status }) => status === "OPEN" || status === "PAYMENT_REQUESTED",
  ).length;
  const visibleCommands = commands.data.filter(({ number }) =>
    String(number).includes(numberFilter.trim()),
  );

  return (
    <section aria-label="Comandas">
      <p className="text-ink-soft mb-3 text-sm">
        {openCount} em uso · {commands.data.length} cadastradas
      </p>
      <p className="text-ink-faint mb-3 text-xs">
        Verde: aberta · amarelo: pagamento · cinza: fechada · vermelho:
        bloqueada
      </p>
      <label className="text-ink-soft mb-3 flex max-w-xs flex-col gap-1 text-xs font-semibold">
        Nº da comanda
        <input
          type="search"
          inputMode="numeric"
          value={numberFilter}
          onChange={(event) =>
            setNumberFilter(event.target.value.replace(/\D/g, ""))
          }
          placeholder="Buscar número"
          className="border-line bg-surface text-ink focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
        />
      </label>
      <div className="grid grid-cols-5 gap-2.5 sm:grid-cols-10">
        {visibleCommands.map(({ number, status, tableNumber }) => (
          <button
            key={number}
            type="button"
            onClick={() => onSelect(number)}
            aria-label={`Comanda ${number}, ${STATUS_LABEL[status]}${tableNumber === null ? "" : `, mesa ${tableNumber}`}`}
            aria-pressed={number === selectedNumber}
            className={cn(
              "aspect-square rounded-xl border text-sm font-bold",
              number === selectedNumber
                ? "bg-primary text-primary-ink border-transparent"
                : status === "OPEN"
                  ? "border-green bg-green-soft text-green-ink"
                  : status === "PAYMENT_REQUESTED"
                    ? "border-amber bg-amber-soft text-amber-ink"
                    : status === "BLOCKED"
                      ? "border-red bg-red-soft text-red-ink"
                      : status === "CLOSED"
                        ? "border-line bg-bg-alt text-ink-faint"
                        : "border-line bg-surface text-ink-soft hover:border-primary/40",
            )}
          >
            {number}
          </button>
        ))}
      </div>
      {visibleCommands.length === 0 ? (
        <p className="text-ink-faint mt-3 text-sm">
          Nenhuma comanda encontrada.
        </p>
      ) : null}
    </section>
  );
}

function TableOccupancySummary() {
  const occupancy = useQuery({
    queryKey: ["pdv", "table-occupancy"],
    queryFn: getTableOccupancy,
  });
  if (!occupancy.data?.totalTables) return null;
  return (
    <div className="border-line bg-surface flex flex-wrap gap-5 rounded-xl border px-4 py-3 text-sm">
      <span>
        <strong>{occupancy.data.occupiedTables}</strong> mesas ocupadas
      </span>
      <span>
        <strong>{occupancy.data.totalTables}</strong> mesas cadastradas
      </span>
      <span>
        <strong>{occupancy.data.occupancyPercentage}%</strong> de ocupação
      </span>
    </div>
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
  const [addItemOpen, setAddItemOpen] = useState(false);
  // FARELO-320: "Confirmar e Receber" — a PDV-only UI shortcut (Farelo has
  // no real backend "Confirmar" transition to formalize — an order is
  // already CREATED/"confirmed" the instant it's placed, see
  // OrderStatus's own javadoc on the unused CONFIRMED value) that both
  // creates the order from AddItemPanel's draft and
  // opens PaymentPanel's payment form with the fresh server balance. The
  // token distinguishes consecutive prompts; amount is fetched after the
  // order commits, before the form opens.
  const [paymentPrompt, setPaymentPrompt] = useState<{
    token: number;
    amount: number | null;
  }>({ token: 0, amount: null });

  const commandQuery = useQuery({
    queryKey: ["pdv", "command", number],
    queryFn: () => getCommand(number),
    refetchInterval: 10000,
  });

  const visitsQuery = useQuery({
    queryKey: ["pdv", "command", number, "visits"],
    queryFn: () => listCommandVisits(number),
  });

  const ordersQuery = useQuery({
    queryKey: ["pdv", "command", number, "orders"],
    queryFn: () => listCommandOrders(number),
    // Kitchen staff can change an order while this comanda stays open.
    refetchInterval: 10000,
  });

  const balanceQuery = useQuery({
    queryKey: ["pdv", "command", number, "paymentBalance"],
    queryFn: () => getPaymentBalance(number),
    refetchInterval: 10000,
  });

  const invalidateCommand = () => {
    queryClient.invalidateQueries({ queryKey: ["pdv", "command", number] });
    queryClient.invalidateQueries({ queryKey: ["pdv", "commands"] });
  };

  const openMutation = useMutation({
    mutationFn: () => openCommand(number),
    onSuccess: invalidateCommand,
  });

  const closeMutation = useMutation({
    mutationFn: () => closeCommand(number),
    onSuccess: invalidateCommand,
  });

  const command = commandQuery.data;
  const canOpen =
    command?.status === "AVAILABLE" || command?.status === "CLOSED";
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

  // FARELO-223: totalOwed/totalPaid/remaining all come from the backend
  // (GET .../payments/balance) — no client-side sum/subtraction anymore,
  // per that ticket's "backend deve ser fonte de verdade" requirement.
  // totalDiscount added by FARELO-230/231/232.
  const totalOwed = balanceQuery.data?.totalOwed ?? 0;
  const totalDiscount = balanceQuery.data?.totalDiscount ?? 0;
  const totalPaid = balanceQuery.data?.totalPaid ?? 0;
  const remaining = balanceQuery.data?.remaining ?? 0;

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
          {command ? (
            <span className="text-ink-faint text-xs">
              Visita {command.cycleNumber}
            </span>
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
              {openMutation.isPending
                ? "Abrindo..."
                : command.status === "CLOSED"
                  ? "Iniciar nova visita"
                  : "Abrir comanda"}
            </Button>
            <Button
              variant="outline"
              disabled={
                !canClose ||
                !balanceQuery.isSuccess ||
                remaining > 0 ||
                closeMutation.isPending
              }
              onClick={() => closeMutation.mutate()}
              title={
                canClose
                  ? !balanceQuery.isSuccess
                    ? "Aguardando saldo atualizado da comanda"
                    : remaining > 0
                      ? "Restam pagamentos pendentes"
                      : undefined
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
          {balanceQuery.isError ? (
            <p role="alert" className="text-red text-sm">
              Não foi possível consultar o saldo. Atualize a comanda antes de
              fechá-la.
            </p>
          ) : null}
        </div>
      ) : null}

      {(visitsQuery.data?.length ?? 0) > 1 ? (
        <div className="border-line border-t pt-3 text-sm">
          <p className="text-ink-soft font-semibold">Histórico de visitas</p>
          <ul className="text-ink-faint mt-2 flex flex-wrap gap-3">
            {visitsQuery.data?.map((visit) => (
              <li key={visit.id}>
                Visita {visit.cycleNumber} · {STATUS_LABEL[visit.status]} ·{" "}
                {dateTimeFormatter.format(new Date(visit.createdAt))}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {command ? (
        <CustomerCard commandNumber={number} command={command} />
      ) : null}

      {command ? <TableAssociation command={command} /> : null}

      <div className="grid gap-5 sm:grid-cols-[2fr_1fr] sm:items-start">
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h3 className="text-ink-faint text-xs font-bold tracking-wide uppercase">
              Pedidos
            </h3>
            {command &&
            (command.status === "AVAILABLE" || command.status === "OPEN") ? (
              <button
                type="button"
                onClick={() => setAddItemOpen((open) => !open)}
                className="text-primary text-xs font-bold hover:underline"
              >
                {addItemOpen ? "Fechar" : "+ Adicionar item"}
              </button>
            ) : null}
          </div>

          {addItemOpen ? (
            <AddItemPanel
              commandNumber={number}
              onAdded={() => {
                // Partial-match invalidation: also catches "orders" and
                // "paymentBalance" (both nested under this same key prefix)
                // in one call — and the command status itself, since
                // adding the first item auto-opens an AVAILABLE comanda
                // (CommandService#openForOrdering) and the header badge
                // needs to reflect that.
                queryClient.invalidateQueries({
                  queryKey: ["pdv", "command", number],
                });
                queryClient.invalidateQueries({
                  queryKey: ["pdv", "commands"],
                });
              }}
              onConfirmAndReceive={() => {
                void freshPaymentAmount(() =>
                  queryClient.fetchQuery({
                    queryKey: ["pdv", "command", number, "paymentBalance"],
                    queryFn: () => getPaymentBalance(number),
                    staleTime: 0,
                  }),
                ).then((amount) =>
                  setPaymentPrompt(({ token }) => ({
                    token: token + 1,
                    amount,
                  })),
                );
              }}
            />
          ) : null}

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
            totalDiscount={totalDiscount}
            totalPaid={totalPaid}
            remaining={remaining}
            openSignal={paymentPrompt}
          />
        ) : null}
      </div>
    </Card>
  );
}

function TableAssociation({ command }: { command: Command }) {
  const queryClient = useQueryClient();
  const [selection, setSelection] = useState<string | null>(null);
  const tables = useQuery({ queryKey: ["pdv", "tables"], queryFn: listTables });
  const selectedValue = selection ?? String(command.tableNumber ?? "");
  const mutation = useMutation({
    mutationFn: () =>
      updateCommandTable(
        command.number,
        selectedValue ? Number(selectedValue) : null,
      ),
    onSuccess: async () => {
      setSelection(null);
      await queryClient.invalidateQueries({
        queryKey: ["pdv", "command", command.number],
      });
      await queryClient.invalidateQueries({
        queryKey: ["pdv", "table-occupancy"],
      });
    },
  });

  if (!tables.data?.length) return null;
  return (
    <div className="border-line flex flex-wrap items-end gap-3 border-t pt-4">
      <label className="text-ink-soft flex min-w-40 flex-col gap-1 text-sm">
        Mesa vinculada
        <select
          value={selectedValue}
          onChange={(event) => setSelection(event.target.value)}
          className="border-line bg-surface text-ink rounded-lg border px-3 py-2"
        >
          <option value="">Sem mesa</option>
          {tables.data.map((table) => (
            <option key={table.id} value={table.number}>
              Mesa {table.number} · {table.capacity} lugares
            </option>
          ))}
        </select>
      </label>
      <Button
        type="button"
        variant="outline"
        disabled={
          mutation.isPending ||
          selectedValue === String(command.tableNumber ?? "")
        }
        onClick={() => mutation.mutate()}
      >
        Salvar mesa
      </Button>
      {mutation.isError ? (
        <p className="text-red text-sm">
          {apiErrorMessage(mutation.error, "Não foi possível vincular a mesa.")}
        </p>
      ) : null}
    </div>
  );
}

// The comanda operator builds one multi-item order locally before sending it
// to the existing POST /api/v1/orders endpoint. Selecting a product never
// writes to the server; Confirmar and Receber are the commit points.
type DraftCartItem = CreateOrderItemInput & {
  lineId: string;
  unitPriceCents: number;
  optionLabels: string[];
};

function selectedOptionCount(
  step: ProductStep,
  quantities: Record<string, number>,
) {
  return step.options.reduce(
    (total, option) => total + (quantities[option.id] ?? 0),
    0,
  );
}

function AddItemPanel({
  commandNumber,
  onAdded,
  onConfirmAndReceive,
}: {
  commandNumber: number;
  onAdded: () => void;
  // FARELO-320: called (in addition to onAdded) when the order was
  // created via "Confirmar e receber" rather than plain confirmation — see
  // CommandDetail's own comment on paymentPromptToken for the full flow.
  onConfirmAndReceive: () => void;
}) {
  const [search, setSearch] = useState("");
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(
    null,
  );
  const [selectedProductId, setSelectedProductId] = useState<string | null>(
    null,
  );
  const [quantity, setQuantity] = useState(1);
  const [optionQuantities, setOptionQuantities] = useState<
    Record<string, number>
  >({});
  const [cart, setCart] = useState<DraftCartItem[]>([]);
  // FARELO-316: "Observação geral do pedido" applies to the draft order,
  // not to the comanda as a whole.
  const [note, setNote] = useState("");
  // FARELO-320: which button triggered the in-flight mutation — read once
  // in onSuccess, then reset. A ref (not state) since it's write-then-
  // read-once bookkeeping for the mutation callback, not something that
  // should trigger a re-render on its own.
  const receiveAfterRef = useRef(false);
  const orderIntent = useRef<OrderIntent | null>(null);

  const productsQuery = useQuery({
    queryKey: ["pdv", "products"],
    queryFn: listProducts,
  });
  const categoriesQuery = useQuery({
    queryKey: ["pdv", "categories"],
    queryFn: listCategories,
  });
  const stepsQuery = useQuery({
    queryKey: ["pdv", "product-steps", selectedProductId],
    queryFn: () => listProductSteps(selectedProductId!),
    enabled: selectedProductId !== null,
    staleTime: 0,
  });

  const addItemMutation = useMutation({
    mutationFn: () => {
      const draft = {
        commandNumber,
        items: cart.map(({ productId, quantity, options }) => ({
          productId,
          quantity,
          ...(options?.length ? { options } : {}),
        })),
        note: note.trim() || undefined,
        printOnConfirm: false,
      };
      orderIntent.current = orderIntentFor(draft, orderIntent.current);
      return createOrder({ ...draft, idempotencyKey: orderIntent.current.key });
    },
    onSuccess: () => {
      orderIntent.current = null;
      setSelectedProductId(null);
      setQuantity(1);
      setOptionQuantities({});
      setCart([]);
      setNote("");
      onAdded();
      if (receiveAfterRef.current) {
        receiveAfterRef.current = false;
        onConfirmAndReceive();
      }
    },
  });

  const errorMessage = apiErrorMessage(
    addItemMutation.error,
    "Não foi possível confirmar o pedido.",
  );

  // Só produtos ativos e habilitados pro caixa (availableOnPos) — mesmo
  // filtro que o cardápio QR já aplica pro seu próprio flag
  // (availableOnMenu), FARELO-181.
  const availableProducts = (productsQuery.data ?? []).filter(
    (product) =>
      product.active &&
      product.availableOnPos &&
      (selectedCategoryId === null ||
        product.categoryId === selectedCategoryId) &&
      product.name.toLowerCase().includes(search.toLowerCase()),
  );
  const categoryNameById = new Map(
    (categoriesQuery.data ?? []).map((category: Category) => [
      category.id,
      category.name,
    ]),
  );
  const sections = new Map<string, Product[]>();
  for (const product of availableProducts) {
    const categoryName = categoryNameById.get(product.categoryId) ?? "Outros";
    sections.set(categoryName, [
      ...(sections.get(categoryName) ?? []),
      product,
    ]);
  }

  const selectedProduct = (productsQuery.data ?? []).find(
    (product) => product.id === selectedProductId,
  );
  const productById = new Map(
    (productsQuery.data ?? []).map((product) => [product.id, product]),
  );
  const cartTotalCents = cart.reduce(
    (total, item) => total + item.unitPriceCents * item.quantity,
    0,
  );
  const selectedSteps = stepsQuery.data ?? [];
  const selectedBaseCents = selectedProduct
    ? parseMoneyCents(String(selectedProduct.price))
    : null;
  const choicesValid = selectedSteps.every((step) => {
    const count = selectedOptionCount(step, optionQuantities);
    return (
      count >= step.minChoices &&
      count <= step.maxChoices &&
      step.options.every(
        (option) => option.optionActive || !optionQuantities[option.id],
      )
    );
  });
  const selectedOptions = selectedSteps.flatMap((step) =>
    step.options
      .filter((option) => (optionQuantities[option.id] ?? 0) > 0)
      .map((option) => ({
        step,
        option,
        quantity: optionQuantities[option.id],
      })),
  );
  const selectedOptionCents = selectedOptions.reduce<number | null>(
    (total, choice) => {
      const cents = parseMoneyCents(String(choice.option.price));
      return total === null || cents === null
        ? null
        : total + cents * choice.quantity;
    },
    0,
  );
  const selectedUnitCents =
    selectedBaseCents === null || selectedOptionCents === null
      ? null
      : selectedBaseCents + selectedOptionCents;
  // A base-zero item with missing/invalid steps must never be placed into
  // the draft as a free sale. Server validation remains authoritative.
  const canAddSelected =
    !!selectedProduct &&
    stepsQuery.isSuccess &&
    !stepsQuery.isFetching &&
    choicesValid &&
    selectedUnitCents !== null &&
    selectedUnitCents > 0 &&
    Number.isSafeInteger(cartTotalCents + selectedUnitCents * quantity) &&
    !addItemMutation.isPending;

  function addSelectedProduct() {
    if (!selectedProduct || !canAddSelected || selectedUnitCents === null)
      return;
    const choices = selectedOptions.map(
      ({ step, option, quantity: choiceQuantity }) => ({
        stepId: step.id,
        optionId: option.id,
        quantity: choiceQuantity,
      }),
    );
    setCart((current) => {
      const existing =
        choices.length === 0 &&
        current.find(
          (item) =>
            item.productId === selectedProduct.id && !item.options?.length,
        );
      return existing
        ? current.map((item) =>
            item.lineId === existing.lineId
              ? { ...item, quantity: item.quantity + quantity }
              : item,
          )
        : [
            ...current,
            {
              lineId: crypto.randomUUID(),
              productId: selectedProduct.id,
              quantity,
              unitPriceCents: selectedUnitCents,
              optionLabels: selectedOptions.map(
                ({ option, quantity: count }) =>
                  `${count} × ${option.optionName}`,
              ),
              ...(choices.length ? { options: choices } : {}),
            },
          ];
    });
    setSelectedProductId(null);
    setQuantity(1);
    setOptionQuantities({});
  }

  return (
    <div className="border-line bg-bg flex flex-col gap-3 rounded-2xl border p-4">
      <input
        type="text"
        placeholder="Buscar produto pelo nome..."
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
      />

      <div
        className="flex gap-2 overflow-x-auto pb-1"
        aria-label="Categorias de produtos"
      >
        <button
          type="button"
          onClick={() => setSelectedCategoryId(null)}
          aria-pressed={selectedCategoryId === null}
          className={cn(
            "shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold",
            selectedCategoryId === null
              ? "bg-primary text-primary-ink"
              : "bg-bg-alt text-ink-soft",
          )}
        >
          Todos
        </button>
        {(categoriesQuery.data ?? [])
          .filter((category) => category.active)
          .sort((a, b) => a.sortOrder - b.sortOrder)
          .map((category) => (
            <button
              key={category.id}
              type="button"
              onClick={() => setSelectedCategoryId(category.id)}
              aria-pressed={selectedCategoryId === category.id}
              className={cn(
                "shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold",
                selectedCategoryId === category.id
                  ? "bg-primary text-primary-ink"
                  : "bg-bg-alt text-ink-soft",
              )}
            >
              {category.name}
            </button>
          ))}
      </div>

      <div className="flex max-h-56 flex-col gap-3 overflow-y-auto">
        {productsQuery.isLoading ? (
          <p className="text-ink-faint text-sm">Carregando...</p>
        ) : null}
        {availableProducts.length === 0 && !productsQuery.isLoading ? (
          <p className="text-ink-faint text-sm">Nenhum produto encontrado.</p>
        ) : null}
        {Array.from(sections.entries()).map(([categoryName, products]) => (
          <div key={categoryName} className="flex flex-col gap-1">
            <div className="text-ink-faint text-[11px] font-bold tracking-wide uppercase">
              {categoryName}
            </div>
            {products.map((product) => (
              <button
                key={product.id}
                type="button"
                onClick={() => {
                  setSelectedProductId(product.id);
                  setOptionQuantities({});
                  setQuantity(1);
                }}
                className={cn(
                  "flex items-center justify-between rounded-lg px-3 py-2 text-left text-sm",
                  selectedProductId === product.id
                    ? "bg-primary-soft text-primary-dark font-semibold"
                    : "hover:bg-bg-alt",
                )}
              >
                <span>{product.name}</span>
                <span className="font-semibold">
                  {currencyFormatter.format(product.price)}
                </span>
              </button>
            ))}
          </div>
        ))}
      </div>

      {selectedProduct ? (
        <div className="border-line flex flex-col gap-2 border-t pt-3">
          {stepsQuery.isPending ? (
            <p className="text-ink-faint text-sm">Carregando escolhas...</p>
          ) : null}
          {stepsQuery.isError ? (
            <p className="text-red text-sm">
              Não foi possível consultar as escolhas deste produto. Tente
              selecioná-lo novamente.
            </p>
          ) : null}
          {selectedSteps.map((step) => {
            const count = selectedOptionCount(step, optionQuantities);
            return (
              <fieldset
                key={step.id}
                className="border-line rounded-xl border p-3"
              >
                <legend className="px-1 text-sm font-semibold">
                  {step.label}
                </legend>
                <p className="text-ink-soft mb-2 text-xs">
                  Escolha de {step.minChoices} a {step.maxChoices} · {count}{" "}
                  selecionada(s)
                </p>
                {step.options.map((option) => {
                  const chosen = optionQuantities[option.id] ?? 0;
                  const optionCents = parseMoneyCents(String(option.price));
                  return (
                    <div
                      key={option.id}
                      className="flex items-center justify-between gap-2 py-1 text-sm"
                    >
                      <span>
                        {option.optionName} ·{" "}
                        {optionCents === null
                          ? "Preço inválido"
                          : `+ ${currencyFormatter.format(optionCents / 100)}`}
                        {!option.optionActive ? (
                          <span className="text-red ml-2 text-xs">
                            Indisponível
                          </span>
                        ) : null}
                      </span>
                      <span className="flex items-center gap-2">
                        <button
                          type="button"
                          aria-label={`Diminuir ${option.optionName}`}
                          disabled={chosen === 0 || addItemMutation.isPending}
                          onClick={() =>
                            setOptionQuantities((current) => ({
                              ...current,
                              [option.id]: Math.max(
                                0,
                                (current[option.id] ?? 0) - 1,
                              ),
                            }))
                          }
                          className="border-line h-7 w-7 rounded-full border disabled:opacity-40"
                        >
                          −
                        </button>
                        <span className="w-5 text-center">{chosen}</span>
                        <button
                          type="button"
                          aria-label={`Aumentar ${option.optionName}`}
                          disabled={
                            !option.optionActive ||
                            optionCents === null ||
                            count >= step.maxChoices ||
                            addItemMutation.isPending
                          }
                          onClick={() =>
                            setOptionQuantities((current) => ({
                              ...current,
                              [option.id]: (current[option.id] ?? 0) + 1,
                            }))
                          }
                          className="border-line h-7 w-7 rounded-full border disabled:opacity-40"
                        >
                          +
                        </button>
                      </span>
                    </div>
                  );
                })}
                {count < step.minChoices ? (
                  <p className="text-red mt-1 text-xs" role="status">
                    Faltam {step.minChoices - count} escolha(s) nesta etapa.
                  </p>
                ) : null}
              </fieldset>
            );
          })}
          <div className="flex items-center gap-3">
            <span className="flex-1 text-sm font-semibold">
              {selectedProduct.name}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setQuantity((qty) => Math.max(1, qty - 1))}
                className="border-line flex h-7 w-7 items-center justify-center rounded-full border text-sm font-bold"
                aria-label="Diminuir quantidade"
              >
                −
              </button>
              <span className="w-6 text-center text-sm font-bold">
                {quantity}
              </span>
              <button
                type="button"
                onClick={() => setQuantity((qty) => qty + 1)}
                className="border-line flex h-7 w-7 items-center justify-center rounded-full border text-sm font-bold"
                aria-label="Aumentar quantidade"
              >
                +
              </button>
            </div>
            <Button
              variant="outline"
              disabled={!canAddSelected}
              onClick={addSelectedProduct}
              className="px-4 py-2 text-[13px]"
            >
              Adicionar ao pedido
            </Button>
          </div>
          {stepsQuery.isSuccess ? (
            <p className="text-right text-sm font-semibold">
              Total deste item:{" "}
              {selectedUnitCents === null
                ? "Preço indisponível"
                : currencyFormatter.format(
                    (selectedUnitCents * quantity) / 100,
                  )}
              {selectedUnitCents === 0 ? (
                <span className="text-red block text-xs">
                  Valor zero: confira o cadastro antes de vender.
                </span>
              ) : null}
            </p>
          ) : null}
        </div>
      ) : null}
      {cart.length > 0 ? (
        <div className="border-line flex flex-col gap-3 border-t pt-3">
          <h4 className="text-sm font-semibold">Pedido em preparação</h4>
          {cart.map((item) => {
            const product = productById.get(item.productId);
            return (
              <div
                key={item.lineId}
                className="flex items-center justify-between gap-3 text-sm"
              >
                <span className="flex flex-col">
                  <span>
                    {item.quantity} × {product?.name ?? "Produto indisponível"}{" "}
                    ·{" "}
                    {currencyFormatter.format(
                      (item.unitPriceCents * item.quantity) / 100,
                    )}
                  </span>
                  {item.optionLabels.length ? (
                    <span className="text-ink-soft text-xs">
                      {item.optionLabels.join(", ")}
                    </span>
                  ) : null}
                </span>
                <button
                  type="button"
                  onClick={() =>
                    setCart((current) =>
                      current.filter((entry) => entry.lineId !== item.lineId),
                    )
                  }
                  disabled={addItemMutation.isPending}
                  className="text-red text-xs font-semibold hover:underline"
                  aria-label={`Remover ${product?.name ?? "produto"} do pedido`}
                >
                  Remover
                </button>
              </div>
            );
          })}
          <p className="text-right text-sm font-semibold">
            Subtotal: {currencyFormatter.format(cartTotalCents / 100)}
          </p>
          <input
            type="text"
            placeholder="Observação do pedido (opcional)"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            maxLength={500}
            className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-[13px] outline-none"
          />
          <div className="flex flex-wrap gap-2">
            <Button
              disabled={addItemMutation.isPending}
              onClick={() => {
                receiveAfterRef.current = false;
                addItemMutation.mutate();
              }}
            >
              {addItemMutation.isPending
                ? "Confirmando..."
                : "Confirmar pedido"}
            </Button>
            <Button
              variant="outline"
              disabled={addItemMutation.isPending}
              onClick={() => {
                receiveAfterRef.current = true;
                addItemMutation.mutate();
              }}
            >
              Confirmar e receber
            </Button>
          </div>
        </div>
      ) : null}
      {errorMessage ? <p className="text-red text-sm">{errorMessage}</p> : null}
    </div>
  );
}

// FARELO-312 — walk-up counter sale (BALCAO), no comanda at all. Same
// product-search/quantity-stepper shape as AddItemPanel above (a small,
// deliberate duplication rather than a deeper shared-component refactor —
// AddItemPanel is tightly coupled to `commandNumber`/createOrder, and this
// panel has no comanda to key off of), but calls createBalcaoOrder instead
// of createOrder, and shows a plain success confirmation instead of
// closing into a comanda detail panel — there is no PDV screen for a
// BALCAO order to land on yet (see the backend's Order javadoc, "Design
// decision — BALCAO", for the known payment-flow follow-up this doesn't
// solve).
function BalcaoPanel({ onClose }: { onClose: () => void }) {
  const [search, setSearch] = useState("");
  const [selectedProductId, setSelectedProductId] = useState<string | null>(
    null,
  );
  const [quantity, setQuantity] = useState(1);
  const [note, setNote] = useState("");
  const [lastCreated, setLastCreated] = useState<Order | null>(null);
  const orderIntent = useRef<OrderIntent | null>(null);

  const productsQuery = useQuery({
    queryKey: ["pdv", "products"],
    queryFn: listProducts,
  });
  const categoriesQuery = useQuery({
    queryKey: ["pdv", "categories"],
    queryFn: listCategories,
  });

  const createMutation = useMutation({
    mutationFn: (product: Product) => {
      const draft = {
        items: [{ productId: product.id, quantity }],
        note: note.trim() || undefined,
        printOnConfirm: false,
      };
      orderIntent.current = orderIntentFor(draft, orderIntent.current);
      return createBalcaoOrder({
        ...draft,
        idempotencyKey: orderIntent.current.key,
      });
    },
    onSuccess: (order) => {
      orderIntent.current = null;
      setSelectedProductId(null);
      setQuantity(1);
      setNote("");
      setLastCreated(order);
    },
  });

  const errorMessage = apiErrorMessage(
    createMutation.error,
    "Não foi possível criar a venda balcão.",
  );

  const availableProducts = (productsQuery.data ?? []).filter(
    (product) =>
      product.active &&
      product.availableOnPos &&
      product.name.toLowerCase().includes(search.toLowerCase()),
  );
  const categoryNameById = new Map(
    (categoriesQuery.data ?? []).map((category: Category) => [
      category.id,
      category.name,
    ]),
  );
  const sections = new Map<string, Product[]>();
  for (const product of availableProducts) {
    const categoryName = categoryNameById.get(product.categoryId) ?? "Outros";
    sections.set(categoryName, [
      ...(sections.get(categoryName) ?? []),
      product,
    ]);
  }

  const selectedProduct = availableProducts.find(
    (product) => product.id === selectedProductId,
  );

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="font-serif text-xl font-semibold">Venda balcão</h2>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-soft hover:text-ink text-sm"
        >
          Fechar
        </button>
      </div>
      <p className="text-ink-soft text-sm">
        Cliente leva/recebe na hora — sem vincular a nenhuma comanda.
      </p>

      <input
        type="text"
        placeholder="Buscar produto..."
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
      />

      <div className="flex max-h-56 flex-col gap-3 overflow-y-auto">
        {productsQuery.isLoading ? (
          <p className="text-ink-faint text-sm">Carregando...</p>
        ) : null}
        {availableProducts.length === 0 && !productsQuery.isLoading ? (
          <p className="text-ink-faint text-sm">Nenhum produto encontrado.</p>
        ) : null}
        {Array.from(sections.entries()).map(([categoryName, products]) => (
          <div key={categoryName} className="flex flex-col gap-1">
            <div className="text-ink-faint text-[11px] font-bold tracking-wide uppercase">
              {categoryName}
            </div>
            {products.map((product) => (
              <button
                key={product.id}
                type="button"
                onClick={() => setSelectedProductId(product.id)}
                className={cn(
                  "flex items-center justify-between rounded-lg px-3 py-2 text-left text-sm",
                  selectedProductId === product.id
                    ? "bg-primary-soft text-primary-dark font-semibold"
                    : "hover:bg-bg-alt",
                )}
              >
                <span>{product.name}</span>
                <span className="font-semibold">
                  {currencyFormatter.format(product.price)}
                </span>
              </button>
            ))}
          </div>
        ))}
      </div>

      {selectedProduct ? (
        <div className="border-line flex flex-col gap-2 border-t pt-3">
          <div className="flex items-center gap-3">
            <span className="flex-1 text-sm font-semibold">
              {selectedProduct.name}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setQuantity((qty) => Math.max(1, qty - 1))}
                className="border-line flex h-7 w-7 items-center justify-center rounded-full border text-sm font-bold"
                aria-label="Diminuir quantidade"
              >
                −
              </button>
              <span className="w-6 text-center text-sm font-bold">
                {quantity}
              </span>
              <button
                type="button"
                onClick={() => setQuantity((qty) => qty + 1)}
                className="border-line flex h-7 w-7 items-center justify-center rounded-full border text-sm font-bold"
                aria-label="Aumentar quantidade"
              >
                +
              </button>
            </div>
            <Button
              disabled={createMutation.isPending}
              onClick={() => createMutation.mutate(selectedProduct)}
              className="px-4 py-2 text-[13px]"
            >
              {createMutation.isPending ? "Criando..." : "Criar venda"}
            </Button>
          </div>
          <input
            type="text"
            placeholder="Observação do pedido (opcional)"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            maxLength={500}
            className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-[13px] outline-none"
          />
        </div>
      ) : null}
      {errorMessage ? <p className="text-red text-sm">{errorMessage}</p> : null}
      {lastCreated ? (
        <p className="text-primary-dark bg-primary-soft rounded-lg px-3 py-2 text-sm font-semibold">
          Venda balcão criada — pedido enviado para a cozinha/bar.
        </p>
      ) : null}
    </Card>
  );
}

// FARELO-190/191 — displays/edits the comanda's central customer record
// (Command.customerName/customerPhone), not an inference from the latest
// order like this screen used to do. Read-modify-write against
// updateCommandCustomer (PATCH .../customer); the same record the QR
// checkout's write-through keeps updated (see docs/domain-model.md,
// seção command).
function formatPhone(digits: string): string {
  if (digits.length === 13 && digits.startsWith("55")) {
    return `+55 (${digits.slice(2, 4)}) ${digits.slice(4, 9)}-${digits.slice(9)}`;
  }
  if (digits.length === 12 && digits.startsWith("55")) {
    return `+55 (${digits.slice(2, 4)}) ${digits.slice(4, 8)}-${digits.slice(8)}`;
  }
  return digits;
}

function CustomerCard({
  commandNumber,
  command,
}: {
  commandNumber: number;
  command: Command;
}) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<{ customerName: string; customerPhone: string }>({
    defaultValues: {
      customerName: command.customerName ?? "",
      customerPhone: command.customerPhone ?? "",
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: { customerName: string; customerPhone: string }) =>
      updateCommandCustomer(commandNumber, {
        customerName: values.customerName.trim() || undefined,
        customerPhone: values.customerPhone.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber],
      });
      setEditing(false);
    },
  });

  const errorMessage = apiErrorMessage(
    updateMutation.error,
    "Não foi possível salvar o cliente.",
  );

  if (editing) {
    return (
      <form
        onSubmit={handleSubmit((values) => updateMutation.mutate(values))}
        noValidate
        className="border-line bg-bg flex flex-col gap-2.5 rounded-xl border px-4 py-3.5"
      >
        <div className="flex flex-wrap gap-2">
          <input
            placeholder="Nome (opcional)"
            className="border-line bg-surface focus:border-primary flex-1 rounded-lg border px-3 py-2 text-sm outline-none"
            {...register("customerName")}
          />
          <input
            placeholder="Telefone (opcional)"
            className="border-line bg-surface focus:border-primary flex-1 rounded-lg border px-3 py-2 text-sm outline-none"
            {...register("customerPhone")}
          />
        </div>
        {errorMessage ? (
          <p className="text-red text-sm">{errorMessage}</p>
        ) : null}
        <div className="flex gap-2">
          <Button
            type="submit"
            disabled={isSubmitting || updateMutation.isPending}
            className="px-4 py-2 text-[13px]"
          >
            {updateMutation.isPending ? "Salvando..." : "Salvar"}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              reset();
              setEditing(false);
            }}
            className="px-4 py-2 text-[13px]"
          >
            Cancelar
          </Button>
        </div>
      </form>
    );
  }

  if (!command.customerName && !command.customerPhone) {
    return (
      <button
        type="button"
        onClick={() => setEditing(true)}
        className="border-line text-ink-soft hover:border-primary/40 rounded-xl border border-dashed px-4 py-3.5 text-left text-sm"
      >
        + Adicionar cliente
      </button>
    );
  }

  const initials = command.customerName
    ? command.customerName
        .split(" ")
        .map((part) => part[0])
        .slice(0, 2)
        .join("")
        .toUpperCase()
    : "?";

  return (
    <div className="border-line bg-bg flex items-center gap-3.5 rounded-xl border px-4 py-3.5">
      <span className="bg-primary-soft text-primary-dark flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-serif text-sm font-semibold">
        {initials}
      </span>
      <div className="flex-1">
        <div className="font-serif text-[15px] font-semibold">
          {command.customerName ?? "Cliente sem nome"}
        </div>
        <div className="text-ink-soft text-[13px]">
          {command.customerPhone
            ? formatPhone(command.customerPhone)
            : "Sem telefone informado"}
        </div>
      </div>
      <button
        type="button"
        onClick={() => setEditing(true)}
        className="text-primary text-xs font-bold hover:underline"
      >
        Editar
      </button>
    </div>
  );
}

function PaymentPanel({
  commandNumber,
  totalOwed,
  totalDiscount,
  totalPaid,
  remaining,
  openSignal,
}: {
  commandNumber: number;
  totalOwed: number;
  totalDiscount: number;
  totalPaid: number;
  remaining: number;
  // FARELO-320: "Confirmar e Receber" — any change to this token (from
  // CommandDetail's paymentPrompt) opens the payment form, same as
  // clicking "Registrar pagamento" below. A counter, not a boolean: a
  // boolean flipped true->true (two "Confirmar e Receber" clicks in a
  // row while the form is already open, e.g. after closing it manually
  // and re-triggering) wouldn't produce a detectable change; a
  // monotonically increasing counter always does — see the render-time
  // comparison below for why this isn't a useEffect.
  openSignal: { token: number; amount: number | null };
}) {
  const queryClient = useQueryClient();
  const session = useSession();
  const userId = session ? decodeUserId(session.token) : null;
  const canReceivePayment =
    session?.role === "ADMIN" ||
    session?.role === "MANAGER" ||
    session?.role === "CASHIER";
  const supervisor = session?.role === "ADMIN" || session?.role === "MANAGER";
  const cashSessions = useQuery({
    queryKey: ["cash-sessions", userId],
    queryFn: listCashSessions,
    enabled: canReceivePayment && Boolean(userId),
  });
  const [chosenCashSessionId, setChosenCashSessionId] = useState("");
  const openCashSessions =
    cashSessions.data?.filter((item) => item.status === "OPEN") ?? [];
  const ownCashSession = openCashSessions.find(
    (item) => item.openedByUserId === userId,
  );
  const selectedCashSession = cashSessions.isError
    ? undefined
    : chosenCashSessionId
      ? openCashSessions.find((item) => item.id === chosenCashSessionId)
      : ownCashSession;
  const [formOpen, setFormOpen] = useState(false);
  // FARELO-225: shown after a CASH payment with change is confirmed by the
  // backend — persists past the form closing/resetting, since that's the
  // number the cashier still needs to hand back to the customer.
  const [lastChangeGiven, setLastChangeGiven] = useState<number | null>(null);
  const paymentIntent = useRef<PaymentIntent | null>(null);

  const paymentFormSchema = z
    .object({
      amount: z
        .string()
        .trim()
        .min(1, "Valor é obrigatório")
        .refine(
          (value) => parseMoneyCents(value) !== null,
          "Informe reais e até dois centavos",
        )
        .refine(
          (value) => (parseMoneyCents(value) ?? 0) > 0,
          "Valor deve ser maior que zero",
        ),
      method: z.enum(["PIX", "CREDIT_CARD", "DEBIT_CARD", "CASH", "OTHER"]),
      // FARELO-225 ("Tratar troco em dinheiro") — optional, CASH-only on
      // the wire (see recordPayment's input type); left as a plain string
      // here so the field can be empty without failing validation.
      amountReceived: z
        .string()
        .trim()
        .refine(
          (value) => value === "" || parseMoneyCents(value) !== null,
          "Informe reais e até dois centavos",
        )
        .optional(),
      institutionCnpj: z.string().trim().optional(),
      cardBrand: z.string().trim().optional(),
      authorizationCode: z.string().trim().optional(),
      receiverCnpj: z.string().trim().optional(),
      terminalId: z.string().trim().optional(),
    })
    .refine(
      (data) =>
        data.method !== "CASH" ||
        !data.amountReceived ||
        (parseMoneyCents(data.amountReceived) !== null &&
          (parseMoneyCents(data.amountReceived) ?? -1) >=
            (parseMoneyCents(data.amount) ?? Number.MAX_SAFE_INTEGER)),
      {
        message: "Dinheiro recebido deve ser maior ou igual ao valor",
        path: ["amountReceived"],
      },
    )
    .superRefine((data, context) => {
      const electronic = ["PIX", "CREDIT_CARD", "DEBIT_CARD"].includes(
        data.method,
      );
      if (!electronic) return;
      if (data.institutionCnpj && !/^[0-9]{14}$/.test(data.institutionCnpj)) {
        context.addIssue({
          code: "custom",
          message: "Informe 14 dígitos",
          path: ["institutionCnpj"],
        });
      }
      if (data.receiverCnpj && !/^[0-9]{14}$/.test(data.receiverCnpj)) {
        context.addIssue({
          code: "custom",
          message: "Informe 14 dígitos",
          path: ["receiverCnpj"],
        });
      }
      if (
        data.method !== "PIX" &&
        data.cardBrand &&
        !/^[0-9]{2}$/.test(data.cardBrand)
      ) {
        context.addIssue({
          code: "custom",
          message: "Informe o código de 2 dígitos",
          path: ["cardBrand"],
        });
      }
      if (data.authorizationCode && data.authorizationCode.length > 128) {
        context.addIssue({
          code: "custom",
          message: "Máximo de 128 caracteres",
          path: ["authorizationCode"],
        });
      }
      if (data.terminalId && data.terminalId.length > 40) {
        context.addIssue({
          code: "custom",
          message: "Máximo de 40 caracteres",
          path: ["terminalId"],
        });
      }
    });
  type PaymentFormValues = z.infer<typeof paymentFormSchema>;

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<PaymentFormValues>({
    resolver: zodResolver(paymentFormSchema),
    defaultValues: {
      amount: remaining > 0 ? String(remaining) : "",
      method: "PIX",
      amountReceived: "",
      institutionCnpj: "",
      cardBrand: "",
      authorizationCode: "",
      receiverCnpj: "",
      terminalId: "",
    },
  });

  // FARELO-320: "Confirmar e Receber" — "adjusting state when a prop
  // changes" pattern (React docs), not a useEffect: calling setState
  // inside an effect body here would trip this project's react-hooks/
  // set-state-in-effect lint rule (cascading-render risk) — comparing
  // against the previous value during render and updating state directly
  // is the sanctioned alternative for "run this when a prop actually
  // changes, not on every render". openSignal starts at 0 (no prompt
  // yet) and only increments from CommandDetail, so the first render
  // never matches this branch.
  const [seenOpenSignal, setSeenOpenSignal] = useState(openSignal.token);
  const [showBalanceWarning, setShowBalanceWarning] = useState(false);
  useEffect(() => {
    paymentIntent.current = null;
  }, [openSignal.token]);
  if (openSignal.token !== seenOpenSignal) {
    setSeenOpenSignal(openSignal.token);
    setShowBalanceWarning(openSignal.amount === null);
    reset({
      amount:
        openSignal.amount !== null && openSignal.amount > 0
          ? String(openSignal.amount)
          : "",
      method: "PIX",
      amountReceived: "",
      institutionCnpj: "",
      cardBrand: "",
      authorizationCode: "",
      receiverCnpj: "",
      terminalId: "",
    });
    setLastChangeGiven(null);
    setFormOpen(true);
  }

  // useWatch, not the form's own watch() — the latter returns a function
  // React Compiler can't safely memoize (see react-hooks/incompatible-
  // library), the same reasoning this project already applies elsewhere
  // for React-Compiler-era hook constraints.
  const method = useWatch({ control, name: "method" });
  const amount = useWatch({ control, name: "amount" });
  const amountReceived = useWatch({ control, name: "amountReceived" });

  // Live preview only — the authoritative changeGiven the cashier acts on
  // comes back from the backend in recordPaymentMutation's response (see
  // onSuccess below), not from this client-side arithmetic.
  const amountCents = parseMoneyCents(amount ?? "");
  const receivedCents = parseMoneyCents(amountReceived ?? "");
  const previewChange =
    method === "CASH" &&
    amountReceived &&
    amountCents !== null &&
    receivedCents !== null &&
    receivedCents >= amountCents
      ? (receivedCents - amountCents) / 100
      : null;

  const recordPaymentMutation = useMutation({
    mutationFn: (values: PaymentFormValues) => {
      if (!selectedCashSession) {
        throw new Error("Abra ou selecione um caixa antes de receber.");
      }
      const input = {
        amount: parseMoneyCents(values.amount)! / 100,
        method: values.method,
        cashSessionId: selectedCashSession.id,
        amountReceived:
          values.method === "CASH" && values.amountReceived
            ? parseMoneyCents(values.amountReceived)! / 100
            : undefined,
        fiscalDetails: ["PIX", "CREDIT_CARD", "DEBIT_CARD"].includes(
          values.method,
        )
          ? {
              integrationType: "2" as const,
              institutionCnpj: values.institutionCnpj || undefined,
              cardBrand:
                values.method === "PIX"
                  ? undefined
                  : values.cardBrand || undefined,
              authorizationCode: values.authorizationCode || undefined,
              receiverCnpj: values.receiverCnpj || undefined,
              terminalId: values.terminalId || undefined,
            }
          : undefined,
      };
      paymentIntent.current = paymentIntentFor(
        commandNumber,
        input,
        paymentIntent.current,
      );
      return recordPayment(commandNumber, input, paymentIntent.current.key);
    },
    onSuccess: (result) => {
      paymentIntent.current = null;
      reset();
      setFormOpen(false);
      setLastChangeGiven(result.changeGiven > 0 ? result.changeGiven : null);
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "paymentBalance"],
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
          {totalDiscount > 0 ? (
            <span className="text-red">
              Desconto · -{currencyFormatter.format(totalDiscount)}
            </span>
          ) : null}
          <span className="text-ink-soft">
            Pago · {currencyFormatter.format(totalPaid)}
          </span>
          <span className="text-primary-dark font-bold">
            Restante · {currencyFormatter.format(remaining)}
          </span>
        </div>
      </div>

      <DiscountButton commandNumber={commandNumber} totalOwed={totalOwed} />

      {lastChangeGiven !== null ? (
        <div className="border-line bg-primary-soft text-primary-dark rounded-2xl border p-3 text-center text-sm font-bold">
          Troco a devolver: {currencyFormatter.format(lastChangeGiven)}
        </div>
      ) : null}

      {showBalanceWarning && formOpen ? (
        <p role="alert" className="text-red text-sm">
          Pedido confirmado, mas não foi possível atualizar o saldo. Confira o
          valor antes de receber.
        </p>
      ) : null}

      {formOpen ? (
        <form
          onSubmit={handleSubmit((values) => {
            if (selectedCashSession) recordPaymentMutation.mutate(values);
          })}
          noValidate
          className="border-line bg-bg flex flex-col gap-3 rounded-2xl border p-4"
        >
          <div className="flex flex-col gap-2 text-sm">
            <span className="text-ink font-medium">Caixa do pagamento</span>
            {cashSessions.isPending && canReceivePayment ? (
              <span className="text-ink-soft">Buscando caixa aberto...</span>
            ) : null}
            {cashSessions.isError ? (
              <span className="text-red">
                {apiErrorMessage(
                  cashSessions.error,
                  "Não foi possível consultar os caixas.",
                )}
              </span>
            ) : null}
            {selectedCashSession ? (
              <span className="text-ink-soft">
                {selectedCashSession.shiftName} ·{" "}
                {selectedCashSession.openedByUserName}
                {selectedCashSession.id === ownCashSession?.id
                  ? " (seu caixa)"
                  : ""}
              </span>
            ) : null}
            {supervisor && openCashSessions.length > 0 ? (
              <select
                aria-label="Selecionar caixa aberto"
                value={selectedCashSession?.id ?? ""}
                onChange={(event) => setChosenCashSessionId(event.target.value)}
                className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
              >
                <option value="">
                  {ownCashSession ? "Meu caixa" : "Selecione um caixa"}
                </option>
                {openCashSessions.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.shiftName} · {item.openedByUserName}
                  </option>
                ))}
              </select>
            ) : null}
            {!selectedCashSession && !cashSessions.isPending ? (
              <span className="text-red">
                {canReceivePayment
                  ? "Nenhum caixa selecionado. Abra o seu caixa ou selecione um caixa aberto para registrar o pagamento."
                  : "Seu perfil não pode registrar pagamentos."}
              </span>
            ) : null}
            {canReceivePayment ? (
              <span className="flex gap-3">
                <Link
                  href="/pdv/caixa"
                  className="text-primary font-semibold hover:underline"
                >
                  Ir para o caixa
                </Link>
                <button
                  type="button"
                  onClick={() => cashSessions.refetch()}
                  className="text-primary font-semibold hover:underline"
                >
                  Atualizar caixas
                </button>
              </span>
            ) : null}
          </div>
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
          {method === "CASH" ? (
            <div className="flex flex-col gap-1">
              <label
                className="text-ink text-sm font-medium"
                htmlFor="amountReceived"
              >
                Dinheiro recebido (R$) · opcional
              </label>
              <input
                id="amountReceived"
                inputMode="decimal"
                placeholder="Deixe em branco se não houver troco"
                className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
                {...register("amountReceived")}
              />
              {errors.amountReceived ? (
                <p className="text-red text-sm">
                  {errors.amountReceived.message}
                </p>
              ) : null}
              {previewChange !== null ? (
                <p className="text-ink-soft text-sm">
                  Troco: {currencyFormatter.format(previewChange)}
                </p>
              ) : null}
            </div>
          ) : null}
          {["PIX", "CREDIT_CARD", "DEBIT_CARD"].includes(method) ? (
            <div className="border-line flex flex-col gap-3 rounded-lg border p-3">
              <p className="text-ink text-sm font-medium">
                Dados do comprovante para a NFC-e
              </p>
              <p className="text-ink-soft text-xs">
                Informe somente dados da transação. Nunca digite número do
                cartão ou CVV.
              </p>
              <p className="text-ink-soft text-xs">
                Processamento: maquininha ou app separado do PDV.
              </p>
              <div className="flex flex-col gap-1">
                <label className="text-ink text-sm" htmlFor="authorizationCode">
                  Código de autorização do comprovante
                </label>
                <input
                  id="authorizationCode"
                  className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
                  {...register("authorizationCode")}
                />
                {errors.authorizationCode ? (
                  <p className="text-red text-sm">
                    {errors.authorizationCode.message}
                  </p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-ink text-sm" htmlFor="institutionCnpj">
                  CNPJ da instituição de pagamento
                </label>
                <input
                  id="institutionCnpj"
                  inputMode="numeric"
                  placeholder="14 dígitos, se constar no comprovante"
                  className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
                  {...register("institutionCnpj")}
                />
                {errors.institutionCnpj ? (
                  <p className="text-red text-sm">
                    {errors.institutionCnpj.message}
                  </p>
                ) : null}
              </div>
              {method !== "PIX" ? (
                <div className="flex flex-col gap-1">
                  <label className="text-ink text-sm" htmlFor="cardBrand">
                    Código fiscal da bandeira
                  </label>
                  <input
                    id="cardBrand"
                    inputMode="numeric"
                    placeholder="2 dígitos, se confirmado"
                    className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
                    {...register("cardBrand")}
                  />
                  {errors.cardBrand ? (
                    <p className="text-red text-sm">
                      {errors.cardBrand.message}
                    </p>
                  ) : null}
                </div>
              ) : null}
              <div className="flex flex-col gap-1">
                <label className="text-ink text-sm" htmlFor="terminalId">
                  Identificador do terminal · opcional
                </label>
                <input
                  id="terminalId"
                  className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
                  {...register("terminalId")}
                />
                {errors.terminalId ? (
                  <p className="text-red text-sm">
                    {errors.terminalId.message}
                  </p>
                ) : null}
              </div>
            </div>
          ) : null}
          {errorMessage ? (
            <p className="text-red text-sm">{errorMessage}</p>
          ) : null}
          <div className="flex gap-2">
            <Button
              type="submit"
              disabled={recordPaymentMutation.isPending || !selectedCashSession}
              className="flex-1"
            >
              {recordPaymentMutation.isPending ? "Registrando..." : "Confirmar"}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                paymentIntent.current = null;
                setShowBalanceWarning(false);
                setFormOpen(false);
              }}
            >
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <div className="border-line bg-surface flex flex-col gap-2 rounded-2xl border p-4">
          <Button
            disabled={remaining <= 0 || !canReceivePayment}
            onClick={() => {
              // useForm's `defaultValues` are only read once, at mount —
              // this component mounts before `remaining` settles (both
              // queries still loading), so re-seed the field here with
              // the real value at the moment the form actually opens.
              reset({
                amount: String(remaining),
                method: "PIX",
                amountReceived: "",
              });
              setLastChangeGiven(null);
              setShowBalanceWarning(false);
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

// FARELO-230/231/232: applies a fixed-amount or percentage discount to the
// comanda. amount/percentage are mutually exclusive on the wire (see
// ApplyDiscountInput) — this form only ever sends the one matching the
// selected type.
function DiscountButton({
  commandNumber,
  totalOwed,
}: {
  commandNumber: number;
  totalOwed: number;
}) {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);

  const discountFormSchema = z
    .object({
      type: z.enum(["FIXED_AMOUNT", "PERCENTAGE"]),
      value: z
        .string()
        .trim()
        .min(1, "Valor é obrigatório")
        .refine((value) => !Number.isNaN(Number(value)), "Valor inválido")
        .refine((value) => Number(value) > 0, "Valor deve ser maior que zero"),
      reason: z.string().trim().optional(),
    })
    .refine((data) => data.type !== "PERCENTAGE" || Number(data.value) <= 100, {
      message: "Percentual não pode passar de 100",
      path: ["value"],
    });
  type DiscountFormValues = z.infer<typeof discountFormSchema>;

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<DiscountFormValues>({
    resolver: zodResolver(discountFormSchema),
    defaultValues: { type: "FIXED_AMOUNT", value: "", reason: "" },
  });

  const type = useWatch({ control, name: "type" });

  const applyDiscountMutation = useMutation({
    mutationFn: (values: DiscountFormValues) => {
      const input: Parameters<typeof applyDiscount>[1] =
        values.type === "PERCENTAGE"
          ? {
              type: "PERCENTAGE",
              percentage: Number(values.value),
              reason: values.reason || undefined,
            }
          : {
              type: "FIXED_AMOUNT",
              amount: Number(values.value),
              reason: values.reason || undefined,
            };
      return applyDiscount(commandNumber, input);
    },
    onSuccess: () => {
      reset();
      setFormOpen(false);
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "paymentBalance"],
      });
    },
  });

  const errorMessage = apiErrorMessage(
    applyDiscountMutation.error,
    "Não foi possível aplicar o desconto.",
  );

  if (!formOpen) {
    return (
      <Button
        variant="outline"
        disabled={totalOwed <= 0}
        onClick={() => setFormOpen(true)}
      >
        Aplicar desconto
      </Button>
    );
  }

  return (
    <form
      onSubmit={handleSubmit((values) => applyDiscountMutation.mutate(values))}
      noValidate
      className="border-line bg-bg flex flex-col gap-3 rounded-2xl border p-4"
    >
      <div className="flex flex-col gap-1">
        <label className="text-ink text-sm font-medium" htmlFor="discountType">
          Tipo de desconto
        </label>
        <select
          id="discountType"
          className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
          {...register("type")}
        >
          <option value="FIXED_AMOUNT">Valor fixo (R$)</option>
          <option value="PERCENTAGE">Percentual (%)</option>
        </select>
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-ink text-sm font-medium" htmlFor="discountValue">
          {type === "PERCENTAGE" ? "Percentual (%)" : "Valor (R$)"}
        </label>
        <input
          id="discountValue"
          inputMode="decimal"
          className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
          {...register("value")}
        />
        {errors.value ? (
          <p className="text-red text-sm">{errors.value.message}</p>
        ) : null}
      </div>
      <div className="flex flex-col gap-1">
        <label
          className="text-ink text-sm font-medium"
          htmlFor="discountReason"
        >
          Motivo · opcional
        </label>
        <input
          id="discountReason"
          className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
          {...register("reason")}
        />
      </div>
      {errorMessage ? <p className="text-red text-sm">{errorMessage}</p> : null}
      <div className="flex gap-2">
        <Button
          type="submit"
          disabled={applyDiscountMutation.isPending}
          className="flex-1"
        >
          {applyDiscountMutation.isPending ? "Aplicando..." : "Confirmar"}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            reset();
            setFormOpen(false);
          }}
        >
          Cancelar
        </Button>
      </div>
    </form>
  );
}

// FARELO-314 — "Garçom/atendente responsável pelo pedido", removível e
// trocável (docs/gaps-sischef-pdv.md item 4). A plain <select> populated
// from GET /api/v1/users (now also readable by CASHIER/ATTENDANT, see the
// backend's UserController#list javadoc) — the empty option unassigns.
function WaiterPicker({
  order,
  ordersQueryKey,
}: {
  order: Order;
  ordersQueryKey: (string | number)[];
}) {
  const queryClient = useQueryClient();

  const usersQuery = useQuery({
    queryKey: ["pdv", "users"],
    queryFn: listUsers,
  });

  const assignMutation = useMutation({
    mutationFn: (userId: string | null) => assignOrderWaiter(order.id, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ordersQueryKey });
    },
  });

  const errorMessage = apiErrorMessage(
    assignMutation.error,
    "Não foi possível atribuir o atendente.",
  );

  const activeUsers = (usersQuery.data ?? []).filter((user) => user.active);

  return (
    <div className="mt-2 flex items-center gap-2">
      <span className="text-ink-faint text-[11px] font-bold tracking-wide uppercase">
        Atendente
      </span>
      <select
        value={order.assignedUserId ?? ""}
        onChange={(event) => assignMutation.mutate(event.target.value || null)}
        disabled={assignMutation.isPending}
        className="border-line bg-surface rounded-md border px-2 py-1 text-[13px] outline-none"
      >
        <option value="">Sem atendente</option>
        {activeUsers.map((user) => (
          <option key={user.id} value={user.id}>
            {user.name}
          </option>
        ))}
      </select>
      {errorMessage ? (
        <span className="text-red text-[12px]">{errorMessage}</span>
      ) : null}
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

  // FARELO-200/201: a cancelled item drops out of the subtotal, same
  // exclusion as the comanda-level totalOwed above.
  const subtotal = order.items
    .filter((item) => !item.cancelled)
    .reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);

  // FARELO-319: "Copiar resumo do pedido" — pure client-side formatting,
  // no backend endpoint (the data is already all here in `order`).
  const [summaryCopied, setSummaryCopied] = useState(false);
  const copySummary = () => {
    const lines = order.items
      .filter((item) => !item.cancelled)
      .map(
        (item) =>
          `${item.quantity}x ${item.productName} — ${currencyFormatter.format(item.unitPrice * item.quantity)}`,
      );
    const header =
      order.commandNumber != null
        ? `Comanda ${order.commandNumber}`
        : "Venda balcão";
    const summary = [
      header,
      ...lines,
      `Subtotal: ${currencyFormatter.format(subtotal)}`,
    ].join("\n");
    navigator.clipboard.writeText(summary).then(() => {
      setSummaryCopied(true);
      setTimeout(() => setSummaryCopied(false), 2000);
    });
  };

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

      <div className="mt-1 flex items-center gap-3">
        <button
          type="button"
          onClick={copySummary}
          className="text-ink-soft text-[12px] font-semibold hover:opacity-80"
        >
          {summaryCopied ? "Copiado!" : "Copiar resumo"}
        </button>
      </div>

      <WaiterPicker order={order} ordersQueryKey={ordersQueryKey} />

      {order.note ? (
        <p className="text-ink-soft bg-bg-alt mt-2 rounded-lg px-3 py-2 text-[13px] italic">
          &ldquo;{order.note}&rdquo;
        </p>
      ) : null}

      <ul className="border-line mt-2 flex flex-col gap-1 border-t pt-2">
        {order.items.map((item) => (
          <OrderItemRow
            key={item.id}
            item={item}
            orderId={order.id}
            commandNumber={commandNumber}
            // Item-level cancellation follows the same non-terminal-order
            // rule as whole-order cancellation (canCancel above) — see
            // OrderItemCancellationNotAllowedException's javadoc.
            canCancel={canCancel}
          />
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

// FARELO-200/201: one line item within an OrderCard, with its own
// cancel-with-reason flow. A separate component (not inline in OrderCard's
// map) because each row needs its own local "is the reason picker open"/
// selected-reason/description state, independent of every other row and
// of the order-level cancel confirmation above.
function OrderItemRow({
  item,
  orderId,
  commandNumber,
  canCancel,
}: {
  item: OrderItem;
  orderId: string;
  commandNumber: number;
  canCancel: boolean;
}) {
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState<OrderItemCancelReason | "">("");
  const [description, setDescription] = useState("");

  // FARELO-317: desconto por item — same inline toggle-and-mini-form shape
  // as the cancellation flow above, kept as its own independent piece of
  // state (a row can't be cancelling and discounting at once in this UI,
  // but nothing enforces that beyond not opening both toggles together).
  const [discounting, setDiscounting] = useState(false);
  const [discountType, setDiscountType] =
    useState<DiscountType>("FIXED_AMOUNT");
  const [discountValue, setDiscountValue] = useState("");
  const [discountReason, setDiscountReason] = useState("");

  const cancelItemMutation = useMutation({
    mutationFn: () => {
      if (!reason) {
        throw new Error("Selecione um motivo.");
      }
      return cancelOrderItem(
        orderId,
        item.id,
        reason,
        description || undefined,
      );
    },
    onSuccess: () => {
      setCancelling(false);
      setReason("");
      setDescription("");
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "orders"],
      });
    },
  });

  const cancelErrorMessage = apiErrorMessage(
    cancelItemMutation.error,
    "Não foi possível cancelar o item.",
  );

  const applyItemDiscountMutation = useMutation({
    mutationFn: () => {
      const value = Number(discountValue);
      const input =
        discountType === "PERCENTAGE"
          ? {
              type: "PERCENTAGE" as const,
              percentage: value,
              reason: discountReason || undefined,
            }
          : {
              type: "FIXED_AMOUNT" as const,
              amount: value,
              reason: discountReason || undefined,
            };
      return applyItemDiscount(orderId, item.id, input);
    },
    onSuccess: () => {
      setDiscounting(false);
      setDiscountValue("");
      setDiscountReason("");
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "orders"],
      });
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "paymentBalance"],
      });
    },
  });

  const discountErrorMessage = apiErrorMessage(
    applyItemDiscountMutation.error,
    "Não foi possível aplicar o desconto.",
  );
  const discountValueNumber = Number(discountValue);
  const canConfirmDiscount =
    discountValue.trim() !== "" &&
    !Number.isNaN(discountValueNumber) &&
    discountValueNumber > 0 &&
    (discountType !== "PERCENTAGE" || discountValueNumber <= 100);

  // FARELO-318: "Duplicar item" adds a new line for this product/quantity.
  const duplicateItemMutation = useMutation({
    mutationFn: () => duplicateOrderItem(orderId, item.id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["pdv", "command", commandNumber, "orders"],
      });
    },
  });

  const duplicateErrorMessage = apiErrorMessage(
    duplicateItemMutation.error,
    "Não foi possível duplicar o item.",
  );
  // description is required only when reason is OTHER (mirrors
  // OrderItemCancelRequest's @AssertTrue on the backend).
  const descriptionRequired = reason === "OTHER";
  const canConfirm =
    reason !== "" && (!descriptionRequired || description.trim() !== "");

  if (item.cancelled) {
    return (
      <li className="flex flex-col gap-0.5 text-sm">
        <div className="flex justify-between">
          <span className="text-ink-soft line-through">
            {item.quantity}× {item.productName}
          </span>
          <span className="text-ink-soft font-semibold line-through">
            {currencyFormatter.format(item.unitPrice * item.quantity)}
          </span>
        </div>
        <span className="text-ink-soft text-[12px]">
          Cancelado
          {item.cancelledByUserName ? ` por ${item.cancelledByUserName}` : ""}
          {item.cancelReason
            ? ` · ${ITEM_CANCEL_REASON_LABEL[item.cancelReason]}`
            : ""}
          {item.cancelDescription ? ` — ${item.cancelDescription}` : ""}
        </span>
      </li>
    );
  }

  return (
    <li className="flex flex-col gap-1 text-sm">
      <div className="flex items-center justify-between gap-2">
        <span>
          {item.quantity}× {item.productName}
        </span>
        <div className="flex items-center gap-2">
          <span className="font-semibold">
            {currencyFormatter.format(item.unitPrice * item.quantity)}
          </span>
          {canCancel ? (
            <button
              type="button"
              disabled={duplicateItemMutation.isPending}
              onClick={() => duplicateItemMutation.mutate()}
              className="text-ink-soft text-[12px] font-semibold hover:opacity-80"
            >
              {duplicateItemMutation.isPending ? "Duplicando..." : "Duplicar"}
            </button>
          ) : null}
          {canCancel ? (
            <button
              type="button"
              onClick={() => setDiscounting((current) => !current)}
              className="text-primary text-[12px] font-semibold hover:opacity-80"
            >
              {discounting ? "Fechar" : "Desconto"}
            </button>
          ) : null}
          {canCancel ? (
            <button
              type="button"
              onClick={() => setCancelling((current) => !current)}
              className="text-red text-[12px] font-semibold hover:opacity-80"
            >
              {cancelling ? "Fechar" : "Cancelar item"}
            </button>
          ) : null}
        </div>
      </div>

      {discounting ? (
        <div className="bg-bg-alt flex flex-col gap-2 rounded-lg p-2">
          <div className="flex gap-2">
            <select
              value={discountType}
              onChange={(event) =>
                setDiscountType(event.target.value as DiscountType)
              }
              className="border-line rounded-md border bg-transparent px-2 py-1.5 text-[13px]"
            >
              <option value="FIXED_AMOUNT">Valor fixo (R$)</option>
              <option value="PERCENTAGE">Percentual (%)</option>
            </select>
            <input
              type="text"
              inputMode="decimal"
              value={discountValue}
              onChange={(event) => setDiscountValue(event.target.value)}
              placeholder={discountType === "PERCENTAGE" ? "%" : "R$"}
              className="border-line w-20 rounded-md border bg-transparent px-2 py-1.5 text-[13px]"
            />
          </div>
          <input
            type="text"
            value={discountReason}
            onChange={(event) => setDiscountReason(event.target.value)}
            placeholder="Motivo (opcional)"
            className="border-line rounded-md border bg-transparent px-2 py-1.5 text-[13px]"
          />
          <div className="flex items-center gap-2">
            <Button
              disabled={
                !canConfirmDiscount || applyItemDiscountMutation.isPending
              }
              onClick={() => applyItemDiscountMutation.mutate()}
              className="px-3 py-1.5 text-[12px]"
            >
              {applyItemDiscountMutation.isPending
                ? "Aplicando..."
                : "Confirmar"}
            </Button>
            <Button
              variant="outline"
              disabled={applyItemDiscountMutation.isPending}
              onClick={() => setDiscounting(false)}
              className="px-3 py-1.5 text-[12px]"
            >
              Voltar
            </Button>
          </div>
          {discountErrorMessage ? (
            <p className="text-red text-[12px]">{discountErrorMessage}</p>
          ) : null}
        </div>
      ) : null}

      {cancelling ? (
        <div className="bg-bg-alt flex flex-col gap-2 rounded-lg p-2">
          <select
            value={reason}
            onChange={(event) =>
              setReason(event.target.value as OrderItemCancelReason)
            }
            className="border-line rounded-md border bg-transparent px-2 py-1.5 text-[13px]"
          >
            <option value="">Selecione o motivo</option>
            {Object.entries(ITEM_CANCEL_REASON_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          {descriptionRequired ? (
            <input
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Descreva o motivo (obrigatório)"
              className="border-line rounded-md border bg-transparent px-2 py-1.5 text-[13px]"
            />
          ) : null}
          <div className="flex items-center gap-2">
            <Button
              variant="danger"
              disabled={!canConfirm || cancelItemMutation.isPending}
              onClick={() => cancelItemMutation.mutate()}
              className="px-3 py-1.5 text-[12px]"
            >
              {cancelItemMutation.isPending ? "Cancelando..." : "Confirmar"}
            </Button>
            <Button
              variant="outline"
              disabled={cancelItemMutation.isPending}
              onClick={() => setCancelling(false)}
              className="px-3 py-1.5 text-[12px]"
            >
              Voltar
            </Button>
          </div>
          {cancelErrorMessage ? (
            <p className="text-red text-[12px]">{cancelErrorMessage}</p>
          ) : null}
        </div>
      ) : null}

      {duplicateErrorMessage ? (
        <p className="text-red text-[12px]">{duplicateErrorMessage}</p>
      ) : null}
    </li>
  );
}
