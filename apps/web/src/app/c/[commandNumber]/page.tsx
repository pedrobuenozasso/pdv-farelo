import { ApiError } from "@/lib/api/client";
import {
  getCommand,
  type Command,
  type CommandStatus,
} from "@/lib/api/commands";

// Server Component: this page has no interactivity yet (no cardápio,
// nenhuma mutação) — só busca a comanda e exibe o resultado, então um
// fetch direto durante o SSR é mais simples que "use client" + TanStack
// Query aqui. Reavaliar quando FARELO-042/043 (cardápio) trouxer
// interatividade real para esta rota.

const MIN_COMMAND_NUMBER = 1;
const MAX_COMMAND_NUMBER = 100;

// Linguagem amigável ao cliente final (pedido.farelo.com.br) — nada de
// jargão de staff como "AVAILABLE"/"OPEN" cru.
const STATUS_LABEL: Record<CommandStatus, string> = {
  AVAILABLE: "Disponível — você já pode fazer seu pedido.",
  OPEN: "Em atendimento.",
  PAYMENT_REQUESTED: "Fechando a conta.",
  CLOSED: "Encerrada.",
  BLOCKED: "Indisponível no momento — peça ajuda no balcão.",
};

type CommandLookupResult =
  | { outcome: "found"; command: Command }
  | { outcome: "not-found" }
  | { outcome: "error" };

// Data fetching stays isolated in its own try/catch, with no JSX inside
// it — constructing JSX inside a try/catch trips
// react-hooks/error-boundaries (rendering errors aren't actually caught by
// a surrounding try/catch), so the component below only ever builds JSX
// from this function's plain return value.
async function lookupCommand(number: number): Promise<CommandLookupResult> {
  try {
    const command = await getCommand(number);
    return { outcome: "found", command };
  } catch (error) {
    if (error instanceof ApiError && error.code === "COMMAND_NOT_FOUND") {
      return { outcome: "not-found" };
    }
    return { outcome: "error" };
  }
}

function NotFoundMessage() {
  return (
    <main className="flex min-h-full flex-1 flex-col items-center justify-center gap-2 bg-zinc-50 p-8 text-center dark:bg-black">
      <h1 className="text-xl font-semibold text-black dark:text-zinc-50">
        Comanda não encontrada
      </h1>
      <p className="max-w-sm text-sm text-zinc-600 dark:text-zinc-400">
        Confira o número ou peça ajuda no balcão.
      </p>
    </main>
  );
}

function GenericErrorMessage() {
  return (
    <main className="flex min-h-full flex-1 flex-col items-center justify-center gap-2 bg-zinc-50 p-8 text-center dark:bg-black">
      <h1 className="text-xl font-semibold text-black dark:text-zinc-50">
        Não foi possível abrir sua comanda
      </h1>
      <p className="max-w-sm text-sm text-zinc-600 dark:text-zinc-400">
        Tente novamente em instantes ou peça ajuda no balcão.
      </p>
    </main>
  );
}

function CommandFound({ command }: { command: Command }) {
  return (
    <main className="flex min-h-full flex-1 flex-col items-center justify-center gap-2 bg-zinc-50 p-8 text-center dark:bg-black">
      <p className="text-sm text-zinc-500 dark:text-zinc-400">Bem-vindo(a)!</p>
      <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
        Comanda {command.number}
      </h1>
      <p className="max-w-sm text-sm text-zinc-600 dark:text-zinc-400">
        {STATUS_LABEL[command.status]}
      </p>
    </main>
  );
}

export default async function CommandPage({
  params,
}: PageProps<"/c/[commandNumber]">) {
  const { commandNumber } = await params;

  // Route params always arrive as a string — validate/convert before
  // hitting the API. A non-numeric or out-of-range value gets the same
  // "não encontrada" treatment as a real 404 from the backend, instead of
  // ever reaching `commandService.findByNumber` with a value the backend
  // wasn't designed for (its @PathVariable is a plain int).
  const parsedNumber = Number(commandNumber);
  const isValidNumber =
    Number.isInteger(parsedNumber) &&
    parsedNumber >= MIN_COMMAND_NUMBER &&
    parsedNumber <= MAX_COMMAND_NUMBER;

  if (!isValidNumber) {
    return <NotFoundMessage />;
  }

  const result = await lookupCommand(parsedNumber);

  switch (result.outcome) {
    case "found":
      return <CommandFound command={result.command} />;
    case "not-found":
      return <NotFoundMessage />;
    case "error":
      return <GenericErrorMessage />;
  }
}
