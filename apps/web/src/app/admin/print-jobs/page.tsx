"use client";

import { useQuery } from "@tanstack/react-query";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import { listPendingPrintJobs, type PrintJob } from "@/lib/api/print-jobs";

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
    <AuthGuard>
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
  return (
    <div className="border-line flex items-center gap-4 border-t px-5 py-3.5 first:border-t-0">
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">
            Comanda {job.content.commandNumber}
          </span>
          {job.content.productionStation ? (
            <span className="text-ink-faint text-xs">
              {job.content.productionStation === "BAR" ? "Bar" : "Cozinha"}
            </span>
          ) : null}
        </div>
        <p className="text-ink-soft mt-0.5 text-sm">
          {job.content.items
            .map((item) => `${item.quantity}× ${item.productName}`)
            .join(", ")}
        </p>
        <div className="text-ink-faint mt-1 text-xs">
          {dateTimeFormatter.format(new Date(job.createdAt))}
        </div>
      </div>
      {job.retryCount > 0 ? (
        <Badge tone="amber">{job.retryCount}ª tentativa</Badge>
      ) : null}
      <Badge tone="primary">Aguardando</Badge>
    </div>
  );
}
