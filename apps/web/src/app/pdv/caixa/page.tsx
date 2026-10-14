"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { AuthGuard } from "@/components/auth-guard";
import { InternalNav } from "@/components/internal-nav";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  closeCashSession,
  listCashMovements,
  listCashSessions,
  listShifts,
  openCashSession,
  reconcileCashSession,
  recordCashMovement,
  type MovementAction,
} from "@/lib/api/cashier";
import { apiErrorMessage } from "@/lib/api/client";
import { decodeUserId } from "@/lib/auth";
import { useSession } from "@/lib/use-session";

const money = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});
const date = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});
const movementLabels: Record<MovementAction, string> = {
  withdrawals: "Sangria",
  supplies: "Suprimento",
  expenses: "Despesa",
  incomes: "Receita",
};
const typeLabels = {
  WITHDRAWAL: "Sangria",
  SUPPLY: "Suprimento",
  EXPENSE: "Despesa",
  INCOME: "Receita",
};

function validMoney(value: string, allowZero = false): boolean {
  return /^\d+(\.\d{1,2})?$/.test(value) && (allowZero || Number(value) > 0);
}

export default function CashierPage() {
  return (
    <AuthGuard allow={["ADMIN", "MANAGER", "CASHIER"]}>
      <InternalNav />
      <CashierContent />
    </AuthGuard>
  );
}

