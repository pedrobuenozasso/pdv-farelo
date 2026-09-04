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
import { listCategories, type Category } from "@/lib/api/categories";
import { apiErrorMessage } from "@/lib/api/client";
import {
  closeCommand,
  getCommand,
  openCommand,
  updateCommandCustomer,
  type Command,
  type CommandStatus,
} from "@/lib/api/commands";
import {
  cancelOrderItem,
  createOrder,
  listCommandOrders,
  markOrderCancelled,
  markOrderDelivered,
  type Order,
  type OrderItem,
  type OrderItemCancelReason,
  type OrderStatus,
} from "@/lib/api/orders";
import {
  getPaymentBalance,
  recordPayment,
  type PaymentMethod,
} from "@/lib/api/payments";
import { printConference } from "@/lib/api/print-jobs";
import { listProducts, type Product } from "@/lib/api/products";
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
  const [addItemOpen, setAddItemOpen] = useState(false);

  const commandQuery = useQuery({
    queryKey: ["pdv", "command", number],
    queryFn: () => getCommand(number),
  });

  const ordersQuery = useQuery({
    queryKey: ["pdv", "command", number, "orders"],
    queryFn: () => listCommandOrders(number),
  });

  const balanceQuery = useQuery({
    queryKey: ["pdv", "command", number, "paymentBalance"],
    queryFn: () => getPaymentBalance(number),
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

  // FARELO-223: totalOwed/totalPaid/remaining all come from the backend
  // (GET .../payments/balance) — no client-side sum/subtraction anymore,
  // per that ticket's "backend deve ser fonte de verdade" requirement.
  const totalOwed = balanceQuery.data?.totalOwed ?? 0;
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

      {command ? (
        <CustomerCard commandNumber={number} command={command} />
      ) : null}

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
              onAdded={() =>
                // Partial-match invalidation: also catches "orders" and
                // "paymentBalance" (both nested under this same key prefix)
                // in one call — and the command status itself, since
                // adding the first item auto-opens an AVAILABLE comanda
                // (CommandService#openForOrdering) and the header badge
                // needs to reflect that.
                queryClient.invalidateQueries({
                  queryKey: ["pdv", "command", number],
                })
              }
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
            totalPaid={totalPaid}
            remaining={remaining}
          />
        ) : null}
      </div>
    </Card>
  );
}

// FARELO-182/183 — lançamento manual de item pelo caixa, sem depender do
// cardápio QR. Reaproveita POST /api/v1/orders (já público e genérico —
// não exige customerName/customerPhone), o mesmo endpoint que o cardápio
// já usa; cada clique em "Adicionar" gera um pedido próprio de um item só
// (mais parecido com "bater no caixa" do que um carrinho), o suficiente
// pro caso de uso do ticket ("Comanda 37 + 1x Água").
function AddItemPanel({
  commandNumber,
  onAdded,
}: {
  commandNumber: number;
  onAdded: () => void;
}) {
  const [search, setSearch] = useState("");
  const [selectedProductId, setSelectedProductId] = useState<string | null>(
    null,
  );
  const [quantity, setQuantity] = useState(1);

  const productsQuery = useQuery({
    queryKey: ["pdv", "products"],
    queryFn: listProducts,
  });
  const categoriesQuery = useQuery({
    queryKey: ["pdv", "categories"],
    queryFn: listCategories,
  });

  const addItemMutation = useMutation({
    mutationFn: (product: Product) =>
      createOrder({
        commandNumber,
        items: [{ productId: product.id, quantity }],
      }),
    onSuccess: () => {
      setSelectedProductId(null);
      setQuantity(1);
      onAdded();
    },
  });

  const errorMessage = apiErrorMessage(
    addItemMutation.error,
    "Não foi possível adicionar o item.",
  );

  // Só produtos ativos e habilitados pro caixa (availableOnPos) — mesmo
  // filtro que o cardápio QR já aplica pro seu próprio flag
  // (availableOnMenu), FARELO-181.
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
    <div className="border-line bg-bg flex flex-col gap-3 rounded-2xl border p-4">
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
        <div className="border-line flex items-center gap-3 border-t pt-3">
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
            disabled={addItemMutation.isPending}
            onClick={() => addItemMutation.mutate(selectedProduct)}
            className="px-4 py-2 text-[13px]"
          >
            {addItemMutation.isPending ? "Adicionando..." : "Adicionar"}
          </Button>
        </div>
      ) : null}
      {errorMessage ? <p className="text-red text-sm">{errorMessage}</p> : null}
    </div>
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
          <span className="text-ink-soft">
            Pago · {currencyFormatter.format(totalPaid)}
          </span>
          <span className="text-primary-dark font-bold">
            Restante · {currencyFormatter.format(remaining)}
          </span>
        </div>
      </div>

      <PrintConferenceButton commandNumber={commandNumber} />

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

// FARELO-211/212: queues a COMMAND_CHECK PrintJob for the comanda — the
// actual printing happens asynchronously via the Edge Agent (same as
// every kitchen ticket), so this button only confirms the job was queued,
// not that paper has come out of a printer yet.
function PrintConferenceButton({ commandNumber }: { commandNumber: number }) {
  const printConferenceMutation = useMutation({
    mutationFn: () => printConference(commandNumber),
  });

  const errorMessage = apiErrorMessage(
    printConferenceMutation.error,
    "Não foi possível enviar a conferência para impressão.",
  );

  return (
    <div className="flex flex-col gap-1.5">
      <Button
        variant="outline"
        disabled={printConferenceMutation.isPending}
        onClick={() => printConferenceMutation.mutate()}
      >
        {printConferenceMutation.isPending
          ? "Enviando..."
          : "Imprimir Conferência"}
      </Button>
      {printConferenceMutation.isSuccess ? (
        <p className="text-ink-faint text-center text-xs">
          Conferência enviada para impressão.
        </p>
      ) : null}
      {errorMessage ? (
        <p className="text-red text-center text-xs">{errorMessage}</p>
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

  const cancelItemMutation = useMutation({
    mutationFn: () => {
      if (!reason) {
        throw new Error("Selecione um motivo.");
      }
      return cancelOrderItem(orderId, item.id, reason, description || undefined);
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

  // description is required only when reason is OTHER (mirrors
  // OrderItemCancelRequest's @AssertTrue on the backend).
  const descriptionRequired = reason === "OTHER";
  const canConfirm = reason !== "" && (!descriptionRequired || description.trim() !== "");

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
              onClick={() => setCancelling((current) => !current)}
              className="text-red text-[12px] font-semibold hover:opacity-80"
            >
              {cancelling ? "Fechar" : "Cancelar item"}
            </button>
          ) : null}
        </div>
      </div>

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
    </li>
  );
}
