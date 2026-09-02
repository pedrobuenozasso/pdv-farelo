"use client";

// Client Component (FARELO-044): the rest of /c/[commandNumber] stays a
// Server Component (page.tsx) — it fetches the comanda/cardápio during
// SSR and has no other interactivity. Only the cart needs client-side
// state (adding/removing items, showing a running total), so just this
// piece is split out across the "use client" boundary; page.tsx renders
// it with the already-fetched `sections` passed in as a prop instead of
// re-fetching client-side.

import { useState } from "react";

import type { Category } from "@/lib/api/categories";
import type { Product } from "@/lib/api/products";

export type MenuSection = { category: Category; products: Product[] };

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

// productId -> quantity. Local-only state (useState is enough for this
// scope — no Zustand/Context needed for one component tree, no
// persistence across reloads yet; see README) — lost on refresh, and
// nothing is sent to the backend here (POST /api/v1/orders doesn't exist
// yet — that's FARELO-045).
type CartState = Record<string, number>;

export function Menu({ sections }: { sections: MenuSection[] }) {
  const [cart, setCart] = useState<CartState>({});

  const productById = new Map(
    sections.flatMap((section) =>
      section.products.map((product) => [product.id, product] as const),
    ),
  );

  const cartItems = Object.entries(cart)
    .filter(([, quantity]) => quantity > 0)
    .map(([productId, quantity]) => ({
      product: productById.get(productId),
      quantity,
    }))
    .filter(
      (item): item is { product: Product; quantity: number } =>
        item.product !== undefined,
    );

  const total = cartItems.reduce(
    (sum, item) => sum + item.product.price * item.quantity,
    0,
  );

  function addItem(productId: string) {
    setCart((current) => ({
      ...current,
      [productId]: (current[productId] ?? 0) + 1,
    }));
  }

  function removeItem(productId: string) {
    setCart((current) => {
      const quantity = current[productId] ?? 0;
      if (quantity <= 1) {
        const next = { ...current };
        delete next[productId];
        return next;
      }
      return { ...current, [productId]: quantity - 1 };
    });
  }

  return (
    <>
      <div className="flex flex-col gap-8 pb-4">
        {sections.map(({ category, products }) => (
          <section key={category.id} className="flex flex-col gap-3">
            <h2 className="text-lg font-semibold text-black dark:text-zinc-50">
              {category.name}
            </h2>
            <ul className="flex flex-col gap-4">
              {products.map((product) => {
                const quantity = cart[product.id] ?? 0;
                return (
                  <li key={product.id} className="flex gap-3">
                    {product.imageUrl ? (
                      // Sem otimização do next/image por enquanto (YAGNI)
                      // — ver README.
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={product.imageUrl}
                        alt={product.name}
                        className="h-16 w-16 flex-shrink-0 rounded object-cover"
                      />
                    ) : null}
                    <div className="flex flex-1 flex-col gap-1">
                      <div className="flex items-baseline justify-between gap-2">
                        <span className="text-sm font-medium text-black dark:text-zinc-50">
                          {product.name}
                        </span>
                        <span className="text-sm font-medium whitespace-nowrap text-black dark:text-zinc-50">
                          {currencyFormatter.format(product.price)}
                        </span>
                      </div>
                      {product.description ? (
                        <p className="text-xs text-zinc-600 dark:text-zinc-400">
                          {product.description}
                        </p>
                      ) : null}
                      <div className="flex items-center gap-3 pt-1">
                        {quantity > 0 ? (
                          <>
                            <button
                              type="button"
                              onClick={() => removeItem(product.id)}
                              aria-label={`Remover um ${product.name} do carrinho`}
                              className="flex h-7 w-7 items-center justify-center rounded border border-zinc-300 text-sm font-medium text-black dark:border-zinc-700 dark:text-zinc-50"
                            >
                              −
                            </button>
                            <span className="min-w-4 text-center text-sm text-black dark:text-zinc-50">
                              {quantity}
                            </span>
                          </>
                        ) : null}
                        <button
                          type="button"
                          onClick={() => addItem(product.id)}
                          aria-label={`Adicionar ${product.name} ao carrinho`}
                          className="flex h-7 w-7 items-center justify-center rounded border border-zinc-300 text-sm font-medium text-black dark:border-zinc-700 dark:text-zinc-50"
                        >
                          +
                        </button>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>

      {cartItems.length > 0 ? (
        <div className="sticky bottom-0 -mx-6 flex flex-col gap-2 border-t border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-black">
          <ul className="flex flex-col gap-1">
            {cartItems.map((item) => (
              <li
                key={item.product.id}
                className="flex items-center justify-between text-sm text-black dark:text-zinc-50"
              >
                <span>
                  {item.quantity}× {item.product.name}
                </span>
                <span>
                  {currencyFormatter.format(item.product.price * item.quantity)}
                </span>
              </li>
            ))}
          </ul>
          <div className="flex items-center justify-between border-t border-zinc-200 pt-2 text-sm font-semibold text-black dark:border-zinc-800 dark:text-zinc-50">
            <span>Total</span>
            <span>{currencyFormatter.format(total)}</span>
          </div>
          {/* Placeholder — a lógica real (nome/telefone + envio, endpoint
              POST /api/v1/orders) é FARELO-045, ainda não existe. */}
          <button
            type="button"
            disabled
            title="Em breve"
            className="rounded bg-black px-4 py-2 text-sm font-medium text-white opacity-50 dark:bg-white dark:text-black"
          >
            Finalizar pedido (em breve)
          </button>
        </div>
      ) : null}
    </>
  );
}
