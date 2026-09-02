import { ApiError } from "@/lib/api/client";
import { listCategories } from "@/lib/api/categories";
import {
  getCommand,
  type Command,
  type CommandStatus,
} from "@/lib/api/commands";
import { listProducts } from "@/lib/api/products";

import { Menu, type MenuSection } from "./menu";

// Server Component: fetches the comanda e o cardápio durante o SSR — mais
// simples que "use client" + TanStack Query aqui, já que a página em si
// não precisa reagir a nada além do resultado dessas buscas. FARELO-044
// adicionou o carrinho, que *precisa* de estado no cliente (quantidade por
// item, total); em vez de converter a página inteira, só a parte
// interativa foi extraída para um Client Component (`Menu`, em
// ./menu.tsx), que recebe `sections` já carregado via SSR como prop — o
// restante (busca da comanda, mensagens de erro/status) continua Server
// Component.

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

// Cardápio só faz sentido quando a comanda está num status "utilizável" —
// nos demais (fechando a conta, encerrada, bloqueada) mostramos só o
// STATUS_LABEL acima, sem produtos.
const MENU_VISIBLE_STATUSES = new Set<CommandStatus>(["AVAILABLE", "OPEN"]);

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

type MenuLookupResult =
  { outcome: "loaded"; sections: MenuSection[] } | { outcome: "error" };

// GET /api/v1/categories and GET /api/v1/products return everything, with
// no active/availableOnMenu filter — there's no public, pre-filtered
// endpoint yet. Filtering here on the frontend is a deliberate,
// documented call for now (small, non-sensitive data); a public filtered
// endpoint is a natural candidate if the catalog grows a lot.
async function loadMenu(): Promise<MenuLookupResult> {
  try {
    const [categories, products] = await Promise.all([
      listCategories(),
      listProducts(),
    ]);

    const visibleProducts = products.filter(
      (product) => product.active && product.availableOnMenu,
    );

    const sections = categories
      .map((category) => ({
        category,
        products: visibleProducts.filter(
          (product) => product.categoryId === category.id,
        ),
      }))
      .filter((section) => section.products.length > 0);

    return { outcome: "loaded", sections };
  } catch {
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

function CommandHeader({ command }: { command: Command }) {
  return (
    <div className="flex flex-col items-center gap-1 text-center">
      <p className="text-sm text-zinc-500 dark:text-zinc-400">Bem-vindo(a)!</p>
      <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
        Comanda {command.number}
      </h1>
      <p className="max-w-sm text-sm text-zinc-600 dark:text-zinc-400">
        {STATUS_LABEL[command.status]}
      </p>
    </div>
  );
}

// Comanda encontrada, mas num status onde o cardápio não se aplica
// (fechando a conta, encerrada, bloqueada) — só a confirmação/status.
function CommandStatusOnly({ command }: { command: Command }) {
  return (
    <main className="flex min-h-full flex-1 flex-col items-center justify-center gap-2 bg-zinc-50 p-8 dark:bg-black">
      <CommandHeader command={command} />
    </main>
  );
}

function MenuUnavailableMessage() {
  return (
    <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
      Não foi possível carregar o cardápio agora. Peça ajuda no balcão.
    </p>
  );
}

function EmptyMenuMessage() {
  return (
    <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
      O cardápio ainda não está disponível. Peça ajuda no balcão.
    </p>
  );
}

// Comanda encontrada e num status "utilizável" — mostra o cardápio.
function CommandWithMenu({
  command,
  menu,
}: {
  command: Command;
  menu: MenuLookupResult;
}) {
  return (
    <main className="mx-auto flex min-h-full w-full max-w-2xl flex-col gap-6 bg-zinc-50 p-6 dark:bg-black">
      <CommandHeader command={command} />
      {menu.outcome === "error" ? (
        <MenuUnavailableMessage />
      ) : menu.sections.length === 0 ? (
        <EmptyMenuMessage />
      ) : (
        <Menu sections={menu.sections} />
      )}
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
    case "not-found":
      return <NotFoundMessage />;
    case "error":
      return <GenericErrorMessage />;
    case "found": {
      const { command } = result;
      if (!MENU_VISIBLE_STATUSES.has(command.status)) {
        return <CommandStatusOnly command={command} />;
      }
      const menu = await loadMenu();
      return <CommandWithMenu command={command} menu={menu} />;
    }
  }
}