function CashierContent() {
  const queryClient = useQueryClient();
  const session = useSession();
  const userId = session ? decodeUserId(session.token) : null;
  const supervisor = session?.role === "ADMIN" || session?.role === "MANAGER";
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [shiftId, setShiftId] = useState("");
  const [action, setAction] = useState<MovementAction>("withdrawals");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [closingAmount, setClosingAmount] = useState("");
  const [movementError, setMovementError] = useState<string | null>(null);
  const [closingError, setClosingError] = useState<string | null>(null);

  const shifts = useQuery({ queryKey: ["shifts"], queryFn: listShifts });
  const sessions = useQuery({
    queryKey: ["cash-sessions"],
    queryFn: listCashSessions,
  });
  const activeShifts = shifts.data?.filter((shift) => shift.active) ?? [];
  const selected =
    sessions.data?.find((item) => item.id === selectedId) ??
    sessions.data?.find(
      (item) => item.openedByUserId === userId && item.status === "OPEN",
    ) ??
    sessions.data?.[0];
  const movements = useQuery({
    queryKey: ["cash-movements", selected?.id],
    queryFn: () => listCashMovements(selected!.id),
    enabled: Boolean(selected),
  });
  const canOperate = Boolean(
    selected && (supervisor || selected.openedByUserId === userId),
  );

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ["cash-sessions"] });
    await queryClient.invalidateQueries({ queryKey: ["cash-movements"] });
  };
  const openMutation = useMutation({
    mutationFn: openCashSession,
    onSuccess: async (opened) => {
      setSelectedId(opened.id);
      await refresh();
    },
  });
  const movementMutation = useMutation({
    mutationFn: () =>
      recordCashMovement(selected!.id, action, amount, description.trim()),
    onSuccess: async () => {
      setAmount("");
      setDescription("");
      await refresh();
    },
  });
  const closeMutation = useMutation({
    mutationFn: () => closeCashSession(selected!.id, closingAmount),
    onSuccess: async () => {
      setClosingAmount("");
      await refresh();
    },
  });
  const reconcileMutation = useMutation({
    mutationFn: () => reconcileCashSession(selected!.id),
    onSuccess: refresh,
  });

  return (
    <main className="mx-auto max-w-6xl space-y-7 p-6 md:p-8">
      <header>
        <p className="text-ink-soft text-sm">PDV / Operação</p>
        <h1 className="font-serif text-3xl font-semibold">Caixa</h1>
        <p className="text-ink-soft mt-1 text-sm">
          Abertura, movimentos, fechamento e conferência em etapas separadas.
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)]">
        <div className="space-y-6">
          <Card>
            <h2 className="font-serif text-xl font-semibold">Abrir caixa</h2>
            <p className="text-ink-soft mt-1 text-sm">
              Escolha um turno ativo. Cada operador pode manter um caixa aberto
              por vez.
            </p>
            <form
              className="mt-4 flex flex-wrap gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                const chosen = shiftId || activeShifts[0]?.id;
                if (chosen) openMutation.mutate(chosen);
              }}
            >
              <select
                aria-label="Turno"
                value={shiftId || activeShifts[0]?.id || ""}
                onChange={(event) => setShiftId(event.target.value)}
                className="border-line bg-surface min-w-40 flex-1 rounded-lg border px-3 py-2 text-sm"
              >
                {activeShifts.length === 0 ? (
                  <option value="">Nenhum turno ativo</option>
                ) : null}
                {activeShifts.map((shift) => (
                  <option key={shift.id} value={shift.id}>
                    {shift.name}
                  </option>
                ))}
              </select>
              <Button
                type="submit"
                disabled={!activeShifts.length || openMutation.isPending}
              >
                Abrir
              </Button>
            </form>
            {shifts.isError ? (
              <p className="text-red mt-3 text-sm">
                {apiErrorMessage(shifts.error, "Falha ao buscar turnos.")}
              </p>
            ) : null}
            {openMutation.isError ? (
              <p className="text-red mt-3 text-sm">
                {apiErrorMessage(openMutation.error, "Falha ao abrir caixa.")}
              </p>
            ) : null}
          </Card>

          <Card>
            <h2 className="font-serif text-xl font-semibold">
              Caixas recentes
            </h2>
            {sessions.isPending ? (
              <p className="text-ink-soft mt-3 text-sm">Carregando caixas...</p>
            ) : null}
            {sessions.isError ? (
              <p className="text-red mt-3 text-sm">
                {apiErrorMessage(sessions.error, "Falha ao buscar caixas.")}
              </p>
            ) : null}
            {sessions.data?.length === 0 ? (
              <p className="text-ink-soft mt-3 text-sm">
                Nenhum caixa registrado.
              </p>
            ) : null}
            <div className="mt-3 max-h-96 space-y-2 overflow-y-auto">
              {sessions.data?.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setSelectedId(item.id)}
                  className={`border-line w-full rounded-xl border p-3 text-left text-sm ${selected?.id === item.id ? "bg-primary-soft" : "hover:bg-bg-alt"}`}
                >
                  <span className="flex justify-between gap-2 font-semibold">
                    <span>{item.shiftName}</span>
                    <span>{item.status === "OPEN" ? "Aberto" : "Fechado"}</span>
                  </span>
                  <span className="text-ink-soft mt-1 block">
                    {item.openedByUserName} ·{" "}
                    {date.format(new Date(item.openedAt))}
                  </span>
                </button>
              ))}
            </div>
          </Card>
        </div>

        {selected ? (
          <div className="space-y-6">
            <Card>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-ink-soft text-sm">
                    Turno {selected.shiftName}
                  </p>
                  <h2 className="font-serif text-xl font-semibold">
                    Caixa de {selected.openedByUserName}
                  </h2>
                </div>
                <span className="bg-primary-soft rounded-full px-3 py-1 text-xs font-semibold">
                  {selected.status === "OPEN" ? "Aberto" : "Fechado"}
                </span>
              </div>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-ink-soft">Aberto em</dt>
                  <dd>{date.format(new Date(selected.openedAt))}</dd>
                </div>
                <div>
                  <dt className="text-ink-soft">Conferência</dt>
                  <dd>
                    {selected.reconciliationStatus === "PENDING"
                      ? "Pendente"
                      : selected.reconciliationStatus === "MATCHED"
                        ? "Conferido"
                        : "Diferença encontrada"}
                  </dd>
                </div>
                {selected.closingAmount !== null ? (
                  <div>
                    <dt className="text-ink-soft">Dinheiro informado</dt>
                    <dd>{money.format(selected.closingAmount)}</dd>
                  </div>
                ) : null}
                {selected.expectedAmount !== null ? (
                  <div>
                    <dt className="text-ink-soft">Dinheiro calculado</dt>
                    <dd>{money.format(selected.expectedAmount)}</dd>
                  </div>
                ) : null}
                {selected.reconciliationDifference !== null ? (
                  <div>
                    <dt className="text-ink-soft">Diferença</dt>
                    <dd>{money.format(selected.reconciliationDifference)}</dd>
                  </div>
                ) : null}
              </dl>
            </Card>

            {selected.status === "OPEN" && canOperate ? (
              <Card>
                <h2 className="font-serif text-xl font-semibold">
                  Movimento de caixa
                </h2>
                <form
                  className="mt-4 space-y-3"
                  onSubmit={(event) => {
                    event.preventDefault();
                    if (!validMoney(amount)) {
                      setMovementError(
                        "Informe um valor maior que zero, com até duas casas decimais.",
                      );
                      return;
                    }
                    setMovementError(null);
                    movementMutation.mutate();
                  }}
                >
                  <div className="grid gap-3 sm:grid-cols-2">
                    <select
                      aria-label="Tipo de movimento"
                      value={action}
                      onChange={(event) =>
                        setAction(event.target.value as MovementAction)
                      }
                      className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
                    >
                      {Object.entries(movementLabels).map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                    <input
                      aria-label="Valor do movimento"
                      type="text"
                      inputMode="decimal"
                      placeholder="Valor em R$ (ex.: 50.00)"
                      value={amount}
                      onChange={(event) =>
                        setAmount(event.target.value.replace(",", "."))
                      }
                      className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
                    />
                  </div>
                  <input
                    aria-label="Descrição do movimento"
                    placeholder="Descrição (opcional)"
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    className="border-line bg-surface w-full rounded-lg border px-3 py-2 text-sm"
                  />
                  <Button type="submit" disabled={movementMutation.isPending}>
                    Registrar {movementLabels[action].toLowerCase()}
                  </Button>
                  {movementError ? (
                    <p className="text-red text-sm">{movementError}</p>
                  ) : null}
                  {movementMutation.isError ? (
                    <p className="text-red text-sm">
                      {apiErrorMessage(
                        movementMutation.error,
                        "Falha ao registrar movimento.",
                      )}
                    </p>
                  ) : null}
                </form>
              </Card>
            ) : null}

            <Card>
              <h2 className="font-serif text-xl font-semibold">
                Movimentos registrados
              </h2>
              {movements.isPending ? (
                <p className="text-ink-soft mt-3 text-sm">
                  Carregando movimentos...
                </p>
              ) : null}
              {movements.isError ? (
                <p className="text-red mt-3 text-sm">
                  {apiErrorMessage(
                    movements.error,
                    "Falha ao buscar movimentos.",
                  )}
                </p>
              ) : null}
              {movements.data?.length === 0 ? (
                <p className="text-ink-soft mt-3 text-sm">
                  Nenhum movimento neste caixa.
                </p>
              ) : null}
              <ul className="mt-3 divide-y">
                {movements.data?.map((item) => (
                  <li
                    key={item.id}
                    className="flex justify-between gap-4 py-3 text-sm"
                  >
                    <div>
                      <p className="font-semibold">{typeLabels[item.type]}</p>
                      <p className="text-ink-soft">
                        {item.description || item.createdByUserName} ·{" "}
                        {date.format(new Date(item.createdAt))}
                      </p>
                    </div>
                    <span>{money.format(item.amount)}</span>
                  </li>
                ))}
              </ul>
            </Card>

            {selected.status === "OPEN" && canOperate ? (
              <Card>
                <h2 className="font-serif text-xl font-semibold">
                  Fechar caixa
                </h2>
                <p className="text-ink-soft mt-1 text-sm">
                  Informe o dinheiro físico contado. A conferência será feita
                  depois por administrador ou gerente.
                </p>
                <form
                  className="mt-4 flex flex-wrap gap-2"
                  onSubmit={(event) => {
                    event.preventDefault();
                    if (!validMoney(closingAmount, true)) {
                      setClosingError(
                        "Informe zero ou um valor positivo com até duas casas decimais.",
                      );
                      return;
                    }
                    setClosingError(null);
                    closeMutation.mutate();
                  }}
                >
                  <input
                    aria-label="Dinheiro contado no fechamento"
                    type="text"
                    inputMode="decimal"
                    placeholder="Dinheiro contado (ex.: 250.00)"
                    value={closingAmount}
                    onChange={(event) =>
                      setClosingAmount(event.target.value.replace(",", "."))
                    }
                    className="border-line bg-surface min-w-48 flex-1 rounded-lg border px-3 py-2 text-sm"
                  />
                  <Button
                    type="submit"
                    variant="outline"
                    disabled={closeMutation.isPending}
                  >
                    Fechar caixa
                  </Button>
                </form>
                {closingError ? (
                  <p className="text-red mt-2 text-sm">{closingError}</p>
                ) : null}
                {closeMutation.isError ? (
                  <p className="text-red mt-2 text-sm">
                    {apiErrorMessage(
                      closeMutation.error,
                      "Falha ao fechar caixa.",
                    )}
                  </p>
                ) : null}
              </Card>
            ) : null}

            {selected.status === "CLOSED" &&
            selected.reconciliationStatus === "PENDING" &&
            supervisor ? (
              <Card>
                <h2 className="font-serif text-xl font-semibold">
                  Conferência administrativa
                </h2>
                <p className="text-ink-soft mt-1 text-sm">
                  Compara o valor contado com pagamentos em dinheiro registrados
                  no período. Sangrias, suprimentos, despesas e receitas ainda
                  não entram nesse cálculo; revise os movimentos antes de
                  confirmar.
                </p>
                <Button
                  className="mt-4"
                  type="button"
                  disabled={reconcileMutation.isPending}
                  onClick={() => reconcileMutation.mutate()}
                >
                  Conferir caixa
                </Button>
                {reconcileMutation.isError ? (
                  <p className="text-red mt-2 text-sm">
                    {apiErrorMessage(
                      reconcileMutation.error,
                      "Falha ao conferir caixa.",
                    )}
                  </p>
                ) : null}
              </Card>
            ) : null}
          </div>
        ) : (
          <Card>
            <p className="text-ink-soft text-sm">
              Abra ou selecione um caixa para ver os detalhes.
            </p>
          </Card>
        )}
      </div>
    </main>
  );
}
