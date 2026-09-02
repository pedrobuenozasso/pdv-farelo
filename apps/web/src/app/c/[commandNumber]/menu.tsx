"use client";

// Client Component (FARELO-044/045): the rest of /c/[commandNumber] stays
// a Server Component (page.tsx) — it fetches the comanda/cardápio during
// SSR and has no other interactivity. Only the cart + checkout need
// client-side state (adding/removing items, running total, the
// name/phone form, the order submission), so just this piece is split
// out across the "use client" boundary; page.tsx renders it with the
// already-fetched `sections` passed in as a prop instead of re-fetching
// client-side.

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import type { Category } from "@/lib/api/categories";
import { apiErrorMessage } from "@/lib/api/client";
import { createOrder } from "@/lib/api/orders";
import type { Product } from "@/lib/api/products";

export type MenuSection = { category: Category; products: Product[] };

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

// productId -> quantity. Local-only state (useState is enough for this
// scope — no Zustand/Context needed for one component tree, no
// persistence across reloads yet; see README).
type CartState = Record<string, number>;

// Nome/telefone coletados aqui (prompt mestre seção 6) são enviados ao
// backend em createOrderMutation.mutate abaixo (customerName/
// customerPhone) — persistidos como snapshot simples em Order. Ver
// comentário em src/lib/api/orders.ts e docs/domain-model.md (seção
// `ordering`).
const checkoutFormSchema = z.object({
  name: z.string().trim().min(1, "Nome é obrigatório"),
  phone: z.string().trim().min(1, "Telefone/WhatsApp é obrigatório"),
});

type CheckoutFormValues = z.infer<typeof checkoutFormSchema>;

export function Menu({
  sections,
  commandNumber,
}: {
  sections: MenuSection[];
  commandNumber: number;
}) {
  const [cart, setCart] = useState<CartState>({});
  const [checkoutStep, setCheckoutStep] = useState<"cart" | "form">("cart");
  // Só para personalizar a mensagem de confirmação — não é enviado a
  // lugar nenhum além disso.
  const [customerName, setCustomerName] = useState("");
  const [orderConfirmed, setOrderConfirmed] = useState(false);

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

  const {
    register,
    handleSubmit,
    reset: resetCheckoutForm,
    formState: { errors, isSubmitting },
  } = useForm<CheckoutFormValues>({
    resolver: zodResolver(checkoutFormSchema),
    defaultValues: { name: "", phone: "" },
  });

  const createOrderMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: () => {
      setOrderConfirmed(true);
      setCart({});
      setCheckoutStep("cart");
      resetCheckoutForm();
    },
    // Em caso de erro (ex: comanda mudou de estado, produto ficou
    // indisponível entre montar o carrinho e enviar) não mexemos no
    // carrinho nem no passo atual — o cliente não deveria perder o que
    // montou por causa de um erro transitório. A mensagem aparece perto
    // do formulário (ver checkoutErrorMessage abaixo).
  });

  const onCheckoutSubmit = handleSubmit((values) => {
    setCustomerName(values.name);
    createOrderMutation.mutate({
      commandNumber,
      items: cartItems.map((item) => ({
        productId: item.product.id,
        quantity: item.quantity,
      })),
      customerName: values.name,
      customerPhone: values.phone,
    });
  });

  const checkoutErrorMessage = apiErrorMessage(
    createOrderMutation.error,
    "Não foi possível enviar o pedido.",
  );

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

      {orderConfirmed ? (
        <div className="sticky bottom-0 -mx-6 flex flex-col gap-1 border-t border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-black">
          <p className="text-sm font-medium text-black dark:text-zinc-50">
            Pedido enviado! Comanda {commandNumber}
            {customerName ? `, ${customerName}` : ""}.
          </p>
          <p className="text-xs text-zinc-600 dark:text-zinc-400">
            Aguarde a preparação — acompanhe pelo balcão.
          </p>
        </div>
      ) : cartItems.length > 0 ? (
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

          {checkoutStep === "cart" ? (
            <button
              type="button"
              onClick={() => setCheckoutStep("form")}
              className="rounded bg-black px-4 py-2 text-sm font-medium text-white dark:bg-white dark:text-black"
            >
              Finalizar pedido
            </button>
          ) : (
            <form
              onSubmit={onCheckoutSubmit}
              noValidate
              className="flex flex-col gap-2"
            >
              <div className="flex flex-col gap-1">
                <label
                  htmlFor="checkout-name"
                  className="text-xs font-medium text-black dark:text-zinc-50"
                >
                  Nome
                </label>
                <input
                  id="checkout-name"
                  type="text"
                  className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
                  {...register("name")}
                />
                {errors.name ? (
                  <p className="text-xs text-red-600 dark:text-red-400">
                    {errors.name.message}
                  </p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1">
                <label
                  htmlFor="checkout-phone"
                  className="text-xs font-medium text-black dark:text-zinc-50"
                >
                  Telefone/WhatsApp
                </label>
                <input
                  id="checkout-phone"
                  type="text"
                  placeholder="Ex: (11) 99999-9999"
                  className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
                  {...register("phone")}
                />
                {errors.phone ? (
                  <p className="text-xs text-red-600 dark:text-red-400">
                    {errors.phone.message}
                  </p>
                ) : null}
              </div>
              {checkoutErrorMessage ? (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {checkoutErrorMessage}
                </p>
              ) : null}
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={isSubmitting || createOrderMutation.isPending}
                  className="rounded bg-black px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-white dark:text-black"
                >
                  {createOrderMutation.isPending
                    ? "Enviando..."
                    : "Confirmar pedido"}
                </button>
                <button
                  type="button"
                  onClick={() => setCheckoutStep("cart")}
                  className="rounded border border-zinc-300 px-4 py-2 text-sm font-medium text-black dark:border-zinc-700 dark:text-zinc-50"
                >
                  Voltar
                </button>
              </div>
            </form>
          )}
        </div>
      ) : null}
    </>
  );
}
