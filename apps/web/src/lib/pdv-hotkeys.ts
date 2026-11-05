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
 * | ← / →            | Navegar a grade (fora de campo, ou campo vazio)  |
 * | Enter            | Lançar o produto em foco (com passos: escolhas)  |
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
  | { type: "move-highlight"; direction: PdvGridDirection }
  | { type: "activate-highlight" }
  | { type: "confirm" }
  | { type: "confirm-and-receive" }
  | { type: "clear" };

/**
 * Direção de navegação na grade de cards. ←/→ andam um card na ordem de
 * leitura; ↑/↓ trocam de linha visual (ver {@link verticalNeighborIndex}).
 */
export type PdvGridDirection = "up" | "down" | "left" | "right";

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
  /**
   * `true` quando o campo de texto em foco está vazio (ver
   * `isEmptyTypingTarget`). Sem texto não há cursor para mover, então ←/→
   * podem ir para a grade sem roubar nada do operador.
   */
  typingEmpty: boolean;
  /**
   * `true` quando o foco está num campo de texto que **não** é a busca de
   * produto (nome do cliente, observação, valor de pagamento...). Nesses
   * campos ↑/↓/←/→ e Enter pertencem ao campo — a grade não os captura, para
   * que Enter no nome do cliente não selecione o produto em destaque.
   */
  inOtherField?: boolean;
};

/**
 * Decide qual ação do PDV uma tecla dispara, ou `null` quando a tecla deve
 * seguir o comportamento padrão do navegador/campo.
 *
 * <p>Regras que não são óbvias:
 * <ul>
 *   <li><b>Setas funcionam mesmo digitando.</b> No PDV real "na busca, as setas
 *   navegam e Enter lança" — então ↑/↓ nunca ficam presas ao cursor do campo.</li>
 *   <li><b>←/→ respeitam o texto digitado.</b> Numa grade elas andam de card em
 *   card, mas dentro de um campo com texto movem o cursor (corrigir uma letra
 *   da busca é mais comum que andar para o lado). Com o campo vazio, ou fora
 *   de campo, navegam a grade.</li>
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

  // Fora da busca, num campo de texto qualquer, setas e Enter são do campo.
  if (
    event.inOtherField &&
    (event.key === "Enter" || event.key.startsWith("Arrow"))
  ) {
    return null;
  }

  // Setas navegam a grade, mesmo com foco na busca.
  if (event.key === "ArrowDown" && context.hasProducts) {
    return { type: "move-highlight", direction: "down" };
  }
  if (event.key === "ArrowUp" && context.hasProducts) {
    return { type: "move-highlight", direction: "up" };
  }
  // ←/→ — só quando não há texto em edição (ver regra acima).
  if (
    (event.key === "ArrowLeft" || event.key === "ArrowRight") &&
    context.hasProducts &&
    (!event.typing || event.typingEmpty)
  ) {
    return {
      type: "move-highlight",
      direction: event.key === "ArrowRight" ? "right" : "left",
    };
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

/**
 * O que um clique (ou Enter) num card de produto faz.
 *
 * <p>Como no PDV que a equipe usa hoje: produto simples entra direto no
 * rascunho, 1 unidade por clique; só produto com passos abre o painel de
 * escolhas. Qualquer dúvida cai no painel, que é o caminho que já valida e
 * explica o bloqueio:
 * <ul>
 *   <li><b>`hasSteps` ausente</b> (API anterior ao campo): abre as escolhas.</li>
 *   <li><b>Preço inválido ou zero</b>: abre o painel, que mostra "Valor zero"
 *   e não deixa lançar — nunca vira venda grátis silenciosa.</li>
 *   <li><b>Total do rascunho estouraria o limite numérico</b>: idem.</li>
 * </ul>
 *
 * @param unitCents preço do produto em centavos (`parseMoneyCents`), ou `null`
 * @param cartTotalCents total atual do rascunho em centavos
 */
export function resolveProductActivation(
  product: { hasSteps?: boolean },
  unitCents: number | null,
  cartTotalCents: number,
): "add-one" | "open-choices" {
  if (product.hasSteps !== false) return "open-choices";
  if (unitCents === null || unitCents <= 0) return "open-choices";
  if (!Number.isSafeInteger(cartTotalCents + unitCents)) return "open-choices";
  return "add-one";
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

/**
 * Se o alvo é a busca de produto do PDV (marcada com `data-pdv-search`). Só
 * nela as setas e o Enter navegam/lançam enquanto se digita.
 */
export function isPdvSearchTarget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement && target.dataset.pdvSearch !== undefined
  );
}

/** Se o alvo é um campo de texto vazio (input/textarea sem valor). */
export function isEmptyTypingTarget(target: EventTarget | null): boolean {
  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement
  ) {
    return target.value === "";
  }
  return false;
}

export type GridRect = {
  top: number;
  bottom: number;
  left: number;
  right: number;
};

/**
 * Índice do card na linha visual acima/abaixo de `current`, escolhendo o de
 * centro horizontal mais próximo, como a seta faz numa grade de verdade.
 * Recebe retângulos (e não um número de colunas) porque a grade é responsiva
 * e quebrada em seções por categoria: o número de colunas muda com a largura
 * e a última linha de uma seção pode estar incompleta.
 *
 * <p>Devolve `null` quando não há linha naquela direção; quem chama decide o
 * que fazer (o PDV volta ao primeiro/último card, como a lista fazia).
 */
export function verticalNeighborIndex(
  rects: readonly GridRect[],
  current: number,
  direction: "up" | "down",
): number | null {
  const origin = rects[current];
  if (!origin) return null;
  const originCenter = (origin.left + origin.right) / 2;
  // Tolerância de meio card: cards da mesma linha podem divergir alguns
  // pixels (bordas, arredondamento), mas nunca meia altura.
  const tolerance = Math.max(1, (origin.bottom - origin.top) / 2);

  // Borda da linha vizinha mais próxima: menor topo abaixo, ou maior base
  // acima, do card atual.
  let edge: number | null = null;
  for (let index = 0; index < rects.length; index++) {
    if (index === current) continue;
    const rect = rects[index];
    if (direction === "down" && rect.top >= origin.bottom - tolerance) {
      edge = edge === null ? rect.top : Math.min(edge, rect.top);
    }
    if (direction === "up" && rect.bottom <= origin.top + tolerance) {
      edge = edge === null ? rect.bottom : Math.max(edge, rect.bottom);
    }
  }
  if (edge === null) return null;

  let best: number | null = null;
  let bestDistance = Number.POSITIVE_INFINITY;
  for (let index = 0; index < rects.length; index++) {
    if (index === current) continue;
    const rect = rects[index];
    const inRow =
      direction === "down"
        ? Math.abs(rect.top - edge) < tolerance
        : Math.abs(rect.bottom - edge) < tolerance;
    if (!inRow) continue;
    const distance = Math.abs((rect.left + rect.right) / 2 - originCenter);
    if (distance < bestDistance) {
      best = index;
      bestDistance = distance;
    }
  }
  return best;
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
          typingEmpty: isEmptyTypingTarget(event.target),
          inOtherField:
            isTypingTarget(event.target) && !isPdvSearchTarget(event.target),
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
