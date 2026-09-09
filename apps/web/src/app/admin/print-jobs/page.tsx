"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import {
  CommandCheckTicketPreview,
  KitchenTicketPreview,
} from "@/components/ticket-preview";
import { listPendingPrintJobs, type PrintJob } from "@/lib/api/print-jobs";

// Dado fixo, só para o modelo de exemplo abaixo — não vem da API. Existe
// para o Pedro poder conferir o layout do cupom a qualquer momento, mesmo
// sem nenhuma comanda pendente de impressão (pedido explícito: "um modelo
// que eu possa ver toda vez").
const EXAMPLE_KITCHEN_CONTENT = {
  commandNumber: 37,
  productionStation: "BAR" as const,
  items: [
    { productName: "Pão na Chapa com Ovos Mexidos e Bacon", quantity: 1 },
    { productName: "Café com Leite (Pingado)", quantity: 1 },
  ],
  customerName: "Rafa Teste",
  customerPhone: "(85) 98446-0585",
  orderCreatedAt: new Date().toISOString(),
};

const EXAMPLE_COMMAND_CHECK_CONTENT = {
  commandNumber: 37,
  items: [
    {
      productName: "Pão na Chapa com Ovos Mexidos e Bacon",
      quantity: 1,
      unitPrice: 28,
      lineTotal: 28,
    },
    {
      productName: "Café com Leite (Pingado)",
      quantity: 1,
      unitPrice: 16,
      lineTotal: 16,
    },
  ],
  total: 44,
};

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

// Poll like the KDS — this queue is meant to drain on its own as the Edge
// Agent prints each job, so a staff member watching this screen should
// see it move without refreshing.
const REFETCH_INTERVAL_MS = 5_000;

export default function PrintJobsAdminPage() {
  const queueQuery = useQuery({
    queryKey: ["print-jobs", "pending"],
    queryFn: listPendingPrintJobs,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div>
            <h1 className="font-serif text-2xl font-semibold">Impressão</h1>
            <p className="text-ink-soft mt-0.5 text-sm">
              {queueQuery.data?.length ?? 0} comandas aguardando impressão
            </p>
          </div>

          <div className="border-line bg-primary-soft text-primary-dark rounded-2xl border p-4 text-sm">
            Esta fila mostra só as comandas de impressão pendentes — o backend
            hoje não expõe uma lista de comandas que falharam ao imprimir, então
            não há como esta tela oferecer um botão de &ldquo;tentar
            novamente&rdquo; ainda. Isso exigiria um novo endpoint no backend
            (algo como filtrar por status).
          </div>

          <div className="border-line bg-surface rounded-2xl border p-5">
            <h2 className="font-serif text-lg font-semibold">
              Modelo de exemplo
            </h2>
            <p className="text-ink-soft mt-0.5 text-sm">
              Como as comandas saem na impressora hoje — cupom de pedido
              (cozinha/bar) e cupom de conferência (caixa). Dados fictícios, só
              para referência de layout.
            </p>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <KitchenTicketPreview content={EXAMPLE_KITCHEN_CONTENT} />
              <CommandCheckTicketPreview
                content={EXAMPLE_COMMAND_CHECK_CONTENT}
              />
            </div>
          </div>

          <div className="border-line bg-surface overflow-hidden rounded-2xl border">
            {queueQuery.isLoading ? (
              <p className="text-ink-faint p-5 text-sm">Carregando...</p>
            ) : null}
            {queueQuery.isError ? (
              <p className="text-red p-5 text-sm">
                Não foi possível carregar a fila de impressão.
              </p>
            ) : null}
            {queueQuery.data && queueQuery.data.length === 0 ? (
              <p className="text-ink-faint p-5 text-sm">
                Nenhuma comanda aguardando impressão.
              </p>
            ) : null}
            {queueQuery.data?.map((job) => (
              <PrintJobRow key={job.id} job={job} />
            ))}
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function PrintJobRow({ job }: { job: PrintJob }) {
  const [expanded, setExpanded] = useState(false);

  // FARELO-210/211: a COMMAND_CHECK job has no productionStation (it isn't
  // scoped to a production station) and its items carry prices — content
  // vs. commandCheckContent, branched by type (see PrintJob's javadoc on
  // the backend for why exactly one of each pair is populated).
  const isCommandCheck = job.type === "COMMAND_CHECK";
  const commandNumber = isCommandCheck
    ? job.commandNumber
    : job.content?.commandNumber;
  const summary = isCommandCheck
    ? `Conferência · ${job.commandCheckContent?.items
        .map((item) => `${item.quantity}× ${item.productName}`)
        .join(", ")}`
    : job.content?.items
        .map((item) => `${item.quantity}× ${item.productName}`)
        .join(", ");

  return (
    <div className="border-line border-t first:border-t-0">
      <button
        type="button"
        onClick={() => setExpanded((current) => !current)}
        className="flex w-full items-center gap-4 px-5 py-3.5 text-left"
      >
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold">
              Comanda {commandNumber}
            </span>
            {!isCommandCheck && job.content?.productionStation ? (
              <span className="text-ink-faint text-xs">
                {job.content.productionStation === "BAR" ? "Bar" : "Cozinha"}
              </span>
            ) : null}
          </div>
          <p className="text-ink-soft mt-0.5 text-sm">{summary}</p>
          <div className="text-ink-faint mt-1 text-xs">
            {dateTimeFormatter.format(new Date(job.createdAt))}
          </div>
        </div>
        {job.retryCount > 0 ? (
          <Badge tone="amber">{job.retryCount}ª tentativa</Badge>
        ) : null}
        <Badge tone="primary">Aguardando</Badge>
      </button>
      {expanded ? (
        <div className="border-line border-t px-5 py-4">
          {isCommandCheck && job.commandCheckContent ? (
            <CommandCheckTicketPreview content={job.commandCheckContent} />
          ) : null}
          {!isCommandCheck && job.content ? (
            <KitchenTicketPreview content={job.content} />
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
