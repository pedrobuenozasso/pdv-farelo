/**
 * Atalhos de teclado do PDV, modelados a partir da operação real do PDV que a
 * cafeteria usa hoje (Sischef). O objetivo é memória muscular: a equipe lança
 * uma comanda inteira sem tirar a mão do teclado, e os atalhos de commit
 * (confirmar/receber) coincidem com os que já conhecem.
 *
 * <p>Mapa observado no PDV atual e replicado aqui (os que fazem sentido no
 * Farelo OS; adicionais/variações seguem o modelo de "passos" do backend):
 *
 * | Tecla            | Ação                                             |
 * | ---------------- | ------------------------------------------------ |
 * | Ctrl/Cmd + F     | Focar a busca de produto                         |
 * | ↑ / ↓            | Navegar a grade de produtos (mesmo digitando)    |
 * | Enter            | Selecionar o produto em foco, ou lançá-lo        |
 * | F10              | Confirmar o pedido (vai para produção)           |
 * | F9               | Confirmar e receber (abre o pagamento)           |
 * | Esc              | Limpar seleção / limpar busca                     |
 *
 * <p>Por que uma função pura em vez de tudo dentro do `useEffect`: a decisão
 * "qual ação esta tecla dispara, neste contexto" é a parte com regras (commit
 * só com carrinho, setas navegam até na busca, Ctrl+F sempre rouba o default
 * do navegador) e é o que vale revisar isolado. O hook abaixo só liga essa
 * decisão ao DOM. O projeto web não tem runner de teste — este recorte puro
 * existe para ser raciocinável à parte do React.
 */
import { useEffect } from "react";

export type PdvHotkeyAction =
  | { type: "focus-search" }
  | { type: "move-highlight"; delta: 1 | -1 }
  | { type: "activate-highlight" }
  | { type: "confirm" }
  | { type: "confirm-and-receive" }
  | { type: "clear" };

export type PdvHotkeyContext = {
  /** Há pelo menos um item no carrinho (habilita confirmar/receber). */
  hasCart: boolean;
  /** Um produto está selecionado aguardando "adicionar" (passos/quantidade). */
  hasSelection: boolean;
  /** Há produtos navegáveis na grade (habilita setas/Enter). */
  hasProducts: boolean;
};

type KeyboardLike = {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  /** `true` quando o foco está num campo de texto (ver `isTypingTarget`). */
  typing: boolean;
};

/**
 * Decide qual ação do PDV uma tecla dispara, ou `null` quando a tecla deve
 * seguir o comportamento padrão do navegador/campo.
 *
 * <p>Regras que não são óbvias:
 * <ul>
 *   <li><b>Setas funcionam mesmo digitando.</b> No PDV real "na busca, as setas
 *   navegam e Enter lança" — então ↑/↓ nunca ficam presas ao cursor do campo.</li>
 *   <li><b>Enter digitando só age quando há um produto em foco ou selecionado.</b>
 *   Sem isso, Enter no campo de busca vazio não deve fazer nada.</li>
 *   <li><b>F9/F10 e Ctrl+F agem em qualquer foco</b>, inclusive dentro da busca —
 *   é o ponto do fluxo "digito o último item e aperto F10".</li>
 *   <li><b>Commit exige carrinho.</b> F9/F10 sem itens não disparam nada, em vez
 *   de enviar um pedido vazio.</li>
 * </ul>
 */
export function resolvePdvHotkey(
  event: KeyboardLike,
  context: PdvHotkeyContext,
): PdvHotkeyAction | null {
  const mod = event.ctrlKey || event.metaKey;

  // Ctrl/Cmd+F — focar busca. Rouba o "localizar na página" do navegador de
  // propósito: no PDV, Ctrl+F é "buscar produto".
  if (mod && (event.key === "f" || event.key === "F")) {
    return { type: "focus-search" };
  }

  // F10 / F9 — pontos de commit. Só com carrinho.
  if (event.key === "F10") {
    return context.hasCart ? { type: "confirm" } : null;
  }
  if (event.key === "F9") {
    return context.hasCart ? { type: "confirm-and-receive" } : null;
  }

  // Setas navegam a grade, mesmo com foco na busca.
  if (event.key === "ArrowDown" && context.hasProducts) {
    return { type: "move-highlight", delta: 1 };
  }
  if (event.key === "ArrowUp" && context.hasProducts) {
    return { type: "move-highlight", delta: -1 };
  }

  // Enter — ativa o produto em foco (seleciona ou lança). Quando digitando,
  // só quando há algo para ativar, para não "engolir" Enter num campo comum.
  if (event.key === "Enter") {
    if (!context.hasProducts && !context.hasSelection) return null;
    if (event.typing && !context.hasProducts && !context.hasSelection) {
      return null;
    }
    return { type: "activate-highlight" };
  }

  // Esc — limpar seleção/busca. Deixa o default seguir também (fechar algo),
  // por isso o hook não faz preventDefault nesta ação.
  if (event.key === "Escape") {
    return { type: "clear" };
  }

  return null;
}

/** Se o alvo do evento é um campo onde o usuário está digitando texto. */
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return (
    tag === "INPUT" ||
    tag === "TEXTAREA" ||
    tag === "SELECT" ||
    target.isContentEditable
  );
}

/** Ações que o navegador não deve também processar quando o atalho dispara. */
const PREVENT_DEFAULT: ReadonlySet<PdvHotkeyAction["type"]> = new Set([
  "focus-search",
  "move-highlight",
  "activate-highlight",
  "confirm",
  "confirm-and-receive",
]);

/**
 * Liga {@link resolvePdvHotkey} ao documento enquanto `enabled` for true.
 * `handlers` é lido por ref a cada evento (via dependências), então pode usar
 * estado fresco sem reinstalar o listener a cada tecla.
 */
export function usePdvHotkeys(
  enabled: boolean,
  getContext: () => PdvHotkeyContext,
  dispatch: (action: PdvHotkeyAction) => void,
): void {
  useEffect(() => {
    if (!enabled) return;
    function onKeyDown(event: KeyboardEvent) {
      const action = resolvePdvHotkey(
        {
          key: event.key,
          ctrlKey: event.ctrlKey,
          metaKey: event.metaKey,
          typing: isTypingTarget(event.target),
        },
        getContext(),
      );
      if (!action) return;
      if (PREVENT_DEFAULT.has(action.type)) event.preventDefault();
      dispatch(action);
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [enabled, getContext, dispatch]);
}
