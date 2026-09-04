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
import { type ReactNode, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
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

function StepperButton({
  onClick,
  ariaLabel,
  filled,
  children,
}: {
  onClick: () => void;
  ariaLabel: string;
  filled?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      className={
        filled
          ? "bg-primary text-primary-ink flex h-7 w-7 items-center justify-center rounded-full"
          : "border-primary text-primary flex h-7 w-7 items-center justify-center rounded-full border-[1.5px]"
      }
    >
      {children}
    </button>
  );
}

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
      <div className="flex flex-col gap-7 px-5 pb-28">
        {sections.map(({ category, products }) => (
          <section key={category.id} className="flex flex-col gap-3.5">
            <h2 className="text-ink-soft font-serif text-[15px] font-semibold italic">
              {category.name}
            </h2>
            <ul className="flex flex-col gap-3">
              {products.map((product) => {
                const quantity = cart[product.id] ?? 0;
                return (
                  <li
                    key={product.id}
                    className="border-line bg-surface flex gap-3 rounded-2xl border p-3.5"
                  >
                    {product.imageUrl ? (
                      // Sem otimização do next/image por enquanto (YAGNI)
                      // — ver README.
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={product.imageUrl}
                        alt={product.name}
                        className="h-16 w-16 flex-shrink-0 rounded-xl object-cover"
                      />
                    ) : null}
                    <div className="flex flex-1 flex-col gap-1">
                      <div className="flex items-baseline justify-between gap-2">
                        <span className="font-serif text-[15px] font-semibold">
                          {product.name}
                        </span>
                      </div>
                      {product.description ? (
                        <p className="text-ink-soft text-[13px]">
                          {product.description}
                        </p>
                      ) : null}
                      <div className="mt-1 flex items-center justify-between">
                        <span className="text-sm font-bold">
                          {currencyFormatter.format(product.price)}
                        </span>
                        <div className="flex items-center gap-2.5">
                          {quantity > 0 ? (
                            <>
                              <StepperButton
                                onClick={() => removeItem(product.id)}
                                ariaLabel={`Remover um ${product.name} do carrinho`}
                              >
                                <svg
                                  width="14"
                                  height="14"
                                  viewBox="0 0 24 24"
                                  fill="none"
                                  stroke="currentColor"
                                  strokeWidth="2.5"
                                  strokeLinecap="round"
                                >
                                  <path d="M5 12h14" />
                                </svg>
                              </StepperButton>
                              <span className="min-w-3 text-center text-sm font-bold">
                                {quantity}
                              </span>
                            </>
                          ) : null}
                          <StepperButton
                            filled
                            onClick={() => addItem(product.id)}
                            ariaLabel={`Adicionar ${product.name} ao carrinho`}
                          >
                            <svg
                              width="14"
                              height="14"
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2.5"
                              strokeLinecap="round"
                            >
                              <path d="M12 5v14M5 12h14" />
                            </svg>
                          </StepperButton>
                        </div>
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
        <div className="border-line bg-surface fixed inset-x-0 bottom-0 mx-auto flex max-w-md flex-col gap-1 border-t px-5 py-4 shadow-[0_-8px_24px_oklch(30%_0.05_45_/_10%)]">
          <p className="text-sm font-semibold">
            Pedido enviado! Comanda {commandNumber}
            {customerName ? `, ${customerName}` : ""}.
          </p>
          <p className="text-ink-soft text-[13px]">
            Aguarde a preparação — acompanhe pelo balcão.
          </p>
        </div>
      ) : cartItems.length > 0 ? (
        <div className="border-line bg-surface fixed inset-x-0 bottom-0 mx-auto flex max-w-md flex-col gap-2 border-t px-5 py-4 shadow-[0_-8px_24px_oklch(30%_0.05_45_/_10%)]">
          {checkoutStep === "cart" ? (
            <>
              <ul className="flex flex-col gap-1">
                {cartItems.map((item) => (
                  <li
                    key={item.product.id}
                    className="flex items-center justify-between text-sm"
                  >
                    <span>
                      {item.quantity}× {item.product.name}
                    </span>
                    <span className="font-semibold">
                      {currencyFormatter.format(
                        item.product.price * item.quantity,
                      )}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="border-line flex items-center justify-between border-t pt-2 text-sm font-bold">
                <span>Total</span>
                <span>{currencyFormatter.format(total)}</span>
              </div>
              <Button onClick={() => setCheckoutStep("form")}>
                Finalizar pedido
              </Button>
            </>
          ) : (
            <>
              <div className="flex items-center justify-between text-sm">
                <span className="text-ink-faint">{cartItems.length} itens</span>
                <span className="font-bold">
                  {currencyFormatter.format(total)}
                </span>
              </div>
              <form
                onSubmit={onCheckoutSubmit}
                noValidate
                className="flex flex-col gap-2"
              >
                <div className="flex flex-col gap-1">
                  <label
                    htmlFor="checkout-name"
                    className="text-ink text-xs font-medium"
                  >
                    Nome
                  </label>
                  <input
                    id="checkout-name"
                    type="text"
                    className="border-line bg-bg focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
                    {...register("name")}
                  />
                  {errors.name ? (
                    <p className="text-red text-xs">{errors.name.message}</p>
                  ) : null}
                </div>
                <div className="flex flex-col gap-1">
                  <label
                    htmlFor="checkout-phone"
                    className="text-ink text-xs font-medium"
                  >
                    Telefone/WhatsApp
                  </label>
                  <input
                    id="checkout-phone"
                    type="text"
                    placeholder="Ex: (11) 99999-9999"
                    className="border-line bg-bg focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
                    {...register("phone")}
                  />
                  {errors.phone ? (
                    <p className="text-red text-xs">{errors.phone.message}</p>
                  ) : null}
                </div>
                {checkoutErrorMessage ? (
                  <p className="text-red text-xs">{checkoutErrorMessage}</p>
                ) : null}
                <div className="flex gap-2">
                  <Button
                    type="submit"
                    disabled={isSubmitting || createOrderMutation.isPending}
                    className="flex-1"
                  >
                    {createOrderMutation.isPending
                      ? "Enviando..."
                      : "Confirmar pedido"}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setCheckoutStep("cart")}
                  >
                    Voltar
                  </Button>
                </div>
              </form>
            </>
          )}
        </div>
      ) : null}
    </>
  );
}
